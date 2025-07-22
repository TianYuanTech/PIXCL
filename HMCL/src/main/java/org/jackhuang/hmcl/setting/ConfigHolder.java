/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.util.FileSaver;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.JarUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @description: 配置文件持有者和管理器
 * 负责配置文件的加载、保存和管理操作
 */
public final class ConfigHolder {

    private ConfigHolder() {
    }

    public static final String CONFIG_FILENAME = "hmcl.json";
    public static final String CONFIG_FILENAME_LINUX = ".hmcl.json";
    public static final Path GLOBAL_CONFIG_PATH = Metadata.HMCL_GLOBAL_DIRECTORY.resolve("config.json");

    // 海外API切换相关常量
    public static final String KOKUGAI_FILENAME = "kokugai";
    public static final String KOKUGAI_CONTENT = "gaikoku";

    private static Path configLocation;
    private static Config configInstance;
    private static GlobalConfig globalConfigInstance;
    private static boolean newlyCreated;
    private static boolean ownerChanged = false;

    /**
     * @description: 获取当前配置实例
     * @return Config - 配置实例
     * @throws IllegalStateException - 如果配置尚未加载
     */
    public static Config config() {
        if (configInstance == null) {
            throw new IllegalStateException("Configuration hasn't been loaded");
        }
        return configInstance;
    }

    /**
     * @description: 获取全局配置实例
     * @return GlobalConfig - 全局配置实例
     * @throws IllegalStateException - 如果配置尚未加载
     */
    public static GlobalConfig globalConfig() {
        if (globalConfigInstance == null) {
            throw new IllegalStateException("Configuration hasn't been loaded");
        }
        return globalConfigInstance;
    }

    /**
     * @description: 获取配置文件位置
     * @return Path - 配置文件路径
     */
    public static Path configLocation() {
        return configLocation;
    }

    /**
     * @description: 检查配置是否为新创建的
     * @return boolean - 新创建返回true
     */
    public static boolean isNewlyCreated() {
        return newlyCreated;
    }

    /**
     * @description: 检查配置文件所有者是否发生变化
     * @return boolean - 所有者变化返回true
     */
    public static boolean isOwnerChanged() {
        return ownerChanged;
    }

    /**
     * @description: 检查是否应该使用海外API
     * 检查.hmcl文件夹下是否存在名为"kokugai"的文件，且文件内容为"gaikoku"
     * @return boolean - 如果应该使用海外API返回true，否则返回false
     */
    public static boolean shouldUseOverseasApi() {
        try {
            // 获取.hmcl目录路径
            Path hmclDirectory = Metadata.HMCL_CURRENT_DIRECTORY;
            Path kokugaiFile = hmclDirectory.resolve(KOKUGAI_FILENAME);

            // 检查文件是否存在
            if (!Files.exists(kokugaiFile)) {
                return false;
            }

            // 检查是否为普通文件（不是目录）
            if (!Files.isRegularFile(kokugaiFile)) {
                return false;
            }

            // 读取文件内容并检查
            String content = FileUtils.readText(kokugaiFile);
            if (content == null) {
                return false;
            }

            // 去除前后空白字符后比较内容
            return KOKUGAI_CONTENT.equals(content.trim());

        } catch (IOException e) {
            LOG.warning("Failed to read kokugai file: " + e.getMessage());
            return false;
        } catch (Exception e) {
            LOG.warning("Unexpected error during kokugai file check: " + e.getMessage());
            return false;
        }
    }

    /**
     * @description: 检查配置文件中指定字段的值
     * 此方法专门用于在应用程序早期初始化阶段检查配置字段
     * 不依赖于完整的配置加载流程
     * @param fieldName - 要检查的字段名
     * @param expectedValue - 期望的字段值
     * @return boolean - 如果字段存在且值匹配返回true，否则返回false
     */
    public static boolean checkConfigField(String fieldName, String expectedValue) {
        if (fieldName == null || expectedValue == null) {
            return false;
        }

        try {
            Path configPath = locateConfig();
            if (!Files.exists(configPath)) {
                return false;
            }

            String content = FileUtils.readText(configPath);
            if (content == null || content.trim().isEmpty()) {
                return false;
            }

            JsonElement jsonElement = JsonParser.parseString(content);
            if (!jsonElement.isJsonObject()) {
                return false;
            }

            JsonObject jsonObject = jsonElement.getAsJsonObject();
            if (!jsonObject.has(fieldName)) {
                return false;
            }

            JsonElement fieldElement = jsonObject.get(fieldName);
            if (!fieldElement.isJsonPrimitive()) {
                return false;
            }

            String actualValue = fieldElement.getAsString();
            return expectedValue.equals(actualValue);

        } catch (IOException e) {
            LOG.warning("Failed to read config file for field check: " + e.getMessage());
            return false;
        } catch (JsonParseException e) {
            LOG.warning("Failed to parse config JSON for field check: " + e.getMessage());
            return false;
        } catch (Exception e) {
            LOG.warning("Unexpected error during config field check: " + e.getMessage());
            return false;
        }
    }

