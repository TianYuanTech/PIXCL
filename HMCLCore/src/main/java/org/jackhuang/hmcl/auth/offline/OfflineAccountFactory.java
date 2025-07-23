// 文件：OfflineAccountFactory.java
// 路径：HMCLCore/src/main/java/org/jackhuang/hmcl/auth/offline/OfflineAccountFactory.java
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
package org.jackhuang.hmcl.auth.offline;

import org.jackhuang.hmcl.auth.AccountFactory;
import org.jackhuang.hmcl.auth.CharacterSelector;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorArtifactProvider;
import org.jackhuang.hmcl.util.gson.UUIDTypeAdapter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.jackhuang.hmcl.util.Lang.tryCast;

/**
 * @description: 离线账户工厂类，负责创建和管理离线账户
 */
public final class OfflineAccountFactory extends AccountFactory<OfflineAccount> {

    /**
     * @description: authlib注入器组件下载器
     */
    private final AuthlibInjectorArtifactProvider downloader;

    /**
     * @description: 构造函数
     * @param downloader authlib注入器组件下载器
     */
    public OfflineAccountFactory(AuthlibInjectorArtifactProvider downloader) {
        this.downloader = downloader;
    }

    /**
     * @description: 获取登录类型
     * @return AccountLoginType 登录类型
     */
    @Override
    public AccountLoginType getLoginType() {
        return AccountLoginType.USERNAME;
    }

    /**
     * @description: 创建离线账户
     * @param username 用户名
     * @param uuid 用户UUID
     * @return OfflineAccount 离线账户实例
     */
    public OfflineAccount create(String username, UUID uuid) {
        return new OfflineAccount(downloader, username, uuid, null);
    }

    /**
     * @description: 创建离线账户
     * @param selector 角色选择器
     * @param username 用户名
     * @param password 密码
     * @param progressCallback 进度回调
     * @param additionalData 附加数据
     * @return OfflineAccount 离线账户实例
     */
    @Override
    public OfflineAccount create(CharacterSelector selector, String username, String password, ProgressCallback progressCallback, Object additionalData) {
        AdditionalData data;
        UUID uuid;
        Skin skin;
        String liveType = null;
        Map<String, String> liveRooms = new HashMap<>();
        String cardKey = null;
        String accountMode = null;

        if (additionalData != null) {
            data = (AdditionalData) additionalData;
            uuid = data.uuid == null ? getUUIDFromUserName(username) : data.uuid;
            skin = data.skin;
            liveType = data.liveType;
            liveRooms = data.liveRooms != null ? new HashMap<>(data.liveRooms) : new HashMap<>();
            cardKey = data.cardKey;
            accountMode = data.accountMode;
        } else {
            uuid = getUUIDFromUserName(username);
            skin = null;
        }

        return new OfflineAccount(downloader, username, uuid, skin, liveType, liveRooms, cardKey, accountMode);
    }

    /**
     * @description: 从存储数据创建离线账户
     * @param storage 存储数据映射
     * @return OfflineAccount 离线账户实例
     */
    @Override
    public OfflineAccount fromStorage(Map<Object, Object> storage) {
        String username = tryCast(storage.get("username"), String.class)
                .orElseThrow(() -> new IllegalStateException("Offline account configuration malformed."));
        UUID uuid = tryCast(storage.get("uuid"), String.class)
                .map(UUIDTypeAdapter::fromString)
                .orElse(getUUIDFromUserName(username));
        Skin skin = Skin.fromStorage(tryCast(storage.get("skin"), Map.class).orElse(null));

        // 读取新增的字段
        String liveType = tryCast(storage.get("liveType"), String.class).orElse(null);
        String cardKey = tryCast(storage.get("cardKey"), String.class).orElse(null);
        String accountMode = tryCast(storage.get("accountMode"), String.class).orElse(null);

        // 处理liveRooms字段，支持向后兼容
        Map<String, String> liveRooms = new HashMap<>();
        Object liveRoomsObj = storage.get("liveRooms");
        if (liveRoomsObj instanceof Map) {
            // 新格式：Map结构
            Map<?, ?> roomMap = (Map<?, ?>) liveRoomsObj;
            for (Map.Entry<?, ?> entry : roomMap.entrySet()) {
                if (entry.getKey() instanceof String && entry.getValue() instanceof String) {
                    liveRooms.put((String) entry.getKey(), (String) entry.getValue());
                }
            }
        } else {
            // 向后兼容：旧的单一房间号格式
            String oldLiveRoom = tryCast(storage.get("liveRoom"), String.class).orElse(null);
            if (oldLiveRoom != null && liveType != null) {
                liveRooms.put(liveType, oldLiveRoom);
            }
        }

        return new OfflineAccount(downloader, username, uuid, skin, liveType, liveRooms, cardKey, accountMode);
    }

