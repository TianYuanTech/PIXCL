// 文件：RootPage.java (修改后的完整版本)
// 路径：HMCL/src/main/java/org/jackhuang/hmcl/ui/main/RootPage.java
package org.jackhuang.hmcl.ui.main;

import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXTextField;
import javafx.application.Platform;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.offline.OfflineAccount;
import org.jackhuang.hmcl.event.EventBus;
import org.jackhuang.hmcl.event.RefreshedVersionsEvent;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.ModpackHelper;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Profiles;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.account.PlayerAvatarView;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.RequiredValidator;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.ui.download.ModpackInstallWizardProvider;
import org.jackhuang.hmcl.ui.nbt.NBTEditorPage;
import org.jackhuang.hmcl.ui.nbt.NBTFileType;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.TaskCancellationAction;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.versioning.VersionNumber;

import java.io.File;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.ui.FXUtils.setValidateWhileTextChanged;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @description: 主页面根页面，包含账户输入界面的左侧栏
 */
public class RootPage extends DecoratorAnimatedPage implements DecoratorPage {

    /**
     * @description: 主页面实例
     */
    private MainPage mainPage = null;

    /**
     * @description: 账户输入控件实例
     */
    private static AccountInputControls accountInputControls;

    /**
     * @description: 构造函数，初始化页面和事件监听
     */
    public RootPage() {
        EventBus.EVENT_BUS.channel(RefreshedVersionsEvent.class)
                .register(event -> onRefreshedVersions((HMCLGameRepository) event.getSource()));

        Profile profile = Profiles.getSelectedProfile();
        if (profile != null && profile.getRepository().isLoaded())
            onRefreshedVersions(Profiles.selectedProfileProperty().get().getRepository());

        getStyleClass().remove("gray-background");
        getLeft().getStyleClass().add("gray-background");
    }


    /**
     * @description: 获取账户输入数据，供MainPage使用
     * @return AccountInputData 账户输入数据
     */
    public static AccountInputData getAccountInputData() {
        if (accountInputControls == null) {
            return null;
        }
        return accountInputControls.getInputData();
    }

    /**
     * @description: 获取所有平台的房间号数据，供MainPage使用
     * @return Map<String, String> 所有平台房间号映射，如果控件不存在则返回空Map
     */
    public static Map<String, String> getAllPlatformRooms() {
        if (accountInputControls == null) {
            return new HashMap<>();
        }
        return accountInputControls.getAllPlatformRooms();
    }

    /**
     * @description: 获取当前缓存的卡密数据，供MainPage使用
     * @return String 缓存的卡密，如果没有缓存则返回null
     */
    public static String getCachedCardKey() {
        if (accountInputControls == null) {
            return null;
        }
        return accountInputControls.getCachedCardKey();
    }

