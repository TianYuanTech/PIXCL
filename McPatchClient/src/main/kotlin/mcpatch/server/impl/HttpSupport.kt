// 文件：HttpSupport.kt
// 路径:McPatchClient/src/main/kotlin/mcpatch/server/impl/HttpSupport.kt
package mcpatch.server.impl

import mcpatch.data.GlobalOptions
import mcpatch.exception.ConnectionInterruptedException
import mcpatch.exception.ConnectionRejectedException
import mcpatch.exception.ConnectionTimeoutException
import mcpatch.exception.HttpResponseStatusCodeException
import mcpatch.extension.FileExtension.bufferedOutputStream
import mcpatch.logging.Log
import mcpatch.server.AbstractServerSource
import mcpatch.server.OnDownload
import mcpatch.stream.ExposedByteArrayOutputStream
import mcpatch.util.File2
import mcpatch.util.MiscUtils
import mcpatch.webdav.CreateIgnoreVerifySsl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InterruptedIOException
import java.io.RandomAccessFile
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

class HttpSupport(serverString: String, val options: GlobalOptions)
    : AbstractServerSource()
{
    val baseUrl = serverString
        .run { if (!endsWith("/")) "$this/" else this }
        .run { substring(0, lastIndexOf("/") + 1) }

    val ssl = CreateIgnoreVerifySsl()

    val okClient = OkHttpClient.Builder()
        .sslSocketFactory(ssl.first.socketFactory, ssl.second)
        .connectTimeout(options.httpConnectTimeout.toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(options.httpResponseTimeout.toLong(), TimeUnit.MILLISECONDS)
        .writeTimeout(options.httpResponseTimeout.toLong(), TimeUnit.MILLISECONDS)
        .build()

    val retryTimes: Int = options.retryTimes

    override fun fetchText(relativePath: String): String
    {
        val url = buildURI(relativePath)
        val req = buildRequest(url)
        Log.debug("http request on $url")

        return withRetrying(retryTimes, 1000) {
            try {
                okClient.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) {
                        val body = r.body?.string()?.limitLength()
                        throw HttpResponseStatusCodeException(r.code, 200..300, url, body)
                    }

                    return@withRetrying r.body!!.string()
                }
            } catch (e: ConnectException) {
                throw ConnectionRejectedException(url, e.message ?: "")
            } catch (e: SocketException) {
                throw ConnectionInterruptedException(url, e.message ?: "")
            } catch (e: SocketTimeoutException) {
                throw ConnectionTimeoutException(url, e.message ?: "")
            } catch (e: Throwable) {
                throw e
            }
        }
    }

    override fun downloadFile(relativePath: String, writeTo: File2, callback: OnDownload)
    {
        val url = buildURI(relativePath)
        val link = url.replace("+", "%2B")

        writeTo.makeParentDirs()

        return withRetrying(retryTimes, 1000) {
            try {
                // 测试请求
                val testing = buildRequest(link, mapOf("Range" to "bytes=0-0"))
                val rangeSupported: Boolean
                val length: Long

                Log.debug("http range test request on $url")
                okClient.newCall(testing).execute().use { r ->
                    if(!r.isSuccessful)
                        throw HttpResponseStatusCodeException(r.code, 200..300, link, r.body?.string()?.limitLength())

                    rangeSupported = r.code == 206
                    length = if (rangeSupported) r.headers["Content-Range"].toString().split("/")[1].toLong() else -1
                }

                Log.debug("http request on $url, concurrent is ${if (rangeSupported) "on" else "off"}, write to: ${writeTo.path}")

                if (rangeSupported)
                    concurrentDownload(url, link, length, writeTo, callback)
                else
                    normalDownload(url, link, writeTo, callback)
            } catch (e: ConnectException) {
                throw ConnectionInterruptedException(link, e.message ?: "")
            } catch (e: SocketException) {
                throw ConnectionRejectedException(link, e.message ?: "")
            } catch (e: SocketTimeoutException) {
                throw ConnectionTimeoutException(link, e.message ?: "")
            } catch (e: Throwable) {
                throw e
            }
        }
    }

    override fun buildURI(relativePath: String): String
    {
        return baseUrl + relativePath
    }

    override fun close() { }

    private fun String.limitLength(limit: Int = 500): String
    {
        return if (length > limit) substring(0, limit) + "\n..." else this
    }

    private fun buildRequest(url: String, headers: Map<String, String>? = null): Request
    {
        val req = Request.Builder().url(url)

        if (options.clientUserAgent.isNotEmpty())
            req.addHeader("User-Agent", this.options.clientUserAgent)

        if (headers != null)
            for (header in headers.entries)
                req.addHeader(header.key, header.value)

        for (header in options.httpHeaders)
            req.addHeader(header.key, header.value)

        return req.build()
    }

    private fun normalDownload(url: String, link: String, writeTo: File2, callback: OnDownload)
    {
        val req = buildRequest(url)

        okClient.newCall(req).execute().use { r ->
            if(!r.isSuccessful)
                throw HttpResponseStatusCodeException(r.code, 200..300, link, r.body?.string()?.limitLength())

            val body = r.body!!
            val bodyLen = if (body.contentLength() != -1L) body.contentLength() else options.httpFallbackFileSize
            val bufferSize = MiscUtils.chooseBufferSize(bodyLen)

            body.source().use { input ->
                writeTo.file.bufferedOutputStream(bufferSize).use { output ->
                    var bytesReceived: Long = 0
                    var len: Int
                    val buffer = ByteArray(bufferSize)
                    val rrf = ReduceReportingFrequency()

                    while (input.read(buffer).also { len = it } != -1)
                    {
                        output.write(buffer, 0, len)
                        bytesReceived += len

                        val report = rrf.feed(len)
                        if (report > 0)
                            callback(report, bytesReceived, bodyLen)
                    }
                }
            }
        }
    }

    private fun concurrentDownload(url: String, link: String, length: Long, writeTo: File2, callback: OnDownload)
    {
        val blockSize = options.concurrentBlockSize
        val totalBlocks = ((length + blockSize - 1) / blockSize).toInt()
        val taskBlocks = LinkedBlockingQueue<Pair<Int, LongRange>>()
        val downloadedBlocks = ConcurrentHashMap<Int, ExposedByteArrayOutputStream>()
        val failedBlocks = ConcurrentHashMap<Int, Int>()
        val maxBlockRetries = 3

        // 任务状态跟踪
        val remainingTasks = AtomicInteger(totalBlocks)
        val activeThreads = AtomicInteger(0)  // 新增：跟踪活跃线程数

        // 初始化任务队列
        for (i in 0 until length / blockSize) {
            taskBlocks.put(Pair(i.toInt(), i * blockSize until i * blockSize + blockSize))
        }

        if (length % blockSize > 0) {
            val i = taskBlocks.size
            taskBlocks.put(Pair(i, i * blockSize until length))
        }

        val totalDownloadedBytes = AtomicLong()
        val reporter = ReduceReportingFrequency()
        val threads = Integer.max(1, min(options.concurrentThreads, taskBlocks.size))
        val pool = Executors.newFixedThreadPool(threads)
        val exceptions = ConcurrentHashMap<Int, Exception>()
        val shouldStop = AtomicBoolean(false)

        Log.info("开始并发下载: 文件大小=${length}字节, 分块数=$totalBlocks, 线程数=$threads")

        for (threadId in 0 until threads)
        {
            pool.execute {
                activeThreads.incrementAndGet()  // 线程启动时增加计数
                try {
                    while (!shouldStop.get())
                    {
                        // 关键修复1: 增加轮询超时到5秒，避免过早退出
                        val task = taskBlocks.poll(5, TimeUnit.SECONDS)

                        if (task == null) {
                            // 关键修复2: 双重检查机制，避免竞态条件
                            Thread.sleep(500)  // 等待500ms，让其他线程有机会更新状态

                            // 再次尝试获取任务
                            val retryTask = taskBlocks.poll(1, TimeUnit.SECONDS)
                            if (retryTask != null) {
                                // 获取到任务，处理它
                                processDownloadTask(
                                    retryTask, threadId, url, link, writeTo, blockSize,
                                    downloadedBlocks, failedBlocks, maxBlockRetries,
                                    totalDownloadedBytes, reporter, length, callback,
                                    shouldStop, remainingTasks, exceptions
                                )
                                continue
                            }

                            // 关键修复3: 确认真正完成的条件
                            // 队列为空 且 没有剩余任务 且 没有其他线程正在处理任务
                            if (taskBlocks.isEmpty() && remainingTasks.get() == 0) {
                                Log.debug("Thread $threadId: All tasks confirmed completed, exiting")
                                break
                            }

                            // 如果还有任务在处理中（即使队列为空），继续等待
                            Log.debug("Thread $threadId: Queue empty but ${remainingTasks.get()} tasks remaining, waiting...")
                            continue
                        }

                        // 处理下载任务
                        processDownloadTask(
                            task, threadId, url, link, writeTo, blockSize,
                            downloadedBlocks, failedBlocks, maxBlockRetries,
                            totalDownloadedBytes, reporter, length, callback,
                            shouldStop, remainingTasks, exceptions
                        )
                    }
                } catch (e: InterruptedIOException) {
                    Log.info("Thread $threadId interrupted by user")
                } catch (e: Exception) {
                    Log.error("Thread $threadId encountered unexpected error: ${e.message}")
                    exceptions[-threadId] = e
                } finally {
                    activeThreads.decrementAndGet()  // 线程退出时减少计数
                }
            }
        }

        pool.shutdown()
        Log.debug("Thread pool shutdown initiated, waiting for completion...")

        // 关键修复4: 增加等待时间，确保所有任务完成
        val terminated = pool.awaitTermination(2, TimeUnit.HOURS)

        if (!terminated) {
            Log.warn("Thread pool did not terminate in time")
            pool.shutdownNow()
        } else {
            Log.debug("All threads completed successfully")
        }

        // 检查是否有异常
        if (exceptions.isNotEmpty()) {
            val firstException = exceptions.values.first()
            if (firstException is InterruptedIOException) {
                throw firstException
            }
        }

        // 验证下载完整性
        Log.debug("Verifying download completeness...")
        if (downloadedBlocks.size != totalBlocks) {
            val missingBlocks = (0 until totalBlocks).filter { !downloadedBlocks.containsKey(it) }
            val errorMessage = StringBuilder()
            errorMessage.append("下载不完整: 期望 $totalBlocks 块，实际下载 ${downloadedBlocks.size} 块\n")
            errorMessage.append("缺失的块: $missingBlocks\n")

            if (failedBlocks.isNotEmpty()) {
                errorMessage.append("失败的块详情:\n")
                failedBlocks.forEach { (blockIndex, attempts) ->
                    errorMessage.append("  块 $blockIndex: 失败 $attempts 次")
                    exceptions[blockIndex]?.let { e ->
                        errorMessage.append(" - ${e.message}")
                    }
                    errorMessage.append("\n")
                }
            }

            throw Exception(errorMessage.toString())
        }

        // 验证总下载字节数
        val totalDownloaded = totalDownloadedBytes.get()
        if (totalDownloaded != length) {
            throw Exception("下载字节数不匹配: 期望 $length 字节，实际 $totalDownloaded 字节")
        }

        // 将所有块按顺序写入文件
        Log.debug("Writing ${downloadedBlocks.size} blocks to file")
        RandomAccessFile(writeTo.file, "rw").use { file ->
            for (i in 0 until totalBlocks)
            {
                val block = downloadedBlocks[i]
                if (block == null) {
                    throw Exception("块 $i 在写入时丢失")
                }

                val buf = block.internalBuffer()
                val len = block.size()

                file.write(buf, 0, len)
                Log.debug("Written block $i, size: $len bytes")
            }
        }

        Log.info("文件下载完成: ${writeTo.path}")
    }

    private fun processDownloadTask(
        task: Pair<Int, LongRange>,
        threadId: Int,
        url: String,
        link: String,
        writeTo: File2,
        blockSize: Int,
        downloadedBlocks: ConcurrentHashMap<Int, ExposedByteArrayOutputStream>,
        failedBlocks: ConcurrentHashMap<Int, Int>,
        maxBlockRetries: Int,
        totalDownloadedBytes: AtomicLong,
        reporter: ReduceReportingFrequency,
        length: Long,
        callback: OnDownload,
        shouldStop: AtomicBoolean,
        remainingTasks: AtomicInteger,
        exceptions: ConcurrentHashMap<Int, Exception>
    ) {
        val (blockIndex, block) = task

        // 检查是否已下载（避免重复下载）
        if (downloadedBlocks.containsKey(blockIndex)) {
            remainingTasks.decrementAndGet()
            return
        }

        var blockSuccess = false
        var lastException: Exception? = null

        // 块级别的重试逻辑
        var attempt = 1
        while (attempt <= maxBlockRetries && !blockSuccess && !shouldStop.get()) {
            try {
                Log.debug("http request on $url part $blockIndex (${block.first} to ${block.last}), attempt $attempt, write to: ${writeTo.path}")

                val req = buildRequest(link, mapOf("Range" to "bytes=${block.first}-${block.last}"))

                okClient.newCall(req).execute().use { r ->
                    if(!r.isSuccessful) {
                        throw HttpResponseStatusCodeException(r.code, 200..300, link, r.body?.string()?.limitLength())
                    }

                    if (r.code != 206) {
                        throw HttpResponseStatusCodeException(r.code, 206..206, link, r.body?.string()?.limitLength())
                    }

                    val body = r.body!!
                    val bodyLen = if (body.contentLength() != -1L) body.contentLength() else blockSize.toLong()
                    val bufferSize = MiscUtils.chooseBufferSize(bodyLen)

                    body.source().use { input ->
                        val buf = ExposedByteArrayOutputStream(bufferSize)

                        buf.use { output ->
                            var len: Int
                            val buffer = ByteArray(bufferSize)

                            while (input.read(buffer).also { len = it } != -1)
                            {
                                if (shouldStop.get()) {
                                    throw InterruptedIOException("Download interrupted by user")
                                }

                                output.write(buffer, 0, len)

                                val total = totalDownloadedBytes.addAndGet(len.toLong())
                                val report = reporter.feed(len)

                                if (report > 0)
                                    callback(report, total, length)
                            }
                        }

                        // 验证块大小
                        val expectedSize = (block.last - block.first + 1).toInt()
                        if (buf.size() != expectedSize) {
                            throw Exception("Block $blockIndex size mismatch: expected $expectedSize, got ${buf.size()}")
                        }

                        downloadedBlocks[blockIndex] = buf
                        blockSuccess = true
                        Log.debug("Successfully downloaded block $blockIndex (${buf.size()} bytes)")
                    }
                }
            } catch (e: InterruptedIOException) {
                Log.info("下载被用户中断")
                throw e
            } catch (e: Exception) {
                lastException = e
                Log.warn("Block $blockIndex attempt $attempt failed: ${e.message}")

                if (attempt < maxBlockRetries) {
                    // 关键修复5: 增加重试延迟，使用指数退避
                    val delayMs = 1000L * (1 shl (attempt - 1))  // 1s, 2s, 4s...
                    Thread.sleep(delayMs)
                    attempt++
                } else {
                    failedBlocks[blockIndex] = attempt
                    exceptions[blockIndex] = e
                    Log.error("Block $blockIndex failed after $maxBlockRetries attempts")
                    attempt++
                }
            }
        }

        if (!blockSuccess && lastException !is InterruptedIOException) {
            Log.warn("Block $blockIndex download failed after all retries")
        }

        // 无论成功还是失败，都减少剩余任务计数
        val remaining = remainingTasks.decrementAndGet()
        Log.debug("Thread $threadId completed block $blockIndex: Remaining tasks: $remaining")
    }
}