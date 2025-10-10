// 文件：PixelLiveGameConfig.java
// 路径：org/jackhuang/hmcl/game/PixelLiveGameConfig.java
package org.jackhuang.hmcl.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.offline.OfflineAccount;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @description: PixelLiveGame配置文件管理类
 * 负责处理游戏目录中的PixelLiveGame.json配置文件的读取、创建和更新
 * 支持多平台房间号管理，统一处理直播模式和卡密模式，仅通过isCardKeyModeEnabled字段区分
 */
public class PixelLiveGameConfig {

    /**
     * @description: JSON格式化器，用于生成可读性良好的配置文件
     */
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()  // 禁用HTML转义
            .create();

    /**
     * @description: 默认的PixelLiveGame配置内容
     * 当配置文件不存在时使用此默认配置，所有字符串字段为空，布尔字段保持原值
     * 注意：liveType默认设置为DOUYIN，确保不为空
     */
    private static final String DEFAULT_CONFIG =
            "{\n" +
                    "  \"liveType\": \"DOUYIN\",\n" +
                    "  \"douyinID\": \"\",\n" +
                    "  \"kuaishouID\": \"\",\n" +
                    "  \"kuaishouCookie\": \"\",\n" +
                    "  \"tiktokID\": \"\",\n" +
                    "  \"tiktokCookie\": \"\",\n" +
                    "  \"chromeUrl\": \"\",\n" +
                    "  \"bilibiliCookie\": \"\",\n" +
                    "  \"twitchID\": \"\",\n" +
                    "  \"twitchCookie\": \"\",\n" +
                    "  \"isGiftMsgDisplay\": true,\n" +
                    "  \"isNameDisplay\": false,\n" +
                    "  \"isCardKeyModeEnabled\": false,\n" +
                    "  \"cardKeyValue\": \"\",\n" +
                    "  \"BilibiliID\": \"\",\n" +
                    "  \"xiaohongshuID\": \"\"\n" +
                    "}";

    /**
     * @description: 直播平台类型映射表
     * 将用户数据中的liveType映射到配置文件中的liveType枚举值
     */
    private static final Map<String, String> LIVE_TYPE_MAPPING = new HashMap<>();
    static {
        LIVE_TYPE_MAPPING.put("抖音", "DOUYIN");
        LIVE_TYPE_MAPPING.put("快手", "KUAISHOU");
        LIVE_TYPE_MAPPING.put("BiliBili", "BILIBILI");
        LIVE_TYPE_MAPPING.put("TikTok", "TIKTOK");
        LIVE_TYPE_MAPPING.put("Twitch", "TWITCH");
        LIVE_TYPE_MAPPING.put("小红书", "XIAOHONGSHU");
    }

    /**
     * @description: 平台ID字段映射表
     * 将平台名称映射到配置文件中的对应ID字段名
     */
    private static final Map<String, String> PLATFORM_ID_FIELD_MAPPING = new HashMap<>();
    static {
        PLATFORM_ID_FIELD_MAPPING.put("DOUYIN", "douyinID");
        PLATFORM_ID_FIELD_MAPPING.put("KUAISHOU", "kuaishouID");
        PLATFORM_ID_FIELD_MAPPING.put("BILIBILI", "BilibiliID");
        PLATFORM_ID_FIELD_MAPPING.put("TIKTOK", "tiktokID");
        PLATFORM_ID_FIELD_MAPPING.put("TWITCH", "twitchID");
        PLATFORM_ID_FIELD_MAPPING.put("XIAOHONGSHU", "xiaohongshuID");
    }

    /**
     * @description: 根据用户账户信息更新PixelLiveGame.json配置文件
     * 此方法在每次游戏启动时都会被调用，支持多平台房间号管理和统一模式处理
     * @param account 用户账户对象，包含多平台房间号信息
     * @param gameDir 游戏目录路径
     * @throws IOException 文件操作异常
     */
    public static void updatePixelLiveGameConfig(Account account, File gameDir) throws IOException {
        LOG.info("Starting PixelLiveGame config update process for account: " + account.getUsername());

        // 获取config目录路径
        Path configDir = gameDir.toPath().resolve("config");
        Path configFile = configDir.resolve("PixelLiveGame.json");

        // 确保config目录存在
        if (!Files.exists(configDir)) {
            Files.createDirectories(configDir);
            LOG.info("Created config directory: " + configDir);
        }

        // 读取现有配置或创建默认配置
        JsonObject config = loadOrCreateConfig(configFile);

        // 根据账户类型更新配置
        if (account instanceof OfflineAccount) {
            OfflineAccount offlineAccount = (OfflineAccount) account;
            updateConfigFromOfflineAccount(config, offlineAccount);
        } else {
            LOG.info("Non-offline account detected, updating to default account mode");
            updateConfigForNonOfflineAccount(config);
        }

        // 保存配置文件
        saveConfig(configFile, config);

        LOG.info("PixelLiveGame config update completed for account: " + account.getUsername());
    }