    /**
     * @description: 根据用户名生成UUID
     * @param username 用户名
     * @return UUID 生成的UUID
     */
    public static UUID getUUIDFromUserName(String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(UTF_8));
    }

    /**
     * @description: 附加数据类，用于传递额外的账户信息
     */
    public static class AdditionalData {
        /**
         * @description: 用户UUID
         */
        private final UUID uuid;

        /**
         * @description: 皮肤信息
         */
        private final Skin skin;

        /**
         * @description: 直播类型
         */
        private final String liveType;

        /**
         * @description: 多平台直播房间号映射
         */
        private final Map<String, String> liveRooms;

        /**
         * @description: 卡密
         */
        private final String cardKey;

        /**
         * @description: 账户模式
         */
        private final String accountMode;

        /**
         * @description: 简单构造函数
         * @param uuid 用户UUID
         * @param skin 皮肤信息
         */
        public AdditionalData(UUID uuid, Skin skin) {
            this(uuid, skin, null, new HashMap<>(), null, null);
        }

        /**
         * @description: 完整构造函数
         * @param uuid 用户UUID
         * @param skin 皮肤信息
         * @param liveType 直播类型
         * @param liveRooms 多平台直播房间号
         * @param cardKey 卡密
         * @param accountMode 账户模式
         */
        public AdditionalData(UUID uuid, Skin skin, String liveType, Map<String, String> liveRooms, String cardKey, String accountMode) {
            this.uuid = uuid;
            this.skin = skin;
            this.liveType = liveType;
            this.liveRooms = liveRooms != null ? liveRooms : new HashMap<>();
            this.cardKey = cardKey;
            this.accountMode = accountMode;
        }

        /**
         * @description: 便捷构造函数，用于单平台房间号创建
         * @param uuid 用户UUID
         * @param skin 皮肤信息
         * @param liveType 直播类型
         * @param singleRoomNumber 单个房间号
         * @param cardKey 卡密
         * @param accountMode 账户模式
         */
        public AdditionalData(UUID uuid, Skin skin, String liveType, String singleRoomNumber, String cardKey, String accountMode) {
            this.uuid = uuid;
            this.skin = skin;
            this.liveType = liveType;
            this.liveRooms = new HashMap<>();
            if (liveType != null && singleRoomNumber != null) {
                this.liveRooms.put(liveType, singleRoomNumber);
            }
            this.cardKey = cardKey;
            this.accountMode = accountMode;
        }

        /**
         * @description: 获取直播类型
         * @return String 直播类型
         */
        public String getLiveType() {
            return liveType;
        }

        /**
         * @description: 获取多平台直播房间号
         * @return Map<String, String> 平台到房间号的映射
         */
        public Map<String, String> getLiveRooms() {
            return liveRooms;
        }

        /**
         * @description: 获取卡密
         * @return String 卡密
         */
        public String getCardKey() {
            return cardKey;
        }

        /**
         * @description: 获取账户模式
         * @return String 账户模式
         */
        public String getAccountMode() {
            return accountMode;
        }
    }
}