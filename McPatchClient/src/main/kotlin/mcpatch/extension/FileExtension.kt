package mcpatch.extension

import java.io.*

object FileExtension
{
    @Suppress("NOTHING_TO_INLINE")
    inline fun File.bufferedInputStream(bufferSize: Int = 128 * 1024): BufferedInputStream
    {
        return FileInputStream(this).buffered(bufferSize)
    }

    @Suppress("NOTHING_TO_INLINE")
    inline fun File.bufferedOutputStream(bufferSize: Int = 128 * 1024): BufferedOutputStream
    {
        return FileOutputStream(this).buffered(bufferSize)
    }
}