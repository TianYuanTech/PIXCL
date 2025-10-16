/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl;

import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.JarUtils;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.NativeUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.windows.Kernel32;
import org.jackhuang.hmcl.util.platform.windows.WinConstants;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.EnumSet;

/**
 * @description: 存储应用程序的元数据信息
 * 该类负责管理应用程序的基本信息、版本信息、URL配置等
 * 并在初始化时根据地区判断动态设置服务器URL
 */
public final class Metadata {
    private Metadata() {
    }

    public static final String NAME = "PIXCL";
    public static final String FULL_NAME = "Pixel Launcher";
    public static final String VERSION = System.getProperty("hmcl.version.override", JarUtils.getManifestAttribute("Implementation-Version", "@develop@"));

    public static final int MINIMUM_REQUIRED_JAVA_VERSION = 8;
    public static final int MINIMUM_SUPPORTED_JAVA_VERSION = 11;

    public static final String TITLE = NAME + " " + VERSION;
    public static final String FULL_TITLE = FULL_NAME + " v" + VERSION;

    public static final String PUBLISH_URL = "https://api.pixellive.cn";
    public static final String TIKTOK_SERVER_URL = "https://tkapi.pixellive.cn";
    public static final String ABOUT_URL;
    private static final String MCPATCH_DOMESTIC_SERVER = "http://api.pixellive.cn:8080";

    public static final String HMCL_UPDATE_URL;
    private static final String MCPATCH_OVERSEAS_SERVER = "http://tkapi.pixellive.cn:8080";

    public static final String DOCS_URL = "https://docs.hmcl.net";
    public static final String CHANGELOG_URL;
    public static final String CONTACT_URL = DOCS_URL + "/help.html";
    public static final String EULA_URL = DOCS_URL + "/eula/hmcl.html";
    public static final String GROUPS_URL = "https://www.bilibili.com/opus/905435541874409529";

    public static final String BUILD_CHANNEL = JarUtils.getManifestAttribute("Build-Channel", "nightly");
    public static final String GITHUB_SHA = JarUtils.getManifestAttribute("GitHub-SHA", null);

    public static final Path CURRENT_DIRECTORY = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
    public static final Path MINECRAFT_DIRECTORY = OperatingSystem.getWorkingDirectory("minecraft");
    public static final Path HMCL_GLOBAL_DIRECTORY;
    public static final Path HMCL_CURRENT_DIRECTORY;
    public static final Path DEPENDENCIES_DIRECTORY;

    static {
        // 初始化目录路径
        String hmclHome = System.getProperty("hmcl.home");
        if (hmclHome == null) {
            if (OperatingSystem.CURRENT_OS.isLinuxOrBSD()) {
                String xdgData = System.getenv("XDG_DATA_HOME");
                if (StringUtils.isNotBlank(xdgData)) {
                    HMCL_GLOBAL_DIRECTORY = Paths.get(xdgData, "hmcl").toAbsolutePath().normalize();
                } else {
                    HMCL_GLOBAL_DIRECTORY = Paths.get(System.getProperty("user.home"), ".local", "share", "hmcl").toAbsolutePath().normalize();
                }
            } else {
                HMCL_GLOBAL_DIRECTORY = OperatingSystem.getWorkingDirectory("hmcl");
            }
        } else {
            HMCL_GLOBAL_DIRECTORY = Paths.get(hmclHome).toAbsolutePath().normalize();
        }

        String hmclCurrentDir = System.getProperty("hmcl.dir");
        HMCL_CURRENT_DIRECTORY = hmclCurrentDir != null
                ? Paths.get(hmclCurrentDir).toAbsolutePath().normalize()
                : CURRENT_DIRECTORY.resolve(".hmcl");
        DEPENDENCIES_DIRECTORY = HMCL_CURRENT_DIRECTORY.resolve("dependencies");

        // 根据用户地区设置服务器URL
        // 中国大陆用户使用PUBLISH_URL，海外用户使用TIKTOK_SERVER_URL
        String baseServerUrl = isInMainlandChina() ? PUBLISH_URL : TIKTOK_SERVER_URL;
        System.out.println("用户所用链接为：" + baseServerUrl);
        ABOUT_URL = PUBLISH_URL + "/about";
        HMCL_UPDATE_URL = System.getProperty("hmcl.update_source.override", baseServerUrl + "/update_link");

        CHANGELOG_URL = PUBLISH_URL + "/update_link";
    }