    /**
     * @description: 根据离线账户信息更新配置
     * 统一处理所有模式，仅通过isCardKeyModeEnabled字段区分登录方式
     * @param config 配置对象
     * @param offlineAccount 离线账户对象
     */
    private static void updateConfigFromOfflineAccount(JsonObject config, OfflineAccount offlineAccount) {
        try {
            String accountMode = offlineAccount.getAccountMode();
            LOG.info("Processing offline account mode: " + accountMode + " for user: " + offlineAccount.getUsername());

            // 统一处理所有离线账户模式
            if ("LIVE".equals(accountMode) || "CARD_KEY".equals(accountMode)) {
                handleUnifiedModeUpdate(config, offlineAccount);
            } else {
                LOG.info("Unknown or missing account mode for offline account, setting to default mode");
                updateConfigForNonOfflineAccount(config);
            }

        } catch (Exception e) {
            LOG.warning("Failed to update config from offline account, setting to default mode", e);
            updateConfigForNonOfflineAccount(config);
        }
    }

    /**
     * @description: 统一处理离线账户配置更新
     * 更新所有配置字段，根据账户模式设置isCardKeyModeEnabled字段值
     * @param config 配置对象
     * @param offlineAccount 离线账户对象
     */
    private static void handleUnifiedModeUpdate(JsonObject config, OfflineAccount offlineAccount) {
        String accountMode = offlineAccount.getAccountMode();
        String liveType = offlineAccount.getLiveType();
        Map<String, String> liveRooms = offlineAccount.getLiveRooms();
        String cardKey = offlineAccount.getCardKey();

        LOG.info("Processing unified mode update: accountMode=" + accountMode +
                ", liveType=" + liveType +
                ", platforms=" + (liveRooms != null ? liveRooms.size() : 0) +
                ", cardKey=" + (cardKey != null ? "[已设置]" : "[未设置]"));

        // 根据账户模式设置卡密模式启用状态
        boolean isCardKeyModeEnabled = "CARD_KEY".equals(accountMode);
        config.addProperty("isCardKeyModeEnabled", isCardKeyModeEnabled);

        // 更新卡密字段
        if (cardKey != null && !cardKey.trim().isEmpty()) {
            config.addProperty("cardKeyValue", cardKey.trim());
            LOG.info("Updated cardKeyValue with account's card key");
        } else {
            config.addProperty("cardKeyValue", "");
            LOG.info("Cleared cardKeyValue as account has no card key");
        }

        // 更新直播类型字段，如果为空则使用默认值DOUYIN
        String mappedLiveType = (liveType != null) ? LIVE_TYPE_MAPPING.getOrDefault(liveType, "DOUYIN") : "DOUYIN";
        config.addProperty("liveType", mappedLiveType);
        LOG.info("Updated liveType to: " + mappedLiveType + " (from: " + liveType + ")");

        // 更新所有平台ID字段
        if (liveRooms != null && !liveRooms.isEmpty()) {
            updateAllPlatformIds(config, liveRooms, mappedLiveType);
        } else {
            clearAllPlatformIds(config);
            LOG.info("No room data found, cleared all platform IDs");
        }

        LOG.info("Unified mode configuration updated successfully: isCardKeyModeEnabled=" + isCardKeyModeEnabled);
    }

    /**
     * @description: 更新所有平台的ID字段
     * 将账户中存储的多平台房间号数据同步到配置文件的对应字段中
     * @param config 配置对象
     * @param liveRooms 多平台房间号映射
     * @param currentPlatform 当前选择的平台（映射后的值）
     */
    private static void updateAllPlatformIds(JsonObject config, Map<String, String> liveRooms, String currentPlatform) {
        // 首先清空所有平台ID字段
        clearAllPlatformIds(config);

        int updatedCount = 0;

        // 遍历所有支持的平台，更新对应的ID字段
        for (Map.Entry<String, String> entry : LIVE_TYPE_MAPPING.entrySet()) {
            String platformDisplayName = entry.getKey();    // 如"抖音"
            String platformConfigName = entry.getValue();   // 如"DOUYIN"
            String idFieldName = PLATFORM_ID_FIELD_MAPPING.get(platformConfigName);

            if (idFieldName != null && liveRooms.containsKey(platformDisplayName)) {
                String roomNumber = liveRooms.get(platformDisplayName);
                if (roomNumber != null && !roomNumber.trim().isEmpty()) {
                    config.addProperty(idFieldName, roomNumber.trim());
                    updatedCount++;

                    String status = platformConfigName.equals(currentPlatform) ? " (当前平台)" : " (其他平台)";
                    LOG.info("Updated platform configuration: " + platformConfigName + " -> " +
                            idFieldName + " = " + roomNumber + status);
                }
            }
        }

        LOG.info("Total platforms updated: " + updatedCount + " out of " + liveRooms.size() +
                ", current platform: " + currentPlatform);
    }