    /**
     * @return ReadOnlyObjectProperty<State> 页面状态
     * @description: 获取页面状态属性
     */
    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return getMainPage().stateProperty();
    }

    /**
     * @return Skin 页面皮肤
     * @description: 创建默认皮肤
     */
    @Override
    protected Skin createDefaultSkin() {
        return new Skin(this);
    }

    /**
     * @return MainPage 主页面
     * @description: 获取主页面实例
     */
    public MainPage getMainPage() {
        if (mainPage == null) {
            MainPage mainPage = new MainPage();
            FXUtils.applyDragListener(mainPage,
                    file -> ModpackHelper.isFileModpackByExtension(file) || NBTFileType.isNBTFileByExtension(file.toPath()),
                    modpacks -> {
                        File file = modpacks.get(0);
                        if (ModpackHelper.isFileModpackByExtension(file)) {
                            Controllers.getDecorator().startWizard(
                                    new ModpackInstallWizardProvider(Profiles.getSelectedProfile(), file),
                                    i18n("install.modpack"));
                        } else if (NBTFileType.isNBTFileByExtension(file.toPath())) {
                            try {
                                Controllers.navigate(new NBTEditorPage(file.toPath()));
                            } catch (Throwable e) {
                                LOG.warning("Fail to open nbt file", e);
                                Controllers.dialog(i18n("nbt.open.failed") + "\n\n" + StringUtils.getStackTrace(e),
                                        i18n("message.error"), MessageDialogPane.MessageType.ERROR);
                            }
                        }
                    });

            FXUtils.onChangeAndOperate(Profiles.selectedVersionProperty(), mainPage::setCurrentGame);

            Profiles.registerVersionsListener(profile -> {
                HMCLGameRepository repository = profile.getRepository();
                List<Version> children = repository.getVersions().parallelStream()
                        .filter(version -> !version.isHidden())
                        .sorted(Comparator
                                .comparing((Version version) -> Lang.requireNonNullElse(version.getReleaseTime(), Instant.EPOCH))
                                .thenComparing(version -> VersionNumber.asVersion(repository.getGameVersion(version).orElse(version.getId()))))
                        .collect(Collectors.toList());
                runInFX(() -> {
                    if (profile == Profiles.getSelectedProfile())
                        mainPage.initVersions(profile, children);
                });
            });
            this.mainPage = mainPage;
        }
        return mainPage;
    }

    /**
     * @param repository 游戏仓库
     * @description: 当版本刷新时的处理方法
     */
    private void onRefreshedVersions(HMCLGameRepository repository) {
        runInFX(() -> {
            if (!checkedModpack) {
                checkedModpack = true;

                if (repository.getVersionCount() == 0) {
                    File modpackFile = new File("modpack.zip").getAbsoluteFile();
                    if (modpackFile.exists()) {
                        Task.supplyAsync(() -> CompressingUtils.findSuitableEncoding(modpackFile.toPath()))
                                .thenApplyAsync(
                                        encoding -> ModpackHelper.readModpackManifest(modpackFile.toPath(), encoding))
                                .thenApplyAsync(modpack -> ModpackHelper
                                        .getInstallTask(repository.getProfile(), modpackFile, modpack.getName(),
                                                modpack)
                                        .executor())
                                .thenAcceptAsync(Schedulers.javafx(), executor -> {
                                    Controllers.taskDialog(executor, i18n("modpack.installing"), TaskCancellationAction.NO_CANCEL);
                                    executor.start();
                                }).start();
                    }
                }
            }
        });
    }

    /**
     * @description: 皮肤实现类，包含玩家头像和账户输入界面的左侧栏
     */
    private static class Skin extends DecoratorAnimatedPageSkin<RootPage> {

        /**
         * @param control 控制器
         * @description: 构造函数，创建包含头像和账户输入界面的左侧栏
         */
        protected Skin(RootPage control) {
            super(control);

            // 创建左侧栏，调整宽度以适应新组件
            VBox leftSidebar = new VBox();
            leftSidebar.getStyleClass().add("advanced-list-box-content");
            leftSidebar.setAlignment(Pos.TOP_CENTER); // 设置VBox内容居中对齐
            leftSidebar.setSpacing(10); // 增加组件之间的间距
            FXUtils.setLimitWidth(leftSidebar, 350);

            // 创建玩家头像组件
            PlayerAvatarView playerAvatarView = new PlayerAvatarView();
            playerAvatarView.accountProperty().bind(Accounts.selectedAccountProperty());

            // 为头像添加点击事件，点击后跳转到账户列表页面
            playerAvatarView.setOnMouseClicked(e -> Controllers.navigate(Controllers.getAccountListPage()));

            // 设置头像的样式，添加一些视觉效果
            playerAvatarView.getStyleClass().add("clickable-avatar");

            // 为头像组件设置外边距
            VBox.setMargin(playerAvatarView, new Insets(60, 0, 10, 0)); // 上边距20，下边距10

            // 创建账户输入控件
            accountInputControls = new AccountInputControls();

            // 为账户输入控件设置顶部外边距，使其整体下移
            VBox.setMargin(accountInputControls, new Insets(20, 0, 0, 0)); // 增加40像素的顶部边距

            // 添加组件到左侧栏，确保头像在顶部居中显示
            leftSidebar.getChildren().addAll(
                    playerAvatarView,
                    accountInputControls
            );

            ScrollPane scrollPane = new ScrollPane(leftSidebar);
            VBox.setVgrow(scrollPane, Priority.ALWAYS);

            // 设置左侧栏
            setLeft(scrollPane);
            setCenter(getSkinnable().getMainPage());
        }
    }

    /**
     * @description: 账户输入数据类，用于传递输入信息
     */
    public static class AccountInputData {
        private final String username;
        private final String loginMethod;
        private final String platform;
        private final String roomNumber;
        private final String cardKey;

        public AccountInputData(String username, String loginMethod, String platform, String roomNumber, String cardKey) {
            this.username = username;
            this.loginMethod = loginMethod;
            this.platform = platform;
            this.roomNumber = roomNumber;
            this.cardKey = cardKey;
        }

        public String getUsername() {
            return username;
        }

        public String getLoginMethod() {
            return loginMethod;
        }

        public String getPlatform() {
            return platform;
        }

        public String getRoomNumber() {
            return roomNumber;
        }

        public String getCardKey() {
            return cardKey;
        }
    }

    /**
     * @description: 检查是否已加载模组包的标志
     */
    private boolean checkedModpack = false;

    /**
     * @description: 账户输入控件类，支持自动填充上次使用的账户数据和多平台房间号切换
     */
    /**
     * @description: 账户输入控件类，支持自动填充上次使用的账户数据和多平台房间号切换
     */
    private static class AccountInputControls extends VBox {

        /**
         * @description: 用户名输入组合框（可编辑的下拉框）
         */
        private final JFXComboBox<String> cboUsername;

        /**
         * @description: 登录方式选择器
         */
        private final JFXComboBox<String> cboLoginMethod;

        /**
         * @description: 直播平台选择器
         */
        private final JFXComboBox<String> cboPlatform;

        /**
         * @description: 直播间号输入框
         */
        private final JFXTextField txtRoomNumber;

        /**
         * @description: 卡密输入框
         */
        private final JFXTextField txtCardKey;

        /**
         * @description: 直播间验证容器
         */
        private final HBox liveContainer;

        /**
         * @description: 卡密验证容器
         */
        private final VBox cardKeyContainer;

        /**
         * @description: 验证状态绑定
         */
        private BooleanBinding validBinding;

        /**
         * @description: 当前账户属性，用于监听账户变化
         */
        private final ObjectProperty<Account> currentAccount = new SimpleObjectProperty<Account>() {
            @Override
            protected void invalidated() {
                Account account = get();
                updateInputFieldsFromAccount(account);
            }
        };

        /**
         * @description: 用户名选择监听器，防止递归更新
         */
        private boolean isUpdatingUsernameSelection = false;

        /**
         * @description: 平台选择监听器，防止递归更新
         */
        private boolean isUpdatingPlatformSelection = false;

        /**
         * @description: 当前账户的多平台房间号，仅用于界面状态管理
         */
        private Map<String, String> displayRooms = new HashMap<>();

        /**
         * @description: 当前账户的卡密缓存，用于登录方式切换时保持数据
         */
        private String currentCardKeyCache = "";

        /**
         * @description: 当前缓存关联的账户用户名，用于检测账户切换
         */
        private String cacheAccountUsername = null;

        /**
         * @description: 翻译键常量，便于维护和管理
         */
        private static final String USERNAME_PROMPT_KEY = "account.input.username.prompt";
        private static final String LOGIN_METHOD_KEY = "account.input.login.method";
        private static final String LIVE_VERIFICATION_KEY = "account.input.live.verification";
        private static final String CARD_KEY_VERIFICATION_KEY = "account.input.card.key.verification";
        private static final String PLATFORM_KEY = "account.input.platform";
        private static final String ROOM_NUMBER_KEY = "account.input.room.number";
        private static final String CARD_KEY_INPUT_KEY = "account.input.card.key";

        /**
         * @description: 构造函数，创建账户输入控件并设置默认值
         */
        public AccountInputControls() {
            setSpacing(25);
            setPadding(new Insets(35, 20, 15, 20));

            // 用户名输入组合框（可编辑）
            cboUsername = new JFXComboBox<>();
            cboUsername.setEditable(true);
            cboUsername.setPromptText(i18n(USERNAME_PROMPT_KEY));
            cboUsername.setPrefWidth(310);
            cboUsername.setMaxWidth(310);

            // 登录方式选择器
            cboLoginMethod = new JFXComboBox<>();
            cboLoginMethod.getItems().addAll(i18n(LIVE_VERIFICATION_KEY), i18n(CARD_KEY_VERIFICATION_KEY));
            cboLoginMethod.setPromptText(i18n(LOGIN_METHOD_KEY));
            cboLoginMethod.setPrefWidth(310);
            cboLoginMethod.setMaxWidth(310);

            // 直播平台选择器
            cboPlatform = new JFXComboBox<>();
            cboPlatform.getItems().addAll("抖音", "快手", "BiliBili", "Twitch", "TikTok");
            cboPlatform.setPromptText(i18n(PLATFORM_KEY));
            cboPlatform.setPrefWidth(100);

            // 直播间号输入框
            txtRoomNumber = new JFXTextField();
            txtRoomNumber.setPromptText(i18n(ROOM_NUMBER_KEY));
            txtRoomNumber.setValidators(new RequiredValidator());
            txtRoomNumber.setPrefWidth(205);
            setValidateWhileTextChanged(txtRoomNumber, true);
            HBox.setHgrow(txtRoomNumber, Priority.ALWAYS);

            // 直播间验证容器
            liveContainer = new HBox(5);
            liveContainer.setAlignment(Pos.CENTER_LEFT);
            liveContainer.getChildren().addAll(cboPlatform, txtRoomNumber);

            // 卡密输入框
            txtCardKey = new JFXTextField();
            txtCardKey.setPromptText(i18n(CARD_KEY_INPUT_KEY));
            txtCardKey.setValidators(new RequiredValidator());
            txtCardKey.setPrefWidth(310);
            setValidateWhileTextChanged(txtCardKey, true);

            // 卡密验证容器
            cardKeyContainer = new VBox();
            cardKeyContainer.getChildren().add(txtCardKey);

            // 初始化用户名选项
            initializeUsernameOptions();

            // 设置事件监听器
            setupEventListeners();

            // 设置验证绑定
            setupValidation();

            // 绑定到当前选中的账户
            currentAccount.bind(Accounts.selectedAccountProperty());

            // 延迟初始化默认状态，避免与账户绑定冲突
            Platform.runLater(() -> {
                Account currentSelectedAccount = Accounts.getSelectedAccount();
                if (currentSelectedAccount == null) {
                    setDefaultState();
                }
            });

            // 添加所有组件
            getChildren().addAll(
                    cboUsername,
                    cboLoginMethod,
                    liveContainer,
                    cardKeyContainer
            );
        }

        /**
         * @description: 设置事件监听器
         */
        private void setupEventListeners() {
            // 用户名选择监听器
            cboUsername.valueProperty().addListener((observable, oldValue, newValue) -> {
                if (!isUpdatingUsernameSelection && newValue != null) {
                    switchToAccountByUsername(newValue);
                }
            });

            // 用户名焦点监听器
            cboUsername.focusedProperty().addListener((observable, oldValue, newValue) -> {
                if (!newValue) {
                    String editorText = cboUsername.getEditor().getText();
                    if ((editorText == null || editorText.trim().isEmpty()) && cboUsername.getValue() == null) {
                        Platform.runLater(() -> cboUsername.setPromptText(i18n(USERNAME_PROMPT_KEY)));
                    }
                }
            });

            // 登录方式变化监听器
            cboLoginMethod.valueProperty().addListener((observable, oldValue, newValue) -> {
                updateLoginMethodVisibility(newValue);
            });

            // 平台选择变化监听器
            cboPlatform.valueProperty().addListener((observable, oldValue, newValue) -> {
                if (!isUpdatingPlatformSelection && newValue != null) {
                    onPlatformChanged(oldValue, newValue);
                }
            });

            // 房间号输入变化监听器
            txtRoomNumber.textProperty().addListener((observable, oldValue, newValue) -> {
                String currentPlatform = cboPlatform.getValue();
                if (currentPlatform != null && newValue != null) {
                    displayRooms.put(currentPlatform, newValue);
                }
            });

            txtCardKey.textProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue != null) {
                    currentCardKeyCache = newValue;
                }
            });
        }

        /**
         * @description: 设置验证绑定
         */
        private void setupValidation() {
            validBinding = new BooleanBinding() {
                {
                    bind(cboUsername.valueProperty());
                    bind(cboUsername.getEditor().textProperty());
                    bind(cboLoginMethod.valueProperty());
                    bind(cboPlatform.valueProperty());
                    bind(txtRoomNumber.textProperty());
                    bind(txtCardKey.textProperty());
                }

                @Override
                protected boolean computeValue() {
                    String usernameValue = getUsernameValue();
                    if (usernameValue == null || usernameValue.trim().isEmpty()) {
                        return false;
                    }

                    String loginMethod = cboLoginMethod.getValue();
                    if (loginMethod == null) return false;

                    if (i18n(LIVE_VERIFICATION_KEY).equals(loginMethod)) {
                        return cboPlatform.getValue() != null && txtRoomNumber.validate();
                    } else if (i18n(CARD_KEY_VERIFICATION_KEY).equals(loginMethod)) {
                        return txtCardKey.validate();
                    }

                    return false;
                }
            };
        }

        /**
         * @description: 处理平台切换事件
         * @param oldPlatform 原平台
         * @param newPlatform 新平台
         */
        private void onPlatformChanged(String oldPlatform, String newPlatform) {
            if (oldPlatform != null && newPlatform != null && !oldPlatform.equals(newPlatform)) {
                // 保存当前输入的房间号到原平台
                String currentRoomNumber = txtRoomNumber.getText();
                if (currentRoomNumber != null && !currentRoomNumber.trim().isEmpty()) {
                    displayRooms.put(oldPlatform, currentRoomNumber.trim());
                }

                // 加载新平台的房间号
                String newRoomNumber = displayRooms.get(newPlatform);
                if (newRoomNumber != null && !newRoomNumber.trim().isEmpty()) {
                    txtRoomNumber.setText(newRoomNumber);
                } else {
                    txtRoomNumber.clear();
                }

                LOG.info("平台切换: " + oldPlatform + " -> " + newPlatform +
                        ", 房间号: " + (newRoomNumber != null ? newRoomNumber : "空"));
            }
        }

        /**
         * @description: 设置默认状态
         */
        private void setDefaultState() {
            cboLoginMethod.setValue(i18n(LIVE_VERIFICATION_KEY));
            cboPlatform.setValue("抖音");
        }

        /**
         * @description: 初始化用户名选项，绑定到账户列表
         */
        private void initializeUsernameOptions() {
            // 获取所有账户的用户名列表
            ObservableList<Account> allAccounts = Accounts.getAccounts();

            // 监听账户列表变化，动态更新用户名选项
            allAccounts.addListener((ListChangeListener<Account>) change -> {
                updateUsernameOptions();
            });

            // 初始更新用户名选项
            updateUsernameOptions();
        }

        /**
         * @description: 更新用户名选项列表
         */
        private void updateUsernameOptions() {
            ObservableList<Account> allAccounts = Accounts.getAccounts();

            // 获取当前选中的值，以便更新后恢复
            String currentValue = cboUsername.getValue();
            String currentEditorText = cboUsername.getEditor().getText();

            // 清空现有选项
            cboUsername.getItems().clear();

            // 添加所有账户的用户名到选项中
            for (Account account : allAccounts) {
                String username = account.getUsername();
                if (username != null && !username.trim().isEmpty()) {
                    if (!cboUsername.getItems().contains(username)) {
                        cboUsername.getItems().add(username);
                    }
                }
            }

            // 恢复之前的选择状态
            if (currentValue != null && cboUsername.getItems().contains(currentValue)) {
                isUpdatingUsernameSelection = true;
                cboUsername.setValue(currentValue);
                isUpdatingUsernameSelection = false;
            } else if (currentEditorText != null && !currentEditorText.trim().isEmpty()) {
                isUpdatingUsernameSelection = true;
                cboUsername.getEditor().setText(currentEditorText);
                isUpdatingUsernameSelection = false;
            }
        }

        /**
         * @description: 根据用户名切换到对应的账户
         * @param username 用户名
         */
        private void switchToAccountByUsername(String username) {
            if (username == null || username.trim().isEmpty()) {
                return;
            }

            // 查找匹配的账户
            ObservableList<Account> allAccounts = Accounts.getAccounts();
            for (Account account : allAccounts) {
                if (username.equals(account.getUsername())) {
                    // 切换到该账户
                    Accounts.setSelectedAccount(account);
                    LOG.info("切换到账户: " + username);
                    break;
                }
            }
        }

        /**
         * @description: 获取用户名值，优先从编辑器获取输入的文本，如果为空则从选择值获取
         * @return String 用户名
         */
        private String getUsernameValue() {
            String editorText = cboUsername.getEditor().getText();
            if (editorText != null && !editorText.trim().isEmpty()) {
                return editorText.trim();
            }

            String selectedValue = cboUsername.getValue();
            return selectedValue != null ? selectedValue.trim() : null;
        }

        /**
         * @description: 根据账户信息更新输入字段
         * @param account 当前账户对象
         */
        private void updateInputFieldsFromAccount(Account account) {
            if (account == null) {
                clearAllFields();
                clearCardKeyCache();
                return;
            }

            String accountUsername = account.getUsername();

            // 检测账户切换，如果是不同账户则清除卡密缓存
            if (cacheAccountUsername == null || !cacheAccountUsername.equals(accountUsername)) {
                clearCardKeyCache();
                cacheAccountUsername = accountUsername;
                LOG.info("检测到账户切换，已清除卡密缓存: " + accountUsername);
            }

            updateUsernameDisplay(accountUsername);

            if (account instanceof OfflineAccount) {
                OfflineAccount offlineAccount = (OfflineAccount) account;
                updateFromOfflineAccount(offlineAccount);
            } else {
                setDefaultModeDisplay();
                LOG.info("非离线账户，仅填充用户名: " + account.getUsername());
            }
        }

        /**
         * @description: 清除卡密缓存
         */
        private void clearCardKeyCache() {
            currentCardKeyCache = "";
            cacheAccountUsername = null;
        }

        /**
         * @description: 更新用户名显示
         * @param accountUsername 账户用户名
         */
        private void updateUsernameDisplay(String accountUsername) {
            if (accountUsername != null && !accountUsername.trim().isEmpty()) {
                Platform.runLater(() -> {
                    isUpdatingUsernameSelection = true;

                    if (cboUsername.getItems().contains(accountUsername)) {
                        cboUsername.getEditor().clear();
                        cboUsername.setValue(accountUsername);
                    } else {
                        cboUsername.setValue(null);
                        cboUsername.getEditor().setText(accountUsername);
                        cboUsername.getEditor().positionCaret(accountUsername.length());
                    }

                    cboUsername.requestLayout();
                    isUpdatingUsernameSelection = false;
                });
            }
        }

        /**
         * @description: 从离线账户更新界面
         * @param offlineAccount 离线账户对象
         */
        private void updateFromOfflineAccount(OfflineAccount offlineAccount) {
            String accountMode = offlineAccount.getAccountMode();
            String liveType = offlineAccount.getLiveType();
            Map<String, String> liveRooms = offlineAccount.getLiveRooms();
            String cardKey = offlineAccount.getCardKey();

            // 更新显示用的房间号数据
            displayRooms.clear();
            if (liveRooms != null && !liveRooms.isEmpty()) {
                displayRooms.putAll(liveRooms);
                LOG.info("加载账户的多平台房间号数据: " + liveRooms.size() + " 个平台");
            }

            if ("LIVE".equals(accountMode) && liveType != null) {
                updateLiveModeDisplay(liveType, liveRooms);
            } else if ("CARD_KEY".equals(accountMode) && cardKey != null) {
                updateCardKeyModeDisplay(cardKey, liveType, liveRooms);
            } else {
                // 账户模式未明确时，根据数据可用性设置默认显示
                if (liveType != null && liveRooms != null && !liveRooms.isEmpty()) {
                    updateLiveModeDisplay(liveType, liveRooms);
                } else {
                    setDefaultModeDisplay();
                }
                LOG.info("账户模式未明确，根据可用数据设置默认显示");
            }
        }

        /**
         * @description: 更新直播模式显示
         * @param liveType 直播类型
         * @param liveRooms 多平台房间号
         */
        private void updateLiveModeDisplay(String liveType, Map<String, String> liveRooms) {
            cboLoginMethod.setValue(i18n(LIVE_VERIFICATION_KEY));

            isUpdatingPlatformSelection = true;
            cboPlatform.setValue(liveType);
            isUpdatingPlatformSelection = false;

            String currentRoomNumber = liveRooms != null ? liveRooms.get(liveType) : null;
            if (currentRoomNumber != null) {
                txtRoomNumber.setText(currentRoomNumber);
            } else {
                txtRoomNumber.clear();
            }

            LOG.info("自动填充直播间验证数据: " + liveType + " - " + currentRoomNumber +
                    ", 总计平台数: " + displayRooms.size());
        }

        /**
         * @description: 更新卡密模式显示
         * @param cardKey 卡密
         * @param liveType 直播类型
         * @param liveRooms 多平台房间号
         */
        private void updateCardKeyModeDisplay(String cardKey, String liveType, Map<String, String> liveRooms) {
            cboLoginMethod.setValue(i18n(CARD_KEY_VERIFICATION_KEY));

            // 根据卡密是否存在决定输入框内容
            if (cardKey != null && !cardKey.trim().isEmpty()) {
                txtCardKey.setText(cardKey.trim());
            } else {
                txtCardKey.clear();
            }

            // 预设平台信息以便切换
            isUpdatingPlatformSelection = true;
            cboPlatform.setValue(liveType != null ? liveType : "抖音");
            isUpdatingPlatformSelection = false;

            String currentPlatform = cboPlatform.getValue();
            if (currentPlatform != null && liveRooms != null && liveRooms.containsKey(currentPlatform)) {
                txtRoomNumber.setText(liveRooms.get(currentPlatform));
            } else {
                txtRoomNumber.clear();
            }

            LOG.info("更新卡密模式显示，卡密状态: " + (cardKey != null && !cardKey.trim().isEmpty() ? "已设置" : "未设置"));
        }

        /**
         * @description: 设置默认模式显示
         */
        private void setDefaultModeDisplay() {
            cboLoginMethod.setValue(i18n(LIVE_VERIFICATION_KEY));

            isUpdatingPlatformSelection = true;
            cboPlatform.setValue("抖音");
            isUpdatingPlatformSelection = false;

            txtRoomNumber.clear();
            txtCardKey.clear();
        }

        /**
         * @description: 清空所有输入字段
         */
        private void clearAllFields() {
            Platform.runLater(() -> {
                isUpdatingUsernameSelection = true;
                cboUsername.setValue(null);
                cboUsername.getEditor().clear();
                cboUsername.setPromptText(i18n(USERNAME_PROMPT_KEY));
                isUpdatingUsernameSelection = false;
            });

            cboLoginMethod.setValue(i18n(LIVE_VERIFICATION_KEY));
            clearModeSpecificFields();
            displayRooms.clear();
            clearCardKeyCache();
        }

        /**
         * @description: 清空模式相关的输入字段
         */
        private void clearModeSpecificFields() {
            isUpdatingPlatformSelection = true;
            cboPlatform.setValue("抖音");
            isUpdatingPlatformSelection = false;

            txtRoomNumber.clear();
            txtCardKey.clear();
            // 注意：这里不清除卡密缓存，因为可能只是界面模式切换
        }

        /**
         * @description: 更新登录方式的可见性和对应数据
         * @param loginMethod 登录方式
         */
        private void updateLoginMethodVisibility(String loginMethod) {
            if (i18n(LIVE_VERIFICATION_KEY).equals(loginMethod)) {
                liveContainer.setVisible(true);
                liveContainer.setManaged(true);
                cardKeyContainer.setVisible(false);
                cardKeyContainer.setManaged(false);

                // 切换到直播间验证时，确保显示当前平台的房间号
                refreshLiveData();

            } else if (i18n(CARD_KEY_VERIFICATION_KEY).equals(loginMethod)) {
                liveContainer.setVisible(false);
                liveContainer.setManaged(false);
                cardKeyContainer.setVisible(true);
                cardKeyContainer.setManaged(true);

                // 切换到卡密验证时，从当前账户读取卡密信息
                refreshCardKeyData();

            } else {
                liveContainer.setVisible(false);
                liveContainer.setManaged(false);
                cardKeyContainer.setVisible(false);
                cardKeyContainer.setManaged(false);
            }
        }

        /**
         * @description: 刷新直播间验证数据
         */
        private void refreshLiveData() {
            String currentPlatform = cboPlatform.getValue();
            if (currentPlatform != null && displayRooms.containsKey(currentPlatform)) {
                String roomNumber = displayRooms.get(currentPlatform);
                if (roomNumber != null && !roomNumber.trim().isEmpty()) {
                    txtRoomNumber.setText(roomNumber);
                }
            }
        }

        /**
         * @description: 刷新卡密验证数据
         */
        private void refreshCardKeyData() {
            Account currentAccount = Accounts.getSelectedAccount();

            // 优先使用缓存中的卡密信息
            if (currentCardKeyCache != null && !currentCardKeyCache.trim().isEmpty()) {
                txtCardKey.setText(currentCardKeyCache.trim());
                LOG.info("从缓存恢复卡密数据");
                return;
            }

            // 缓存为空时从账户读取
            if (currentAccount instanceof OfflineAccount) {
                OfflineAccount offlineAccount = (OfflineAccount) currentAccount;
                String cardKey = offlineAccount.getCardKey();
                if (cardKey != null && !cardKey.trim().isEmpty()) {
                    txtCardKey.setText(cardKey.trim());
                    currentCardKeyCache = cardKey.trim();
                    LOG.info("从当前账户读取卡密数据并更新缓存");
                } else {
                    txtCardKey.clear();
                    LOG.info("当前账户无卡密信息，清空卡密输入框");
                }
            } else {
                txtCardKey.clear();
                LOG.info("非离线账户，清空卡密输入框");
            }
        }

        /**
         * @description: 获取输入数据
         * @return AccountInputData 输入数据
         */
        public AccountInputData getInputData() {
            return new AccountInputData(
                    getUsernameValue(),
                    cboLoginMethod.getValue(),
                    cboPlatform.getValue(),
                    txtRoomNumber.getText(),
                    txtCardKey.getText()
            );
        }

        /**
         * @description: 获取当前账户的所有平台房间号数据
         * @return Map<String, String> 所有平台的房间号映射
         */
        public Map<String, String> getAllPlatformRooms() {
            // 确保当前输入的房间号也被保存
            String currentPlatform = cboPlatform.getValue();
            String currentRoomNumber = txtRoomNumber.getText();
            if (currentPlatform != null && currentRoomNumber != null && !currentRoomNumber.trim().isEmpty()) {
                displayRooms.put(currentPlatform, currentRoomNumber.trim());
            }

            return new HashMap<>(displayRooms);
        }

        /**
         * @description: 获取当前缓存的卡密数据
         * @return String 缓存的卡密，如果为空则返回null
         */
        public String getCachedCardKey() {
            return (currentCardKeyCache != null && !currentCardKeyCache.trim().isEmpty())
                    ? currentCardKeyCache.trim() : null;
        }

        /**
         * @description: 获取验证状态绑定
         * @return BooleanBinding 验证状态
         */
        public BooleanBinding validProperty() {
            return validBinding;
        }
    }
}