    /**
     * @description: 初始化配置系统
     * 加载配置文件并设置相关的系统配置
     * @throws IOException - 文件操作失败时抛出异常
     */
    public static void init() throws IOException {
        if (configInstance != null) {
            throw new IllegalStateException("Configuration is already loaded");
        }

        configLocation = locateConfig();

        LOG.info("Config location: " + configLocation);

        configInstance = loadConfig();
        configInstance.addListener(source -> FileSaver.save(configLocation, configInstance.toJson()));

        globalConfigInstance = loadGlobalConfig();
        globalConfigInstance.addListener(source -> FileSaver.save(GLOBAL_CONFIG_PATH, globalConfigInstance.toJson()));

        Locale.setDefault(config().getLocalization().getLocale());
        I18n.setLocale(configInstance.getLocalization());
        LOG.setLogRetention(globalConfig().getLogRetention());
        Settings.init();

        if (newlyCreated) {
            LOG.info("Creating config file " + configLocation);
            FileUtils.saveSafely(configLocation, configInstance.toJson());
        }

        if (!Files.isWritable(configLocation)) {
            if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS
                    && configLocation.getFileSystem() == FileSystems.getDefault()
                    && configLocation.toFile().canWrite()) {
                // There are some serious problems with the implementation of Samba or OpenJDK
                throw new SambaException();
            } else {
                // the config cannot be saved
                // throw up the error now to prevent further data loss
                throw new IOException("Config at " + configLocation + " is not writable");
            }
        }
    }

    /**
     * @description: 定位配置文件
     * 按照优先级顺序查找配置文件位置
     * @return Path - 配置文件路径
     */
    private static Path locateConfig() {
        Path defaultConfigFile = Metadata.HMCL_CURRENT_DIRECTORY.resolve(CONFIG_FILENAME);
        if (Files.isRegularFile(defaultConfigFile))
            return defaultConfigFile;

        try {
            Path jarPath = JarUtils.thisJarPath();
            if (jarPath != null && Files.isRegularFile(jarPath) && Files.isWritable(jarPath)) {
                jarPath = jarPath.getParent();

                Path config = jarPath.resolve(CONFIG_FILENAME);
                if (Files.isRegularFile(config))
                    return config;

                Path dotConfig = jarPath.resolve(CONFIG_FILENAME_LINUX);
                if (Files.isRegularFile(dotConfig))
                    return dotConfig;
            }

        } catch (Throwable ignore) {
        }

        Path config = Paths.get(CONFIG_FILENAME);
        if (Files.isRegularFile(config))
            return config;

        Path dotConfig = Paths.get(CONFIG_FILENAME_LINUX);
        if (Files.isRegularFile(dotConfig))
            return dotConfig;

        // create new
        return defaultConfigFile;
    }

    /**
     * @description: 加载配置文件
     * 读取并解析配置文件内容，处理各种异常情况
     * @return Config - 配置实例
     * @throws IOException - 文件操作失败时抛出异常
     */
    private static Config loadConfig() throws IOException {
        if (Files.exists(configLocation)) {
            try {
                if (OperatingSystem.CURRENT_OS != OperatingSystem.WINDOWS
                        && "root".equals(System.getProperty("user.name"))
                        && !"root".equals(Files.getOwner(configLocation).getName())) {
                    ownerChanged = true;
                }
            } catch (IOException e1) {
                LOG.warning("Failed to get owner");
            }
            try {
                String content = FileUtils.readText(configLocation);
                Config deserialized = Config.fromJson(content);
                if (deserialized == null) {
                    LOG.info("Config is empty");
                } else {
                    ConfigUpgrader.upgradeConfig(deserialized, content);
                    return deserialized;
                }
            } catch (JsonParseException e) {
                LOG.warning("Malformed config.", e);
            }
        }

        newlyCreated = true;
        return new Config();
    }

    /**
     * @description: 加载全局配置文件
     * 读取并解析全局配置文件内容
     * @return GlobalConfig - 全局配置实例
     * @throws IOException - 文件操作失败时抛出异常
     */
    private static GlobalConfig loadGlobalConfig() throws IOException {
        if (Files.exists(GLOBAL_CONFIG_PATH)) {
            try {
                String content = FileUtils.readText(GLOBAL_CONFIG_PATH);
                GlobalConfig deserialized = GlobalConfig.fromJson(content);
                if (deserialized == null) {
                    LOG.info("Config is empty");
                } else {
                    return deserialized;
                }
            } catch (JsonParseException e) {
                LOG.warning("Malformed config.", e);
            }
        }

        LOG.info("Creating an empty global config");
        return new GlobalConfig();
    }
}