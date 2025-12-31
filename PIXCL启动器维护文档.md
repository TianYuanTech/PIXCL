# PIXCL 直播启动器项目维护文档

> **版本**: 1.0  
> **最后更新**: 2025年12月31日  
> **项目基于**: HMCL (Hello Minecraft! Launcher)

---

## 📌 第一章：重要注意事项

### 1.1 开源协议声明

本项目 **PIXCL** (Pixel Launcher) 是基于 [HMCL](https://github.com/huangyuhui/HMCL) 进行修改的衍生版本，必须严格遵守以下开源协议要求：

#### HMCL 开源协议 (GPLv3)

```
Hello Minecraft! Launcher
Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.
```

**必须遵守的要求**：
1. ✅ 将启动器源代码开源
2. ✅ 保留原作者 huangyuhui 的版权声明
3. ✅ 版本号与名字必须与原 HMCL 有明显差别
4. ✅ 使用相同的 GPLv3 协议发布

#### McPatchClient 开源协议

McPatchClient 更新器项目同样使用 GPLv3 协议，因此在启动器的**关于页面**中已新增：
- McPatchClient 作者信息
- McPatchClient 项目开源协议声明

### 1.2 项目命名规范

| 项目 | 原名称 | 修改后名称 |
|------|--------|------------|
| 启动器名称 | HMCL | PIXCL |
| 完整名称 | Hello Minecraft! Launcher | Pixel Launcher |
| 版本号格式 | 3.x.x.x | 1.x.x |

### 1.3 项目结构概述

```
PIXCL/
├── HMCL/                    # 启动器主模块（UI、业务逻辑）
├── HMCLCore/                # 核心库模块（账户、启动器核心功能）
├── McPatchClient/           # 新增：文件更新器模块（Kotlin实现）
├── buildSrc/                # Gradle构建脚本
├── config/                  # 代码检查配置
├── gradle/                  # Gradle包装器
└── lib/                     # 第三方库
```

---

## 📦 第二章：重要修改类结构

### 2.1 类文件结构树

```
PIXCL/
│
├── HMCLCore/src/main/java/org/jackhuang/hmcl/
│   ├── auth/offline/
│   │   └── OfflineAccountFactory.java     ★ [修改] 离线账户工厂，新增直播/卡密模式支持
│   ├── launch/
│   │   └── Launcher.java                   [轻微修改] 游戏启动器抽象基类
│   └── task/
│       └── Task.java                       [轻微修改] 异步任务基类
│
├── HMCL/src/main/java/org/jackhuang/hmcl/
│   ├── Launcher.java                       ★ [修改] 应用程序入口类
│   ├── Metadata.java                       ★ [修改] 应用元数据，包含服务器URL配置
│   │
│   ├── game/
│   │   ├── LauncherHelper.java             ★★ [重要修改] 启动流程核心，集成McPatch
│   │   └── PixelLiveGameConfig.java        ★★ [新增] 直播游戏配置管理类
│   │
│   ├── setting/
│   │   └── ConfigHolder.java               ★ [修改] 配置文件管理，新增海外API检测
│   │
│   ├── upgrade/
│   │   ├── IntegrityChecker.java           ★ [修改] 完整性检查（禁用自检）
│   │   └── UpdateHandler.java              [轻微修改] 更新处理器
│   │
│   ├── util/
│   │   └── AuthorizationChecker.java       ★★ [新增] 授权检查器（直播间/卡密验证）
│   │
│   └── ui/
│       ├── UpgradeDialog.java              ★ [修改] 升级对话框，从服务器获取更新日志
│       ├── account/
│       │   ├── AccountListPage.java        ★ [修改] 账户列表页面，新增卡密模式入口
│       │   ├── CreateAccountPane.java      ★★ [修改] 账户创建面板，支持直播/卡密模式
│       │   └── PlayerAvatarView.java       ★ [新增] 玩家头像显示组件
│       └── main/
│           ├── MainPage.java               ★★ [重要修改] 主页面，集成启动前验证
│           ├── RootPage.java               ★★ [修改] 根页面，新增账户输入控件
│           ├── SettingsView.java           [轻微修改] 设置视图
│           └── LauncherSettingsPage.java   [轻微修改] 启动器设置页面
│
└── McPatchClient/src/main/kotlin/mcpatch/
    ├── McPatchClient.kt                    ★★ [新增项目] 文件更新器主类
    ├── callback/
    │   └── ProgressCallback.kt             ★ [新增] 进度回调接口
    └── ...                                 其他更新器相关类
```

### 2.2 各类用途说明

#### 2.2.1 核心修改类 (HMCLCore)

| 类名 | 路径 | 用途 |
|------|------|------|
| `OfflineAccountFactory` | `auth/offline/` | 离线账户工厂类，负责创建和管理离线账户。**新增**：支持直播类型(liveType)、多平台房间号(liveRooms)、卡密(cardKey)、账户模式(accountMode)等字段的存储和读取 |
| `Task` | `task/` | 异步任务基类，提供任务执行、进度更新、取消操作等功能 |
| `Launcher` | `launch/` | 游戏启动器抽象基类，定义启动参数和启动方法 |

#### 2.2.2 业务逻辑类 (HMCL)

| 类名 | 路径 | 用途 |
|------|------|------|
| `Launcher` | `/` | 应用程序入口类，负责JavaFX初始化、配置加载、更新检查 |
| `Metadata` | `/` | 应用元数据类，定义启动器名称、版本号、服务器URL。**新增**：动态服务器URL选择（国内/海外），TIKTOK_SERVER_URL配置 |
| `LauncherHelper` | `game/` | **核心类**：启动流程管理器，负责游戏启动的完整流程。**新增**：McPatch文件更新任务、PixelLiveGame配置更新 |
| `PixelLiveGameConfig` | `game/` | **新增类**：直播游戏配置管理，负责读取/创建/更新游戏目录下的`config/PixelLiveGame.json`配置文件 |
| `ConfigHolder` | `setting/` | 配置文件持有者，管理配置加载和保存。**新增**：`shouldUseOverseasApi()`方法检测kokugai文件 |
| `IntegrityChecker` | `upgrade/` | JAR完整性检查器。**修改**：`DISABLE_SELF_INTEGRITY_CHECK = true`禁用自检 |
| `UpdateHandler` | `upgrade/` | 启动器更新处理器，负责下载和应用更新 |
| `AuthorizationChecker` | `util/` | **新增类**：授权检查器，负责验证直播间授权和卡密授权状态 |

#### 2.2.3 UI类 (HMCL)

| 类名 | 路径 | 用途 |
|------|------|------|
| `MainPage` | `ui/main/` | **核心UI类**：主页面，包含启动按钮、版本选择、更新提示。**新增**：启动前的账户验证逻辑、直播间/卡密验证流程 |
| `RootPage` | `ui/main/` | 根页面容器，包含左侧栏和主内容区。**新增**：AccountInputControls账户输入控件 |
| `SettingsView` | `ui/main/` | 设置视图基类 |
| `LauncherSettingsPage` | `ui/main/` | 启动器设置页面，包含全局设置、Java管理、关于等标签页 |
| `AccountListPage` | `ui/account/` | 账户列表页面，显示已创建的账户。**修改**：新增离线模式和卡密模式两个入口 |
| `CreateAccountPane` | `ui/account/` | 账户创建对话框。**修改**：支持`AccountMode.OFFLINE`和`AccountMode.CARD_KEY`两种模式 |
| `PlayerAvatarView` | `ui/account/` | **新增类**：玩家头像显示组件，128x128像素画布 |
| `UpgradeDialog` | `ui/` | 升级对话框。**修改**：从服务器API获取JSON格式的更新日志 |

#### 2.2.4 McPatchClient模块 (新增)

| 类名 | 路径 | 用途 |
|------|------|------|
| `McPatchClient` | `mcpatch/` | 文件更新器主类，负责连接服务器检查文件更新 |
| `ProgressCallback` | `callback/` | 进度回调接口，用于向HMCL反馈更新进度 |
| `WorkThread` | `mcpatch/` | 工作线程，执行实际的文件更新任务 |

---

## 🚀 第三章：启动流程详解

### 3.1 启动流程概览

```
用户点击"启动游戏"按钮
         │
         ▼
    ┌─────────────────┐
    │  MainPage.launch()  │  ← 入口方法
    └─────────────────┘
         │
         ▼
    ┌─────────────────┐
    │ 验证用户名格式     │  ← USERNAME_CHECKER_PATTERN
    └─────────────────┘
         │
         ▼
    ┌─────────────────┐
    │ 获取账户输入数据   │  ← RootPage.getAccountInputData()
    └─────────────────┘
         │
         ▼
    ┌─────────────────────────┐
    │ 验证直播间/卡密授权      │  ← AuthorizationChecker
    └─────────────────────────┘
         │
         ▼
    ┌─────────────────────────┐
    │ 创建/更新账户并启动游戏  │  ← createAccountAndLaunch()
    └─────────────────────────┘
         │
         ▼
    ┌─────────────────┐
    │ LauncherHelper  │  ← 实际启动流程
    └─────────────────┘
         │
         ▼
    ┌──────────────────────────────────────────┐
    │  任务链执行顺序：                          │
    │  1. McPatch文件更新检查                   │
    │  2. 检查游戏状态和Java环境                 │
    │  3. 检查依赖完整性                        │
    │  4. 账户登录验证                          │
    │  5. 更新PixelLiveGame.json配置           │
    │  6. 启动游戏进程                          │
    └──────────────────────────────────────────┘
```

### 3.2 启动流程核心代码

#### 3.2.1 MainPage.launch() - 启动入口

```java
// 文件：MainPage.java
// 方法：launch()

private void launch() {
    // 1. 获取当前选中的游戏版本
    Profile profile = Profiles.getSelectedProfile();
    String version = getCurrentGame();

    // 2. 验证用户名格式
    String username = /* 从输入框获取 */;
    if (!USERNAME_CHECKER_PATTERN.matcher(username).matches()) {
        Controllers.dialog(i18n("account.username.invalid"), 
                i18n("input.error"), MessageDialogPane.MessageType.ERROR);
        return;
    }

    // 3. 获取账户输入数据
    AccountInputData inputData = RootPage.getAccountInputData();
    String loginMethod = inputData.getLoginMethod();
    String liveType = inputData.getLiveType();
    String roomNumber = inputData.getRoomNumber();
    String cardKey = inputData.getCardKey();

    // 4. 根据登录方式进行验证
    boolean authResult;
    String accountMode;

    if (i18n("auth.method.live").equals(loginMethod)) {
        // 直播模式验证
        authResult = AuthorizationChecker.checkWebcastAuthorization(liveType, roomNumber);
        accountMode = "LIVE";
    } else if (i18n("auth.method.cardkey").equals(loginMethod)) {
        // 卡密模式验证
        authResult = AuthorizationChecker.checkCardAuthorization(cardKey);
        accountMode = "CARD_KEY";
    } else {
        Controllers.dialog(i18n("launch.login.method.required"), ...);
        return;
    }

    // 5. 验证失败处理
    if (!authResult) {
        Controllers.dialog(i18n("verification.failed"), ...);
        return;
    }

    // 6. 创建账户并启动游戏
    createAccountAndLaunch(username, accountMode, liveType, roomNumber, cardKey);
}
```

#### 3.2.2 LauncherHelper.launch0() - 任务链构建

```java
// 文件：LauncherHelper.java
// 方法：launch0()

private void launch0() {
    // 构建任务链
    TaskExecutor executor = createMcPatchTask()  // 1. McPatch文件更新
            .thenComposeAsync(() -> checkGameState(...))  // 2. 检查游戏状态
            .thenComposeAsync(java -> {
                // 3. 处理依赖
                return Task.allOf(
                    dependencyManager.checkGameCompletionAsync(...),
                    // 模组包完成任务
                    // 渲染器加载任务
                );
            }).withStage("launch.state.dependencies")
            .thenComposeAsync(() -> new GameVerificationFixTask(...))
            .thenComposeAsync(() -> logIn(account).withStage("launch.state.logging_in"))
            .thenComposeAsync(authInfo -> Task.supplyAsync(() -> {
                // 4. 更新PixelLiveGame配置
                updatePixelLiveGameConfig(authInfo, launchOptions);
                
                // 5. 创建游戏启动器
                return new HMCLGameLauncher(...);
            }))
            .thenComposeAsync(launcher -> {
                // 6. 启动游戏
                return Task.supplyAsync(launcher::launch);
            })
            .executor();

    // 执行任务链
    executor.start();
}
```

#### 3.2.3 McPatchTask - 文件更新任务

```java
// 文件：LauncherHelper.java
// 内部类：McPatchTask

private class McPatchTask extends Task<Void> {
    
    @Override
    public void execute() throws Exception {
        LOG.info("开始文件更新检查");
        updateMessage(i18n("mcpatch.connecting"));
        updateProgress(0.0);

        // 创建进度回调
        McPatchProgressCallback progressCallback = new McPatchProgressCallback(this);

        // 异步执行McPatch
        CompletableFuture<Boolean> mcPatchFuture = CompletableFuture.supplyAsync(() -> {
            return McPatchClient.modloaderWithProgress(true, true, progressCallback);
        });

        // 等待执行完成，支持取消
        boolean hasUpdates = mcPatchFuture.get();
        
        updateProgress(1.0);
        String resultMessage = hasUpdates ? 
            i18n("mcpatch.completed") : i18n("mcpatch.up_to_date");
        updateMessage(resultMessage);
    }
}
```

---

## 👤 第四章：直播账户创建与权限检查

### 4.1 账户模式说明

PIXCL支持两种账户模式：

| 模式 | accountMode值 | 说明 |
|------|--------------|------|
| 直播模式 | `LIVE` | 使用直播间房间号验证授权 |
| 卡密模式 | `CARD_KEY` | 使用卡密验证授权 |

### 4.2 授权检查流程

```
         用户输入验证信息
                │
                ▼
        ┌───────────────────┐
        │ AuthorizationChecker │
        └───────────────────┘
                │
        ┌───────┴───────┐
        ▼               ▼
   直播模式           卡密模式
        │               │
        ▼               ▼
checkWebcastAuth    checkCardAuth
        │               │
        ▼               ▼
┌──────────────┐  ┌──────────────┐
│ 选择服务器URL  │  │ 使用默认服务器 │
│ (国内/TikTok) │  │              │
└──────────────┘  └──────────────┘
        │               │
        ▼               ▼
   HTTP GET请求      HTTP POST请求
        │               │
        └───────┬───────┘
                ▼
        解析JSON响应
        code==200 && data==true
                │
                ▼
        返回验证结果
```

### 4.3 授权检查核心代码

```java
// 文件：AuthorizationChecker.java

public class AuthorizationChecker {
    
    private static final int CONNECT_TIMEOUT = 5000;
    private static final int READ_TIMEOUT = 10000;

    /**
     * 检查直播间授权状态
     * @param platform 平台标识（抖音、快手、TikTok等）
     * @param studioName 直播间房间号
     * @return boolean 授权状态
     */
    public static boolean checkWebcastAuthorization(String platform, String studioName) {
        if (platform == null || studioName == null) return false;

        try {
            // 根据平台选择服务器
            String serverUrl = selectServerUrlByPlatform(platform);
            
            // 构建请求URL
            String fullUrl = serverUrl + "/check/webcast/authorization" +
                    "?platform=" + URLEncoder.encode(platform, "UTF-8") +
                    "&studioName=" + URLEncoder.encode(studioName, "UTF-8");

            // 发送请求并处理响应
            HttpURLConnection conn = createConnection(fullUrl, "GET");
            return processResponse(conn);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 检查卡密授权状态
     * @param cardKey 卡密
     * @return boolean 授权状态
     */
    public static boolean checkCardAuthorization(String cardKey) {
        if (cardKey == null || cardKey.isEmpty()) return false;

        try {
            String fullUrl = Metadata.PUBLISH_URL + "/check/card/authorization" +
                    "?cardKey=" + URLEncoder.encode(cardKey, "UTF-8");

            HttpURLConnection conn = createConnection(fullUrl, "POST");
            conn.getOutputStream().write(new byte[0]);
            
            return processResponse(conn);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 根据平台选择服务器URL
     */
    private static String selectServerUrlByPlatform(String platform) {
        String platformLower = platform.toLowerCase();
        // TikTok和Twitch使用海外服务器
        if ("tiktok".equals(platformLower) || "twitch".equals(platformLower)) {
            return Metadata.TIKTOK_SERVER_URL;  // https://tkapi.pixellive.cn
        }
        // 其他平台使用国内服务器
        return Metadata.PUBLISH_URL;  // https://api.pixellive.cn
    }

    /**
     * 处理HTTP响应
     */
    private static boolean processResponse(HttpURLConnection conn) throws IOException {
        int responseCode = conn.getResponseCode();
        if (responseCode != 200) return false;

        // 解析JSON响应
        String responseBody = readInputStream(conn.getInputStream());
        Map<?, ?> responseMap = GSON.fromJson(responseBody, Map.class);
        
        Object code = responseMap.get("code");
        Object data = responseMap.get("data");

        // 验证响应：code=200 且 data=true 表示授权有效
        return code instanceof Number && 
               ((Number) code).intValue() == 200 && 
               Boolean.TRUE.equals(data);
    }
}
```

### 4.4 账户创建与存储

```java
// 文件：MainPage.java
// 方法：createAccountAndLaunch()

private void createAccountAndLaunch(String username, String accountMode,
                                    String liveType, String roomNumber, String cardKey) {
    
    // 1. 查找现有账户以保留数据
    OfflineAccount existingAccount = findExistingOfflineAccount(username);
    
    // 2. 合并平台房间号数据
    Map<String, String> allPlatformRooms = new HashMap<>();
    if (existingAccount != null) {
        Map<String, String> existingRooms = existingAccount.getLiveRooms();
        if (existingRooms != null) {
            allPlatformRooms.putAll(existingRooms);
        }
    }
    
    // 3. 添加当前平台房间号
    if (liveType != null && roomNumber != null) {
        allPlatformRooms.put(liveType, roomNumber.trim());
    }
    
    // 4. 创建账户附加数据
    OfflineAccountFactory.AdditionalData additionalData = 
        new OfflineAccountFactory.AdditionalData(
            UUID.randomUUID(),
            existingAccount != null ? existingAccount.getSkin() : null,
            liveType,
            allPlatformRooms,
            cardKey,
            accountMode
        );
    
    // 5. 创建新账户
    Account account = Accounts.FACTORY_OFFLINE.create(
            null, username, null, null, additionalData);
    
    // 6. 添加账户并设置为当前账户
    Accounts.getAccounts().add(account);
    Accounts.setSelectedAccount(account);
    
    // 7. 启动游戏
    new LauncherHelper(profile, account, selectedVersion).launch();
}
```

---

## 🔄 第五章：启动器更新与账户本地化

### 5.1 启动器更新机制

#### 5.1.1 更新检查流程

```
启动器启动
    │
    ▼
UpdateChecker.init()
    │
    ▼
请求更新检查API
GET {PUBLISH_URL}/update_link
    │
    ▼
解析响应获取RemoteVersion
    │
    ▼
比较版本号
    │
    ├─ 有更新 → 显示更新气泡
    │           │
    │           ▼
    │      用户点击气泡
    │           │
    │           ▼
    │      UpdateHandler.updateFrom()
    │           │
    │           ▼
    │      显示UpgradeDialog
    │      (从API获取更新日志)
    │           │
    │           ▼
    │      用户确认更新
    │           │
    │           ▼
    │      下载新版本JAR
    │           │
    │           ▼
    │      执行更新并重启
    │
    └─ 无更新 → 继续正常启动
```

#### 5.1.2 更新URL配置

```java
// 文件：Metadata.java

public final class Metadata {
    // 服务器URL配置
    private static final String DEFAULT_PUBLISH_URL = "https://api.pixellive.cn";
    public static final String TIKTOK_SERVER_URL = "https://tkapi.pixellive.cn";
    
    // 动态确定的发布URL
    public static final String PUBLISH_URL;
    public static final String HMCL_UPDATE_URL;
    public static final String CHANGELOG_URL;

    static {
        // 根据kokugai文件决定使用哪个服务器
        String publishUrl = determinePublishUrl();
        PUBLISH_URL = publishUrl;
        
        HMCL_UPDATE_URL = PUBLISH_URL + "/update_link";
        CHANGELOG_URL = PUBLISH_URL + "/update_link";
    }

    private static String determinePublishUrl() {
        // 检查.hmcl/kokugai文件内容是否为"gaikoku"
        boolean useOverseasUrl = ConfigHolder.shouldUseOverseasApi();
        return useOverseasUrl ? TIKTOK_SERVER_URL : DEFAULT_PUBLISH_URL;
    }
}
```

### 5.2 账户本地化存储

#### 5.2.1 存储结构

账户数据存储在 `hmcl.json` 配置文件中：

```json
{
  "accounts": [
    {
      "type": "offline",
      "username": "PlayerName",
      "uuid": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
      "skin": {
        "type": "DEFAULT",
        "textureUrl": null
      },
      "liveType": "抖音",
      "liveRooms": {
        "抖音": "123456",
        "快手": "654321",
        "BiliBili": "789012"
      },
      "cardKey": "ABCD-EFGH-IJKL",
      "accountMode": "LIVE"
    }
  ],
  "selectedAccount": 0
}
```

#### 5.2.2 账户字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `type` | String | 账户类型，固定为 "offline" |
| `username` | String | 玩家用户名 |
| `uuid` | String | 玩家UUID |
| `skin` | Object | 皮肤信息 |
| `liveType` | String | 当前选择的直播平台 |
| `liveRooms` | Map | 多平台房间号映射 |
| `cardKey` | String | 卡密 |
| `accountMode` | String | 账户模式（LIVE/CARD_KEY） |

#### 5.2.3 账户读取代码

```java
// 文件：OfflineAccountFactory.java
// 方法：fromStorage()

@Override
public OfflineAccount fromStorage(Map<Object, Object> storage) {
    String username = tryCast(storage.get("username"), String.class)
            .orElseThrow(() -> new IllegalStateException("Account malformed"));
    
    UUID uuid = tryCast(storage.get("uuid"), String.class)
            .map(UUIDTypeAdapter::fromString)
            .orElse(getUUIDFromUserName(username));
    
    Skin skin = Skin.fromStorage(tryCast(storage.get("skin"), Map.class).orElse(null));
    
    // 读取直播相关字段
    String liveType = tryCast(storage.get("liveType"), String.class).orElse(null);
    String cardKey = tryCast(storage.get("cardKey"), String.class).orElse(null);
    String accountMode = tryCast(storage.get("accountMode"), String.class).orElse(null);

    // 读取多平台房间号（支持向后兼容）
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

    return new OfflineAccount(downloader, username, uuid, skin, 
            liveType, liveRooms, cardKey, accountMode);
}
```

### 5.3 PixelLiveGame.json 配置更新

#### 5.3.1 配置文件位置

```
游戏目录/
└── config/
    └── PixelLiveGame.json
```

#### 5.3.2 配置文件结构

```json
{
  "liveType": "DOUYIN",
  "douyinID": "123456",
  "kuaishouID": "654321",
  "kuaishouCookie": "",
  "tiktokID": "",
  "tiktokCookie": "",
  "BilibiliID": "789012",
  "bilibiliCookie": "",
  "twitchID": "",
  "twitchCookie": "",
  "xiaohongshuID": "",
  "chromeUrl": "",
  "isGiftMsgDisplay": true,
  "isNameDisplay": false,
  "isCardKeyModeEnabled": false,
  "cardKeyValue": ""
}
```

#### 5.3.3 配置更新代码

```java
// 文件：PixelLiveGameConfig.java

public class PixelLiveGameConfig {
    
    // 平台类型映射
    private static final Map<String, String> LIVE_TYPE_MAPPING = new HashMap<>();
    static {
        LIVE_TYPE_MAPPING.put("抖音", "DOUYIN");
        LIVE_TYPE_MAPPING.put("快手", "KUAISHOU");
        LIVE_TYPE_MAPPING.put("BiliBili", "BILIBILI");
        LIVE_TYPE_MAPPING.put("TikTok", "TIKTOK");
        LIVE_TYPE_MAPPING.put("Twitch", "TWITCH");
        LIVE_TYPE_MAPPING.put("小红书", "XIAOHONGSHU");
    }

    // 平台ID字段映射
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
     * 根据账户信息更新PixelLiveGame.json
     */
    public static void updatePixelLiveGameConfig(Account account, File gameDir) 
            throws IOException {
        
        Path configFile = gameDir.toPath().resolve("config/PixelLiveGame.json");
        
        // 读取或创建配置
        JsonObject config = loadOrCreateConfig(configFile);
        
        if (account instanceof OfflineAccount) {
            OfflineAccount offlineAccount = (OfflineAccount) account;
            String accountMode = offlineAccount.getAccountMode();
            
            // 设置卡密模式标志
            boolean isCardKeyMode = "CARD_KEY".equals(accountMode);
            config.addProperty("isCardKeyModeEnabled", isCardKeyMode);
            
            // 更新卡密
            String cardKey = offlineAccount.getCardKey();
            config.addProperty("cardKeyValue", cardKey != null ? cardKey : "");
            
            // 更新直播类型
            String liveType = offlineAccount.getLiveType();
            String mappedLiveType = LIVE_TYPE_MAPPING.getOrDefault(liveType, "DOUYIN");
            config.addProperty("liveType", mappedLiveType);
            
            // 更新所有平台ID
            Map<String, String> liveRooms = offlineAccount.getLiveRooms();
            if (liveRooms != null) {
                for (Map.Entry<String, String> entry : liveRooms.entrySet()) {
                    String platform = LIVE_TYPE_MAPPING.get(entry.getKey());
                    String fieldName = PLATFORM_ID_FIELD_MAPPING.get(platform);
                    if (fieldName != null) {
                        config.addProperty(fieldName, entry.getValue());
                    }
                }
            }
        }
        
        // 保存配置
        saveConfig(configFile, config);
    }
}
```

---

## 📦 第六章：McPatchClient 集成

### 6.1 McPatchClient 简介

McPatchClient 是一个独立的文件更新器模块，使用 Kotlin 编写，负责在游戏启动前检查和下载游戏文件更新。

### 6.2 项目结构

```
McPatchClient/
├── src/main/kotlin/mcpatch/
│   ├── McPatchClient.kt          # 主入口类
│   ├── WorkThread.kt             # 工作线程
│   ├── WorkThreadWithCallback.kt # 带回调的工作线程
│   ├── callback/
│   │   └── ProgressCallback.kt   # 进度回调接口
│   ├── config/
│   │   └── HardcodedConfig.kt    # ★★ 硬编码配置（重要）
│   ├── core/                     # 核心更新逻辑
│   ├── data/                     # 数据模型
│   ├── server/                   # 服务器通信
│   └── gui/                      # GUI组件
└── build.gradle.kts
```

### 6.3 HardcodedConfig 核心配置详解

`HardcodedConfig.kt` 是 McPatchClient 的核心配置文件，包含了更新服务器地址、下载行为、网络参数等关键配置。

#### 6.3.1 服务器地址配置

```kotlin
// 文件：HardcodedConfig.kt

object HardcodedConfig {

    // 海外API切换相关常量
    private const val KOKUGAI_FILENAME = "kokugai"
    private const val KOKUGAI_CONTENT = "gaikoku"

    // 服务器地址常量
    private const val DOMESTIC_SERVER_HOST = "http://api.pixellive.cn"   // 国内服务器
    private const val OVERSEAS_SERVER_HOST = "http://tkapi.pixellive.cn" // 海外服务器

    /**
     * 生成服务器地址列表
     * 根据 .hmcl/kokugai 文件决定使用国内还是海外服务器
     */
    private fun generateServerUrls(): List<String> {
        val useOverseas = shouldUseOverseasApi()
        val serverHost = if (useOverseas) {
            OVERSEAS_SERVER_HOST
        } else {
            DOMESTIC_SERVER_HOST
        }
        // 使用固定端口8080
        return listOf("$serverHost:8080")
    }
}
```

**服务器切换逻辑**：
- 检查 `.hmcl/kokugai` 文件是否存在
- 如果文件内容为 `gaikoku`，则使用海外服务器
- 否则使用国内服务器

#### 6.3.2 完整配置项说明

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| **服务器配置** | | |
| `server` | 动态生成 | 更新服务器地址列表，格式：`["http://host:8080"]` |
| **界面配置** | | |
| `disable-theme` | `false` | 是否禁用UI主题 |
| `show-finish-message` | `true` | 是否显示更新完成消息 |
| `show-changelogs-message` | `true` | 是否显示更新记录 |
| `changelogs-auto-close` | `0` | 更新记录自动关闭秒数，0表示不自动关闭 |
| `quiet-mode` | `false` | 静默模式，隐藏更新窗口 |
| **网络配置** | | |
| `http-connect-timeout` | `3000` | HTTP连接超时（毫秒） |
| `http-response-timeout` | `5000` | HTTP响应超时（毫秒） |
| `retry-times` | `5` | 下载失败重试次数 |
| **多线程下载** | | |
| `concurrent-threads` | `4` | 并发下载线程数 |
| `concurrent-block-size` | `4194304` | 分块大小（4MB） |
| **版本管理** | | |
| `version-file` | `.minecraft/config/mc-patch-version.txt` | 本地版本文件路径 |
| `server-versions-file-name` | `versions.txt` | 服务器版本文件名 |
| `auto-restart-version` | `true` | 是否自动重启版本检查 |
| **其他配置** | | |
| `no-throwing` | `false` | 错误时是否继续运行 |
| `http-headers` | `{}` | 自定义HTTP请求头 |
| `ignore-https-certificate` | `false` | 是否忽略HTTPS证书验证 |
| `http-fallback-file-size` | `1073741824` | 回退文件大小限制（1GB） |
| `base-path` | `""` | 基础路径前缀 |

#### 6.3.3 配置模式

HardcodedConfig 提供了多种预设配置模式：

```kotlin
// 1. 标准配置
fun getConfig(): Map<String, Any>

// 2. 生产环境配置 - 静默模式，减少干扰
fun getProductionConfig(): Map<String, Any> {
    // quiet-mode = true
    // show-finish-message = false
    // changelogs-auto-close = 5
}

// 3. 开发环境配置 - 便于调试
fun getDevConfig(): Map<String, Any> {
    // quiet-mode = false
    // http-connect-timeout = 10000
    // retry-times = 3
}

// 4. 高性能配置 - 适用于高速网络
fun getHighPerformanceConfig(): Map<String, Any> {
    // concurrent-threads = 8
    // concurrent-block-size = 8MB
}

// 5. 低速网络配置 - 适用于网络差的环境
fun getLowSpeedNetworkConfig(): Map<String, Any> {
    // concurrent-threads = 2
    // concurrent-block-size = 1MB
    // retry-times = 8
}

// 6. 自定义服务器配置
fun getConfigWithCustomServers(serverUrls: List<String>): Map<String, Any>
```

#### 6.3.4 修改服务器地址

如需修改更新服务器地址，编辑以下常量：

```kotlin
// 文件：HardcodedConfig.kt

// 国内服务器地址
private const val DOMESTIC_SERVER_HOST = "http://api.pixellive.cn"

// 海外服务器地址
private const val OVERSEAS_SERVER_HOST = "http://tkapi.pixellive.cn"
```

**注意**：服务器地址后会自动添加端口 `:8080`

#### 6.3.5 版本文件说明

```
更新流程：
1. McPatchClient 读取本地版本文件
   路径：.minecraft/config/mc-patch-version.txt
   
2. 向服务器请求版本信息
   URL: {server}/versions.txt
   
3. 比较版本，确定需要更新的文件
   
4. 下载更新文件并应用
   
5. 更新本地版本文件
```

#### 6.3.6 网络参数调优建议

| 网络环境 | concurrent-threads | concurrent-block-size | http-connect-timeout | retry-times |
|----------|-------------------|----------------------|---------------------|-------------|
| 高速网络 | 8 | 8MB | 1500ms | 3 |
| 标准网络 | 4 | 4MB | 3000ms | 5 |
| 低速网络 | 2 | 1MB | 8000ms | 8 |
| 不稳定网络 | 2 | 2MB | 10000ms | 10 |

### 6.4 与 HMCL 的集成方式

#### 6.4.1 ProgressCallback 接口

```kotlin
// 文件：ProgressCallback.kt

interface ProgressCallback {
    
    /** 更新窗口标题 */
    fun updateTitle(title: String)
    
    /** 更新状态标签 */
    fun updateLabel(label: String)
    
    /** 更新进度条 (0-1000) */
    fun updateProgress(progressText: String, progressValue: Int)
    
    /** 检查是否应该中断 */
    fun shouldInterrupt(): Boolean
    
    /** 显示完成消息 */
    fun showCompletionMessage(hasUpdates: Boolean)
    
    /** 显示更新记录 */
    fun showChangeLogs(title: String, content: String, autoCloseSeconds: Int)
}
```

#### 6.4.2 HMCL 中的回调实现

```java
// 文件：LauncherHelper.java
// 内部类：McPatchProgressCallback

private class McPatchProgressCallback implements ProgressCallback {
    
    private final McPatchTask task;

    public McPatchProgressCallback(McPatchTask task) {
        this.task = task;
    }

    @Override
    public void updateTitle(String title) {
        Platform.runLater(() -> {
            if (!task.isCancelled()) {
                LOG.info("McPatch标题: " + title);
                task.updateMessage(title);
            }
        });
    }

    @Override
    public void updateLabel(String label) {
        Platform.runLater(() -> {
            if (!task.isCancelled()) {
                LOG.info("McPatch状态: " + label);
                task.updateMessage(label);
            }
        });
    }

    @Override
    public void updateProgress(String text, int value) {
        Platform.runLater(() -> {
            if (!task.isCancelled()) {
                double progress = value / 1000.0;
                task.updateMessage(text);
                task.updateProgressImmediately(progress);
            }
        });
    }

    @Override
    public boolean shouldInterrupt() {
        return task.isCancelled() || Thread.currentThread().isInterrupted();
    }

    @Override
    public void showCompletionMessage(boolean hasUpdates) {
        String message = hasUpdates ? 
            i18n("mcpatch.completed") : i18n("mcpatch.up_to_date");
        Platform.runLater(() -> task.updateMessage(message));
    }
}
```

#### 6.4.3 调用 McPatchClient

```java
// 文件：LauncherHelper.java

private Task<Void> createMcPatchTask() {
    return new McPatchTask();
}

private class McPatchTask extends Task<Void> {
    
    @Override
    public void execute() throws Exception {
        LOG.info("开始文件更新检查");
        updateMessage(i18n("mcpatch.connecting"));
        
        McPatchProgressCallback callback = new McPatchProgressCallback(this);
        
        // 调用 McPatchClient
        CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
            // modloaderWithProgress: 以模组加载器模式运行，带进度回调
            // 参数1: graphicsMode - 是否显示GUI (这里传true显示进度)
            // 参数2: hasStandaloneProgress - 是否独立进程 (这里传true)
            // 参数3: progressCallback - 进度回调
            return McPatchClient.modloaderWithProgress(true, true, callback);
        });
        
        boolean hasUpdates = future.get();
        LOG.info("文件更新" + (hasUpdates ? "完成" : "无需更新"));
    }
}
```

### 6.5 启动进度显示

McPatch任务在启动对话框中显示为第一个阶段：

```
┌──────────────────────────────────────┐
│          正在启动游戏...              │
├──────────────────────────────────────┤
│ ▶ 文件更新检查                        │  ← McPatch阶段
│   检查Java环境                        │
│   处理依赖                            │
│   登录验证                            │
│   等待启动                            │
├──────────────────────────────────────┤
│ [████████████░░░░░░░░] 60%           │
│ 正在下载: mod_example.jar (2.5MB)    │
└──────────────────────────────────────┘
```

---

## 🎨 第七章：UI 调整与开发指南

### 7.1 已进行的 UI 调整

#### 7.1.1 主页面调整

1. **启动器标题**: 从 "HMCL" 改为 "PIXCL"
2. **完整标题**: 从 "Hello Minecraft! Launcher" 改为 "Pixel Launcher"
3. **左侧账户输入区域**: 新增直播平台选择、房间号输入、卡密输入等控件

#### 7.1.2 账户页面调整

1. **账户创建入口**: 
   - 离线模式（直播验证）
   - 卡密模式
2. **移除的功能**:
   - Microsoft 登录
   - 第三方认证服务器

#### 7.1.3 设置页面调整

1. **关于页面**: 新增 McPatchClient 作者和协议信息
2. **更新设置**: 隐藏部分不需要的更新选项

### 7.2 UI 组件开发示例

#### 7.2.1 创建新的弹窗对话框

```java
// 使用 JFXDialogLayout 创建自定义对话框

public class CustomDialog extends JFXDialogLayout {
    
    public CustomDialog(String title, String content, Runnable onConfirm) {
        // 设置标题
        setHeading(new Label(title));
        
        // 设置内容
        VBox contentBox = new VBox(10);
        contentBox.getChildren().add(new Label(content));
        setBody(contentBox);
        
        // 创建按钮
        JFXButton confirmBtn = new JFXButton(i18n("button.ok"));
        confirmBtn.getStyleClass().add("dialog-accept");
        confirmBtn.setOnAction(e -> {
            onConfirm.run();
            fireEvent(new DialogCloseEvent());
        });
        
        JFXButton cancelBtn = new JFXButton(i18n("button.cancel"));
        cancelBtn.getStyleClass().add("dialog-cancel");
        cancelBtn.setOnAction(e -> fireEvent(new DialogCloseEvent()));
        
        setActions(confirmBtn, cancelBtn);
        
        // ESC键关闭
        FXUtils.onEscPressed(this, cancelBtn::fire);
    }
}

// 使用方式
Controllers.dialog(new CustomDialog("提示", "操作成功！", () -> {
    LOG.info("用户确认");
}));
```

#### 7.2.2 创建消息提示对话框

```java
// 简单消息对话框
Controllers.dialog(
    i18n("message.content"),           // 消息内容
    i18n("message.title"),             // 标题
    MessageDialogPane.MessageType.INFO // 类型: INFO/WARNING/ERROR/SUCCESS
);

// 带确认回调的对话框
Controllers.confirm(
    i18n("confirm.message"),
    i18n("confirm.title"),
    () -> {
        // 用户点击确认后执行
        LOG.info("用户确认操作");
    },
    () -> {
        // 用户点击取消后执行（可选）
        LOG.info("用户取消操作");
    }
);
```

#### 7.2.3 创建 Toast 通知

```java
// 显示简单的 Toast 通知
Controllers.showToast(i18n("operation.success"));

// Toast 会自动消失，适合非关键性提示
```

#### 7.2.4 创建带进度的任务对话框

```java
// 创建任务
Task<?> task = Task.supplyAsync(() -> {
    // 模拟耗时操作
    for (int i = 0; i <= 100; i++) {
        Thread.sleep(50);
        updateProgress(i / 100.0);
        updateMessage("处理中... " + i + "%");
    }
    return "完成";
});

// 显示任务对话框
TaskExecutor executor = task.executor();
Controllers.taskDialog(
    executor,
    i18n("task.title"),
    TaskCancellationAction.NORMAL  // 允许取消
);

// 启动任务
executor.start();
```

#### 7.2.5 创建自定义输入对话框

```java
public class InputDialog extends JFXDialogLayout {
    
    private final JFXTextField inputField;
    private final Consumer<String> onSubmit;
    
    public InputDialog(String title, String prompt, Consumer<String> onSubmit) {
        this.onSubmit = onSubmit;
        
        setHeading(new Label(title));
        
        VBox content = new VBox(10);
        content.getChildren().add(new Label(prompt));
        
        inputField = new JFXTextField();
        inputField.setPromptText("请输入...");
        content.getChildren().add(inputField);
        
        setBody(content);
        
        JFXButton submitBtn = new JFXButton(i18n("button.ok"));
        submitBtn.getStyleClass().add("dialog-accept");
        submitBtn.setOnAction(e -> submit());
        
        JFXButton cancelBtn = new JFXButton(i18n("button.cancel"));
        cancelBtn.getStyleClass().add("dialog-cancel");
        cancelBtn.setOnAction(e -> fireEvent(new DialogCloseEvent()));
        
        setActions(submitBtn, cancelBtn);
        
        // 回车提交
        inputField.setOnAction(e -> submit());
    }
    
    private void submit() {
        String value = inputField.getText();
        if (value != null && !value.trim().isEmpty()) {
            onSubmit.accept(value.trim());
            fireEvent(new DialogCloseEvent());
        }
    }
}

// 使用方式
Controllers.dialog(new InputDialog(
    "输入房间号",
    "请输入您的直播间房间号：",
    roomNumber -> {
        LOG.info("用户输入: " + roomNumber);
    }
));
```

#### 7.2.6 创建下拉选择对话框

```java
public class SelectDialog<T> extends JFXDialogLayout {
    
    private final JFXComboBox<T> comboBox;
    
    public SelectDialog(String title, List<T> options, 
                        StringConverter<T> converter, Consumer<T> onSelect) {
        
        setHeading(new Label(title));
        
        comboBox = new JFXComboBox<>();
        comboBox.getItems().addAll(options);
        comboBox.setConverter(converter);
        if (!options.isEmpty()) {
            comboBox.getSelectionModel().select(0);
        }
        
        VBox content = new VBox(10);
        content.getChildren().add(comboBox);
        setBody(content);
        
        JFXButton confirmBtn = new JFXButton(i18n("button.ok"));
        confirmBtn.getStyleClass().add("dialog-accept");
        confirmBtn.setOnAction(e -> {
            T selected = comboBox.getValue();
            if (selected != null) {
                onSelect.accept(selected);
            }
            fireEvent(new DialogCloseEvent());
        });
        
        setActions(confirmBtn);
    }
}

// 使用方式
List<String> platforms = Arrays.asList("抖音", "快手", "BiliBili", "TikTok");
Controllers.dialog(new SelectDialog<>(
    "选择平台",
    platforms,
    FXUtils.stringConverter(s -> s),
    selected -> LOG.info("选择了: " + selected)
));
```

### 7.3 样式类参考

| 样式类 | 用途 |
|--------|------|
| `dialog-accept` | 确认按钮样式 |
| `dialog-cancel` | 取消按钮样式 |
| `jfx-button-border` | 带边框的按钮 |
| `toggle-icon4` | 图标按钮 |
| `card` | 卡片容器 |
| `card-list` | 卡片列表 |
| `navigation-drawer-item` | 导航抽屉项目 |
| `subtitle-label` | 副标题标签 |

---

## 📚 第八章：补充说明

### 8.1 国际化 (i18n)

项目使用 `i18n()` 方法进行国际化，语言文件位于：
- `HMCL/src/main/resources/assets/lang/`

添加新的翻译键：
```properties
# zh_CN.properties
mcpatch.connecting=正在连接更新服务器...
mcpatch.completed=文件更新完成
mcpatch.up_to_date=所有文件已是最新
mcpatch.cancelled=文件更新已取消
mcpatch.failed=文件更新失败
```

### 8.2 日志记录

使用项目统一的日志工具：
```java
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

LOG.info("普通信息");
LOG.warning("警告信息");
LOG.warning("带异常的警告", exception);
LOG.error("错误信息");
```

### 8.3 异步任务最佳实践

```java
// 推荐方式：使用项目的Task系统
Task.supplyAsync(Schedulers.io(), () -> {
    // IO操作
    return result;
}).whenComplete(Schedulers.javafx(), (result, exception) -> {
    // UI更新
    if (exception == null) {
        // 成功处理
    } else {
        // 错误处理
    }
}).start();

// 不推荐：直接使用Platform.runLater
// 可能导致UI线程阻塞或竞态条件
```

### 8.4 配置文件位置

| 文件 | 位置 | 说明 |
|------|------|------|
| hmcl.json | `.hmcl/hmcl.json` | 主配置文件 |
| config.json | `%APPDATA%/hmcl/config.json` | 全局配置 |
| kokugai | `.hmcl/kokugai` | 海外API切换标记 |
| PixelLiveGame.json | `游戏目录/config/` | 直播游戏配置 |

### 8.5 调试技巧

1. **启用详细日志**: 启动参数添加 `-Dhmcl.debug=true`
2. **查看配置**: 检查 `.hmcl/hmcl.json` 文件
3. **网络调试**: 使用 Fiddler 或 Charles 抓包分析API请求
4. **UI调试**: 使用 ScenicView 工具查看JavaFX节点树

---

## 📝 更新日志

| 日期 | 版本 | 更新内容 |
|------|------|----------|
| 2025-12-31 | 1.0 | 初始版本，包含完整的项目结构和开发指南 |

---

## 🔗 相关链接

- HMCL 原项目: https://github.com/huangyuhui/HMCL
- GPLv3 协议: https://www.gnu.org/licenses/gpl-3.0.html
- JavaFX 文档: https://openjfx.io/
- JFoenix 文档: https://github.com/sshahine/JFoenix

---

*本文档由 PIXCL 开发团队维护*
