package org.jackhuang.hmcl.util;

import com.google.gson.Gson;
import org.jackhuang.hmcl.Metadata;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @description: 授权检查器，负责验证直播间和卡密的授权状态
 */
public class AuthorizationChecker {
    /**
     * @description: HTTP连接超时时间（毫秒）
     */
    private static final int CONNECT_TIMEOUT = 5000; // 5秒连接超时
    /**
     * @description: HTTP读取超时时间（毫秒）
     */
    private static final int READ_TIMEOUT = 10000;   // 10秒读取超时
    /**
     * @description: JSON序列化工具
     */
    private static final Gson GSON = new Gson();

    /**
     * @description: 官方服务器上下文ID，用于区分不同服务器的会话密钥
     */
    private static final String OFFICIAL_CONTEXT = "official";

    /**
     * @description: TikTok服务器上下文ID，用于区分不同服务器的会话密钥
     */
    private static final String TIKTOK_CONTEXT = "tiktok";

    /**
     * @description: 检查直播间授权状态，根据平台自动选择合适的服务器
     * @param platform 平台标识（如"抖音"、"TikTok"、"Twitch"等）
     * @param studioName 直播间名称/房间号
     * @return boolean true表示有授权，false表示无授权或请求失败
     */
    public static boolean checkWebcastAuthorization(String platform, String studioName) {
        // 参数验证
        if (platform == null || platform.isEmpty() || studioName == null || studioName.isEmpty()) {
            return false;
        }

        try {
            // 根据平台选择合适的服务器URL
            String serverUrl = selectServerUrlByPlatform(platform);

            // 构建请求URL
            String encodedPlatform = URLEncoder.encode(platform, StandardCharsets.UTF_8.toString());
            String encodedStudioName = URLEncoder.encode(studioName, StandardCharsets.UTF_8.toString());
            String fullUrl = serverUrl + "/check/webcast/authorization" +
                    "?platform=" + encodedPlatform + "&studioName=" + encodedStudioName;

            // 创建HTTP连接
            HttpURLConnection conn = createConnection(fullUrl, "GET");

            // 处理响应
            return processResponse(conn);
        } catch (Exception e) {
            // 记录错误日志（实际项目中应使用日志框架）
            e.printStackTrace();
            return false;
        }
    }

    /**
     * @description: 根据平台类型选择对应的服务器URL
     * @param platform 平台名称
     * @return String 对应的服务器URL地址
     */
    private static String selectServerUrlByPlatform(String platform) {
        if (platform == null) {
            return Metadata.PUBLISH_URL;
        }

        // 检查是否为TikTok或Twitch平台（不区分大小写）
        String platformLower = platform.toLowerCase();
        if ("tiktok".equals(platformLower) || "twitch".equals(platformLower)) {
            return Metadata.TIKTOK_SERVER_URL;
        }

        // 默认使用官方服务器
        return Metadata.PUBLISH_URL;
    }

    /**
     * @description: 检查卡密授权状态
     * @param cardKey 卡密值
     * @return boolean true表示有授权，false表示无授权或请求失败
     */
    public static boolean checkCardAuthorization(String cardKey) {
        // 参数验证
        if (cardKey == null || cardKey.isEmpty()) {
            return false;
        }

        try {
            // 构建请求URL
            String encodedCardKey = URLEncoder.encode(cardKey, StandardCharsets.UTF_8.toString());

            String fullUrl = Metadata.PUBLISH_URL + "/check/card/authorization" +
                    "?cardKey=" + encodedCardKey;

            // 创建HTTP连接（使用POST方法）
            HttpURLConnection conn = createConnection(fullUrl, "POST");

            // 对于POST请求需要写入空请求体（接口要求）
            try (OutputStream os = conn.getOutputStream()) {
                os.write(new byte[0]); // 发送空请求体
            }

            // 处理响应
            return processResponse(conn);
        } catch (Exception e) {
            // 记录错误日志
            e.printStackTrace();
            return false;
        }
    }

    /**
     * @description: 创建HTTP连接并设置基本参数
     * @param url 请求的完整URL地址
     * @param method HTTP请求方法（GET或POST）
     * @return HttpURLConnection 配置好的HTTP连接对象
     * @throws IOException 当创建连接失败时抛出异常
     */
    private static HttpURLConnection createConnection(String url, String method) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(CONNECT_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);
        conn.setRequestProperty("User-Agent", "HMCL-Authorization-Checker/1.0");
        conn.setRequestProperty("Accept", "application/json");

        // 对于POST请求启用输出
        if ("POST".equalsIgnoreCase(method)) {
            conn.setDoOutput(true);
        }

        return conn;
    }

    /**
     * @description: 处理HTTP响应并解析授权结果
     * @param conn 已建立的HTTP连接对象
     * @return boolean 解析得到的授权状态
     * @throws IOException 当读取响应失败时抛出异常
     */
    private static boolean processResponse(HttpURLConnection conn) throws IOException {
        int responseCode = conn.getResponseCode();

        try (InputStream inputStream = responseCode == 200 ?
                conn.getInputStream() : conn.getErrorStream()) {

            if (inputStream == null) {
                return false;
            }

            // 读取响应内容
            String responseBody = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));

            // 处理非200响应
            if (responseCode != 200) {
                // 可在此处记录错误响应（实际项目中应使用日志框架）
                return false;
            }

            // 解析JSON响应
            Map<?, ?> responseMap = GSON.fromJson(responseBody, Map.class);
            Object codeObj = responseMap.get("code");
            Object dataObj = responseMap.get("data");

            // 验证响应格式
            if (!(codeObj instanceof Number) || !(dataObj instanceof Boolean)) {
                return false;
            }

            int code = ((Number) codeObj).intValue();
            return code == 200 && (Boolean) dataObj;
        }
    }
}