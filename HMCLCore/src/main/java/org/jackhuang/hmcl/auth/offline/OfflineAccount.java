// 文件：OfflineAccount.java
// 路径：HMCLCore/src/main/java/org/jackhuang/hmcl/auth/offline/OfflineAccount.java
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

import javafx.beans.binding.ObjectBinding;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.AuthInfo;
import org.jackhuang.hmcl.auth.AuthenticationException;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorArtifactInfo;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorArtifactProvider;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorDownloadException;
import org.jackhuang.hmcl.auth.yggdrasil.Texture;
import org.jackhuang.hmcl.auth.yggdrasil.TextureType;
import org.jackhuang.hmcl.game.Arguments;
import org.jackhuang.hmcl.game.LaunchOptions;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.ToStringBuilder;
import org.jackhuang.hmcl.util.gson.UUIDTypeAdapter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import static java.util.Objects.requireNonNull;
import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Pair.pair;

/**
 * @description: 离线账户实现类，支持离线模式和卡密模式，现在支持多平台房间号
 */
public class OfflineAccount extends Account {

    /**
     * @description: authlib注入器组件下载器
     */
    private final AuthlibInjectorArtifactProvider downloader;

    /**
     * @description: 用户名
     */
    private final String username;

    /**
     * @description: 用户UUID
     */
    private final UUID uuid;

    /**
     * @description: 皮肤信息
     */
    private Skin skin;

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
     * @description: 离线账户类的额外数据字段
     * 用于存储liveType、liveRooms、cardKey等扩展信息
     */
    private String extraData;

    /**
     * @description: 构造函数
     * @param downloader authlib注入器组件下载器
     * @param username 用户名
     * @param uuid 用户UUID
     * @param skin 皮肤信息
     */
    protected OfflineAccount(AuthlibInjectorArtifactProvider downloader, String username, UUID uuid, Skin skin) {
        this(downloader, username, uuid, skin, null, new HashMap<>(), null, null);
    }

    /**
     * @description: 完整构造函数
     * @param downloader authlib注入器组件下载器
     * @param username 用户名
     * @param uuid 用户UUID
     * @param skin 皮肤信息
     * @param liveType 直播类型
     * @param liveRooms 多平台直播房间号
     * @param cardKey 卡密
     * @param accountMode 账户模式
     */
    protected OfflineAccount(AuthlibInjectorArtifactProvider downloader, String username, UUID uuid, Skin skin,
                             String liveType, Map<String, String> liveRooms, String cardKey, String accountMode) {
        this.downloader = requireNonNull(downloader);
        this.username = requireNonNull(username);
        this.uuid = requireNonNull(uuid);
        this.skin = skin;
        this.liveType = liveType;
        this.liveRooms = liveRooms != null ? new HashMap<>(liveRooms) : new HashMap<>();
        this.cardKey = cardKey;
        this.accountMode = accountMode;

        if (StringUtils.isBlank(username)) {
            throw new IllegalArgumentException("Username cannot be blank");
        }
    }

    /**
     * @description: 获取authlib注入器组件下载器
     * @return AuthlibInjectorArtifactProvider 下载器实例
     */
    public AuthlibInjectorArtifactProvider getDownloader() {
        return downloader;
    }

    /**
     * @description: 获取用户UUID
     * @return UUID 用户UUID
     */
    @Override
    public UUID getUUID() {
        return uuid;
    }

    /**
     * @description: 获取用户名
     * @return String 用户名
     */
    @Override
    public String getUsername() {
        return username;
    }

    /**
     * @description: 获取角色名
     * @return String 角色名
     */
    @Override
    public String getCharacter() {
        return username;
    }

    /**
     * @description: 获取账户标识符
     * @return String 账户标识符
     */
    @Override
    public String getIdentifier() {
        return username + ":" + username;
    }

    /**
     * @description: 获取皮肤信息
     * @return Skin 皮肤信息
     */
    public Skin getSkin() {
        return skin;
    }

    /**
     * @description: 设置皮肤信息
     * @param skin 皮肤信息
     */
    public void setSkin(Skin skin) {
        this.skin = skin;
        invalidate();
    }

    /**
     * @description: 获取直播类型
     * @return String 直播类型
     */
    public String getLiveType() {
        return liveType;
    }

    /**
     * @description: 获取多平台直播房间号映射
     * @return Map<String, String> 平台到房间号的映射
     */
    public Map<String, String> getLiveRooms() {
        return new HashMap<>(liveRooms);
    }

    /**
     * @description: 获取指定平台的直播房间号
     * @param platform 平台名称
     * @return String 房间号，如果不存在则返回null
     */
    public String getLiveRoom(String platform) {
        return liveRooms.get(platform);
    }

    /**
     * @description: 获取当前选择平台的直播房间号
     * @return String 当前平台房间号，如果当前平台未设置则返回null
     */
    public String getCurrentLiveRoom() {
        if (liveType == null) {
            return null;
        }
        return liveRooms.get(liveType);
    }