    /**
     * @description: 清空所有平台的ID字段
     * @param config 配置对象
     */
    private static void clearAllPlatformIds(JsonObject config) {
        for (String idFieldName : PLATFORM_ID_FIELD_MAPPING.values()) {
            config.addProperty(idFieldName, "");
        }
        LOG.info("Cleared all platform ID fields");
    }

    /**
     * @description: 为非离线账户更新配置
     * 设置为默认模式，清空所有相关字段，确保liveType设置为DOUYIN
     * @param config 配置对象
     */
    private static void updateConfigForNonOfflineAccount(JsonObject config) {
        // 禁用卡密模式
        config.addProperty("isCardKeyModeEnabled", false);

        // 清空所有字段
        config.addProperty("cardKeyValue", "");
        clearAllPlatformIds(config);

        // 确保liveType设置为DOUYIN，防止为空
        config.addProperty("liveType", "DOUYIN");

        LOG.info("Configuration updated for non-offline account mode - all fields cleared, liveType set to DOUYIN");
    }

    /**
     * @description: 加载现有配置文件或创建默认配置
     * @param configFile 配置文件路径
     * @return JsonObject 配置对象
     * @throws IOException 文件操作异常
     */
    private static JsonObject loadOrCreateConfig(Path configFile) throws IOException {
        JsonObject config;

        if (Files.exists(configFile)) {
            try {
                byte[] bytes = Files.readAllBytes(configFile);
                String content = new String(bytes, StandardCharsets.UTF_8);
                config = JsonParser.parseString(content).getAsJsonObject();
                LOG.info("Successfully loaded existing PixelLiveGame.json configuration");
            } catch (Exception e) {
                LOG.warning("Failed to parse existing PixelLiveGame.json, creating new configuration", e);
                config = JsonParser.parseString(DEFAULT_CONFIG).getAsJsonObject();
            }
        } else {
            LOG.info("PixelLiveGame.json not found, creating new configuration");
            config = JsonParser.parseString(DEFAULT_CONFIG).getAsJsonObject();
        }

        // 确保配置包含所有必要字段，添加任何缺失的字段
        ensureRequiredFields(config);

        return config;
    }

    /**
     * @description: 确保liveType字段不为空
     * @param config 配置对象
     */
    private static void ensureLiveTypeNotEmpty(JsonObject config) {
        if (!config.has("liveType") || config.get("liveType").isJsonNull() ||
                config.get("liveType").getAsString().trim().isEmpty()) {
            config.addProperty("liveType", "DOUYIN");
            LOG.info("liveType was empty or missing, set to default value: DOUYIN");
        }
    }

    /**
     * @description: 确保配置文件包含所有必要的字段
     * 如果某些字段缺失，则添加默认值，但不覆盖现有值
     * @param config 配置对象
     */
    private static void ensureRequiredFields(JsonObject config) {
        JsonObject defaultConfig = JsonParser.parseString(DEFAULT_CONFIG).getAsJsonObject();

        // 只添加缺失的字段，不覆盖现有值
        for (String key : defaultConfig.keySet()) {
            if (!config.has(key)) {
                config.add(key, defaultConfig.get(key));
                LOG.info("Added missing field: " + key);
            }
        }

        // 特别检查liveType字段，确保不为空
        ensureLiveTypeNotEmpty(config);
    }

    /**
     * @description: 保存配置对象到文件
     * @param configFile 配置文件路径
     * @param config 配置对象
     * @throws IOException 文件操作异常
     */
    private static void saveConfig(Path configFile, JsonObject config) throws IOException {
        // 确保所有必要字段都存在
        ensureRequiredFields(config);

        // 最后再次确保liveType不为空
        ensureLiveTypeNotEmpty(config);

        String content = GSON.toJson(config);
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        Files.write(configFile, bytes);
        LOG.info("PixelLiveGame.json configuration saved successfully to: " + configFile);
    }
}