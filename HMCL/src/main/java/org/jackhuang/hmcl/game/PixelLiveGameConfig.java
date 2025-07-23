// 文件：PixelLiveGameConfig.java
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
 * 现在支持多平台房间号管理，每次游戏启动时会根据当前选择的平台更新对应的配置字段
 */
public class PixelLiveGameConfig {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    /**
     * @description: 默认的PixelLiveGame配置内容
     * 当配置文件不存在时使用此默认配置，所有字符串字段为空，布尔字段保持原值
     */
    private static final String DEFAULT_CONFIG =
            "{\n" +
                    "  \"liveType\": \"\",\n" +
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
                    "  \"BilibiliID\": \"\"\n" +
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
    }

    /**
     * @description: 根据用户账户信息更新PixelLiveGame.json配置文件
     * 此方法在每次游戏启动时都会被调用，现在支持多平台房间号管理
     * 只更新当前选择平台的ID字段，保留其他平台已配置的房间号
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

        // 如果是离线账户，根据账户信息更新相关字段
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
     * 现在支持多平台房间号管理，只更新当前选择平台的配置，保留其他平台设置
     * @param config 配置对象
     * @param offlineAccount 离线账户对象
     */
    private static void updateConfigFromOfflineAccount(JsonObject config, OfflineAccount offlineAccount) {
        try {
            String accountMode = offlineAccount.getAccountMode();
            LOG.info("Processing offline account mode: " + accountMode + " for user: " + offlineAccount.getUsername());

            if ("LIVE".equals(accountMode)) {
                handleLiveModeUpdateWithMultiPlatform(config, offlineAccount);
            } else if ("CARD_KEY".equals(accountMode)) {
                handleCardKeyModeUpdate(config, offlineAccount);
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
     * @description: 处理直播模式的配置更新，支持多平台房间号管理
     * 根据当前选择的平台更新对应的ID字段，同时保留其他平台的房间号设置
     * @param config 配置对象
     * @param offlineAccount 离线账户对象
     */
    private static void handleLiveModeUpdateWithMultiPlatform(JsonObject config, OfflineAccount offlineAccount) {
        String liveType = offlineAccount.getLiveType();
        Map<String, String> liveRooms = offlineAccount.getLiveRooms();

        LOG.info("Processing live mode with multi-platform support: liveType=" + liveType + ", platforms=" +
                (liveRooms != null ? liveRooms.size() : 0));

        if (liveType == null) {
            LOG.warning("Missing liveType in offline account");
            return;
        }

        // 映射直播平台类型
        String mappedLiveType = LIVE_TYPE_MAPPING.get(liveType);
        if (mappedLiveType == null) {
            LOG.warning("Unknown live type: " + liveType);
            return;
        }

        // 首先禁用卡密模式
        config.addProperty("isCardKeyModeEnabled", false);

        // 更新配置中的liveType字段为当前选择的平台
        config.addProperty("liveType", mappedLiveType);

        // 根据多平台房间号数据更新配置
        if (liveRooms != null && !liveRooms.isEmpty()) {
            updateAllPlatformIds(config, liveRooms, mappedLiveType);
        } else {
            // 如果没有多平台数据，清空所有平台ID但保持当前平台为空
            clearAllPlatformIds(config);
            LOG.info("No multi-platform room data found, cleared all platform IDs");
        }

        LOG.info("Live mode configuration updated successfully for platform: " + mappedLiveType);
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
    }

    /**
     * @description: 处理卡密模式的配置更新
     * 只更新卡密相关字段，保留其他字段不变
     * @param config 配置对象
     * @param offlineAccount 离线账户对象
     */
    private static void handleCardKeyModeUpdate(JsonObject config, OfflineAccount offlineAccount) {
        String cardKey = offlineAccount.getCardKey();

        LOG.info("Processing card key mode: cardKey=" + (cardKey != null ? "[已设置]" : "[未设置]"));

        if (cardKey == null) {
            LOG.warning("Missing cardKey in offline account");
            return;
        }

        // 启用卡密模式
        config.addProperty("isCardKeyModeEnabled", true);
        config.addProperty("cardKeyValue", cardKey);

        // 设置默认的liveType为空，但保留所有平台ID字段不变
        config.addProperty("liveType", "");

        LOG.info("Card key mode configuration updated successfully");
    }

    /**
     * @description: 为非离线账户更新配置
     * 设置为默认模式，但保留Cookie等用户自定义字段
     * @param config 配置对象
     */
    private static void updateConfigForNonOfflineAccount(JsonObject config) {
        // 禁用卡密模式
        config.addProperty("isCardKeyModeEnabled", false);
        config.addProperty("cardKeyValue", "");

        // 设置默认的直播平台和清空ID字段
        config.addProperty("liveType", "");
        clearAllPlatformIds(config);

        LOG.info("Configuration updated for non-offline account mode");
    }

    /**
     * @description: 加载现有配置文件或创建默认配置
     * @param configFile 配置文件路径
     * @return JsonObject 配置对象
     * @throws IOException 文件操作异常
     */
    private static JsonObject loadOrCreateConfig(Path configFile) throws IOException {
        if (Files.exists(configFile)) {
            try {
                byte[] bytes = Files.readAllBytes(configFile);
                String content = new String(bytes, StandardCharsets.UTF_8);
                LOG.info("Successfully loaded existing PixelLiveGame.json configuration");
                return JsonParser.parseString(content).getAsJsonObject();
            } catch (Exception e) {
                LOG.warning("Failed to parse existing PixelLiveGame.json, creating new configuration", e);
                return JsonParser.parseString(DEFAULT_CONFIG).getAsJsonObject();
            }
        } else {
            LOG.info("PixelLiveGame.json not found, creating new configuration");
            return JsonParser.parseString(DEFAULT_CONFIG).getAsJsonObject();
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

        String content = GSON.toJson(config);
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        Files.write(configFile, bytes);
        LOG.info("PixelLiveGame.json configuration saved successfully to: " + configFile);
    }
}