    /**
     * @description: 向后兼容方法，获取单个房间号（已弃用）
     * @return String 房间号
     * @deprecated 使用 getCurrentLiveRoom() 或 getLiveRoom(String platform) 替代
     */
    @Deprecated
    public String getLiveRoom() {
        return getCurrentLiveRoom();
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

    /**
     * @description: 获取账户的额外数据
     * @return String 额外数据的JSON字符串
     */
    public String getExtraData() {
        return extraData;
    }

    /**
     * @description: 设置账户的额外数据
     * @param extraData 额外数据的JSON字符串
     */
    public void setExtraData(String extraData) {
        this.extraData = extraData;
    }

    /**
     * @description: 判断是否需要加载authlib注入器
     * @param skin 皮肤信息
     * @return boolean 是否需要加载
     */
    protected boolean loadAuthlibInjector(Skin skin) {
        return skin != null && skin.getType() != Skin.Type.DEFAULT;
    }

    /**
     * @description: 登录方法
     * @return AuthInfo 认证信息
     * @throws AuthenticationException 认证异常
     */
    @Override
    public AuthInfo logIn() throws AuthenticationException {
        // Using "legacy" user type here because "mojang" user type may cause "invalid session token" or "disconnected" when connecting to a game server.
        AuthInfo authInfo = new AuthInfo(username, uuid, UUIDTypeAdapter.fromUUID(UUID.randomUUID()), AuthInfo.USER_TYPE_MSA, "{}");

        if (loadAuthlibInjector(skin)) {
            CompletableFuture<AuthlibInjectorArtifactInfo> artifactTask = CompletableFuture.supplyAsync(() -> {
                try {
                    return downloader.getArtifactInfo();
                } catch (IOException e) {
                    throw new CompletionException(new AuthlibInjectorDownloadException(e));
                }
            });

            AuthlibInjectorArtifactInfo artifact;
            try {
                artifact = artifactTask.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AuthenticationException(e);
            } catch (ExecutionException e) {
                if (e.getCause() instanceof AuthenticationException) {
                    throw (AuthenticationException) e.getCause();
                } else {
                    throw new AuthenticationException(e.getCause());
                }
            }

            try {
                return new OfflineAuthInfo(authInfo, artifact);
            } catch (Exception e) {
                throw new AuthenticationException(e);
            }
        } else {
            return authInfo;
        }
    }

    /**
     * @description: 离线认证信息内部类
     */
    private class OfflineAuthInfo extends AuthInfo {
        private final AuthlibInjectorArtifactInfo artifact;
        private YggdrasilServer server;

        /**
         * @description: 构造函数
         * @param authInfo 认证信息
         * @param artifact authlib注入器组件信息
         */
        public OfflineAuthInfo(AuthInfo authInfo, AuthlibInjectorArtifactInfo artifact) {
            super(authInfo.getUsername(), authInfo.getUUID(), authInfo.getAccessToken(), USER_TYPE_MSA, authInfo.getUserProperties());
            this.artifact = artifact;
        }

        /**
         * @description: 获取启动参数
         * @param options 启动选项
         * @return Arguments 启动参数
         * @throws IOException IO异常
         */
        @Override
        public Arguments getLaunchArguments(LaunchOptions options) throws IOException {
            if (!options.isDaemon()) return null;

            server = new YggdrasilServer(0);
            server.start();

            try {
                server.addCharacter(new YggdrasilServer.Character(uuid, username,
                        skin != null ? skin.load(username).run() : null));
            } catch (IOException e) {
                // ignore
            } catch (Exception e) {
                throw new IOException(e);
            }

            return new Arguments().addJVMArguments(
                    "-javaagent:" + artifact.getLocation().toString() + "=" + "http://localhost:" + server.getListeningPort(),
                    "-Dauthlibinjector.side=client"
            );
        }

        /**
         * @description: 关闭资源
         * @throws Exception 异常
         */
        @Override
        public void close() throws Exception {
            super.close();

            if (server != null)
                server.stop();
        }
    }

    /**
     * @description: 离线游戏方法
     * @return AuthInfo 认证信息
     * @throws AuthenticationException 认证异常
     */
    @Override
    public AuthInfo playOffline() throws AuthenticationException {
        return logIn();
    }

    /**
     * @description: 将账户数据序列化为存储格式
     * @return Map<Object, Object> 存储数据映射
     */
    @Override
    public Map<Object, Object> toStorage() {
        return mapOf(
                pair("uuid", UUIDTypeAdapter.fromUUID(uuid)),
                pair("username", username),
                pair("skin", skin == null ? null : skin.toStorage()),
                pair("liveType", liveType),
                pair("liveRooms", new HashMap<>(liveRooms)),
                pair("cardKey", cardKey),
                pair("accountMode", accountMode)
        );
    }

    /**
     * @description: 获取材质信息
     * @return ObjectBinding<Optional<Map<TextureType, Texture>>> 材质信息绑定
     */
    @Override
    public ObjectBinding<Optional<Map<TextureType, Texture>>> getTextures() {
        return super.getTextures();
    }

    /**
     * @description: 转换为字符串
     * @return String 字符串表示
     */
    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("username", username)
                .append("uuid", uuid)
                .append("liveType", liveType)
                .append("liveRooms", liveRooms)
                .append("accountMode", accountMode)
                .toString();
    }

    /**
     * @description: 计算哈希码
     * @return int 哈希码
     */
    @Override
    public int hashCode() {
        return username.hashCode();
    }

    /**
     * @description: 判断对象是否相等
     * @param obj 比较对象
     * @return boolean 是否相等
     */
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof OfflineAccount))
            return false;
        OfflineAccount another = (OfflineAccount) obj;
        return isPortable() == another.isPortable() && username.equals(another.username);
    }
}