    /**
     * @description: 判断用户是否位于中国大陆地区
     * 通过多种方式检测用户地理位置，包括时区、Windows系统地理位置API等
     * @return boolean - 位于中国大陆返回true，否则返回false
     */
    private static boolean isInMainlandChina() {
        String zoneId = ZoneId.systemDefault().getId();

        // 检查时区标识符是否为中国大陆时区
        if (Arrays.asList(
                "Asia/Shanghai",
                "Asia/Beijing",      // 非标准名称，但某些系统使用
                "Asia/Chongqing",
                "Asia/Chungking",
                "Asia/Harbin"
        ).contains(zoneId)) {
            return true;
        }

        // Windows系统通过系统API获取地理位置
        if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS && NativeUtils.USE_JNA) {
            Kernel32 kernel32 = Kernel32.INSTANCE;
            // 地理位置ID 45 对应中国
            if (kernel32 != null && kernel32.GetUserGeoID(WinConstants.GEOCLASS_NATION) == 45) {
                return true;
            }
        } else if (OperatingSystem.CURRENT_OS == OperatingSystem.LINUX && "GMT+08:00".equals(zoneId)) {
            // Linux系统可能使用非标准时区名称，Java会将其解析为GMT+08:00
            return true;
        }

        return false;
    }

    public static String getMcPatchServerUrl() {
        boolean isMainland = isInMainlandChina();
        String serverUrl = isMainland ? MCPATCH_DOMESTIC_SERVER : MCPATCH_OVERSEAS_SERVER;
        System.out.println("检测到用户位于" + (isMainland ? "中国大陆" : "海外地区") + "，使用服务器: " + serverUrl);
        return serverUrl;
    }

    /**
     * @description: 检查当前构建是否为稳定版本
     * @return boolean - 稳定版本返回true
     */
    public static boolean isStable() {
        return "stable".equals(BUILD_CHANNEL);
    }

    /**
     * @description: 检查当前构建是否为开发版本
     * @return boolean - 开发版本返回true
     */
    public static boolean isDev() {
        return "dev".equals(BUILD_CHANNEL);
    }

    /**
     * @description: 检查当前构建是否为每夜构建版本
     * @return boolean - 每夜构建版本返回true
     */
    public static boolean isNightly() {
        return !isStable() && !isDev();
    }

    /**
     * @description: 获取建议的Java下载链接
     * 根据当前操作系统和架构返回合适的Java下载链接
     * @return String - Java下载链接，如果不支持当前平台则返回null
     */
    public static @Nullable String getSuggestedJavaDownloadLink() {
        if (OperatingSystem.CURRENT_OS == OperatingSystem.LINUX && Architecture.SYSTEM_ARCH == Architecture.LOONGARCH64_OW)
            return "https://www.loongnix.cn/zh/api/java/downloads-jdk21/index.html";
        else {
            EnumSet<Architecture> supportedArchitectures;
            if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS)
                supportedArchitectures = EnumSet.of(Architecture.X86_64, Architecture.X86, Architecture.ARM64);
            else if (OperatingSystem.CURRENT_OS == OperatingSystem.LINUX)
                supportedArchitectures = EnumSet.of(
                        Architecture.X86_64, Architecture.X86,
                        Architecture.ARM64, Architecture.ARM32,
                        Architecture.RISCV64, Architecture.LOONGARCH64
                );
            else if (OperatingSystem.CURRENT_OS == OperatingSystem.MACOS)
                supportedArchitectures = EnumSet.of(Architecture.X86_64, Architecture.ARM64);
            else
                supportedArchitectures = EnumSet.noneOf(Architecture.class);

            if (supportedArchitectures.contains(Architecture.SYSTEM_ARCH))
                return String.format("https://docs.hmcl.net/downloads/%s/%s.html",
                        OperatingSystem.CURRENT_OS.getCheckedName(),
                        Architecture.SYSTEM_ARCH.getCheckedName()
                );
            else
                return null;
        }
    }
}