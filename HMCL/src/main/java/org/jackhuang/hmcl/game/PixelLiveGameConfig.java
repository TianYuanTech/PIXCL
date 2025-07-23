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
 * 每次游戏启动时都会执行配置文件的更新操作，但只更新与用户账户相关的字段
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
     * @description: 根据用户账户信息更新PixelLiveGame.json配置文件
     * 此方法在每次游戏启动时都会被调用，只更新与用户账户相关的字段，保留其他字段的现有值
     * @param account - 用户账户对象，包含新的字段信息
     * @param gameDir - 游戏目录路径
     * @throws IOException - 文件操作异常
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
     * 只更新与账户模式相关的字段，保留其他字段的现有值
     * @param config - 配置对象
     * @param offlineAccount - 离线账户对象
     */
    private static void updateConfigFromOfflineAccount(JsonObject config, OfflineAccount offlineAccount) {
        try {
            String accountMode = offlineAccount.getAccountMode();
            LOG.info("Processing offline account mode: " + accountMode + " for user: " + offlineAccount.getUsername());

            if ("LIVE".equals(accountMode)) {
                handleLiveModeUpdate(config, offlineAccount);
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
     * @description: 处理直播模式的配置更新
     * 只更新liveType和对应的ID字段，保留其他字段不变
     * @param config - 配置对象
     * @param offlineAccount - 离线账户对象
     */
    private static void handleLiveModeUpdate(JsonObject config, OfflineAccount offlineAccount) {
        String liveType = offlineAccount.getLiveType();
        String liveRoom = offlineAccount.getLiveRoom();

        LOG.info("Processing live mode: liveType=" + liveType + ", liveRoom=" + liveRoom);

        if (liveType == null || liveRoom == null) {
            LOG.warning("Missing liveType or liveRoom in offline account");
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
//        config.addProperty("cardKeyValue", "");

        // 更新配置中的liveType字段
        config.addProperty("liveType", mappedLiveType);

        // 清空所有平台的ID字段，然后设置当前平台的ID
//        config.addProperty("douyinID", "");
//        config.addProperty("kuaishouID", "");
//        config.addProperty("BilibiliID", "");
//        config.addProperty("tiktokID", "");
//        config.addProperty("twitchID", "");

        // 根据平台类型设置对应的ID字段
        switch (mappedLiveType) {
            case "DOUYIN":
                config.addProperty("douyinID", liveRoom);
                LOG.info("Updated DOUYIN configuration: douyinID -> " + liveRoom);
                break;
            case "KUAISHOU":
                config.addProperty("kuaishouID", liveRoom);
                LOG.info("Updated KUAISHOU configuration: kuaishouID -> " + liveRoom);
                break;
            case "BILIBILI":
                config.addProperty("BilibiliID", liveRoom);
                LOG.info("Updated BILIBILI configuration: BilibiliID -> " + liveRoom);
                break;
            case "TIKTOK":
                config.addProperty("tiktokID", liveRoom);
                LOG.info("Updated TIKTOK configuration: tiktokID -> " + liveRoom);
                break;
            case "TWITCH":
                config.addProperty("twitchID", liveRoom);
                LOG.info("Updated TWITCH configuration: twitchID -> " + liveRoom);
                break;
        }

        LOG.info("Live mode configuration updated successfully: " + mappedLiveType + " -> " + liveRoom);
    }

    /**
     * @description: 处理卡密模式的配置更新
     * 只更新卡密相关字段，保留其他字段不变
     * @param config - 配置对象
     * @param offlineAccount - 离线账户对象
     */
    private static void handleCardKeyModeUpdate(JsonObject config, OfflineAccount offlineAccount) {
        String cardKey = offlineAccount.getCardKey();

        LOG.info("Processing card key mode: cardKey=" + cardKey);

        if (cardKey == null) {
            LOG.warning("Missing cardKey in offline account");
            return;
        }

        // 启用卡密模式
        config.addProperty("isCardKeyModeEnabled", true);
        config.addProperty("cardKeyValue", cardKey);

        // 设置默认的liveType为空，但不清空其他字段
        config.addProperty("liveType", "");

        LOG.info("Card key mode configuration updated successfully: " + cardKey);
    }

    /**
     * @description: 为非离线账户更新配置
     * 设置为默认模式，但保留Cookie等用户自定义字段
     * @param config - 配置对象
     */
    private static void updateConfigForNonOfflineAccount(JsonObject config) {
        // 禁用卡密模式
        config.addProperty("isCardKeyModeEnabled", false);
        config.addProperty("cardKeyValue", "");

        // 设置默认的直播平台和清空ID字段
        config.addProperty("liveType", "");
        config.addProperty("douyinID", "");
        config.addProperty("kuaishouID", "");
        config.addProperty("BilibiliID", "");
        config.addProperty("tiktokID", "");
        config.addProperty("twitchID", "");

        LOG.info("Configuration updated for non-offline account mode");
    }

    /**
     * @description: 加载现有配置文件或创建默认配置
     * @param configFile - 配置文件路径
     * @return JsonObject - 配置对象
     * @throws IOException - 文件操作异常
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
     * @param config - 配置对象
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
     * @param configFile - 配置文件路径
     * @param config - 配置对象
     * @throws IOException - 文件操作异常
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