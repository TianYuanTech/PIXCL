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
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
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
import java.util.List;
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
     * @return AccountInputData 账户输入数据
     * @description: 获取账户输入数据，供MainPage使用
     */
    public static AccountInputData getAccountInputData() {
        if (accountInputControls == null) {
            return null;
        }
        return accountInputControls.getInputData();
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
     * @description: 账户输入控件类，支持自动填充上次使用的账户数据
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
        private final BooleanBinding validBinding;

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

            // 绑定用户名选项到账户列表
            initializeUsernameOptions();

            // 添加用户名选择监听器
            cboUsername.valueProperty().addListener((observable, oldValue, newValue) -> {
                if (!isUpdatingUsernameSelection && newValue != null) {
                    switchToAccountByUsername(newValue);
                }
            });

            // 添加焦点监听器
            cboUsername.focusedProperty().addListener((observable, oldValue, newValue) -> {
                if (!newValue) {
                    String editorText = cboUsername.getEditor().getText();
                    if ((editorText == null || editorText.trim().isEmpty()) && cboUsername.getValue() == null) {
                        Platform.runLater(() -> cboUsername.setPromptText(i18n(USERNAME_PROMPT_KEY)));
                    }
                }
            });

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

            // 先设置登录方式变化监听器
            cboLoginMethod.valueProperty().addListener(new ChangeListener<String>() {
                @Override
                public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
                    updateLoginMethodVisibility(newValue);
                }
            });

            // 创建验证绑定
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

            // 最后绑定到当前选中的账户
            currentAccount.bind(Accounts.selectedAccountProperty());

            // 延迟初始化默认状态，避免与账户绑定冲突
            Platform.runLater(() -> {
                Account currentSelectedAccount = Accounts.getSelectedAccount();
                if (currentSelectedAccount == null) {
                    // 只有在没有选中账户时才设置默认值
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
         * @description: 设置默认状态
         */
        private void setDefaultState() {
            cboLoginMethod.setValue(i18n(LIVE_VERIFICATION_KEY));
            // updateLoginMethodVisibility 会通过监听器自动调用
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
                // 如果没有账户，清空所有字段
                clearAllFields();
                return;
            }

            // 填充用户名到组合框，避免触发账户切换
            String accountUsername = account.getUsername();
            if (accountUsername != null && !accountUsername.trim().isEmpty()) {

                // 使用Platform.runLater确保UI更新在正确的线程中执行
                Platform.runLater(() -> {
                    isUpdatingUsernameSelection = true;

                    // 检查是否在下拉选项中
                    if (cboUsername.getItems().contains(accountUsername)) {
                        // 先清空编辑器文本，再设置选择值
                        cboUsername.getEditor().clear();
                        cboUsername.setValue(accountUsername);
                    } else {
                        // 如果不在选项中，先清空选择值，再设置编辑器文本
                        cboUsername.setValue(null);
                        cboUsername.getEditor().setText(accountUsername);

                        // 手动触发编辑器的文本更新，确保promptText消失
                        cboUsername.getEditor().positionCaret(accountUsername.length());
                    }

                    // 确保组件重新布局以正确显示内容
                    cboUsername.requestLayout();

                    isUpdatingUsernameSelection = false;
                });
            }

            // 如果是离线账户，尝试提取额外信息
            if (account instanceof OfflineAccount) {
                OfflineAccount offlineAccount = (OfflineAccount) account;

                String accountMode = offlineAccount.getAccountMode();
                String liveType = offlineAccount.getLiveType();
                String liveRoom = offlineAccount.getLiveRoom();
                String cardKey = offlineAccount.getCardKey();

                if ("LIVE".equals(accountMode) && liveType != null && liveRoom != null) {
                    // 设置为直播间验证模式
                    cboLoginMethod.setValue(i18n(LIVE_VERIFICATION_KEY));
                    cboPlatform.setValue(liveType);
                    txtRoomNumber.setText(liveRoom);

                    LOG.info("自动填充直播间验证数据: " + liveType + " - " + liveRoom);

                } else if ("CARD_KEY".equals(accountMode) && cardKey != null) {
                    // 设置为卡密验证模式
                    cboLoginMethod.setValue(i18n(CARD_KEY_VERIFICATION_KEY));
                    txtCardKey.setText(cardKey);

                    LOG.info("自动填充卡密验证数据");

                } else {
                    // 未知模式或数据不完整，清空相关字段
                    cboLoginMethod.setValue(i18n(LIVE_VERIFICATION_KEY));
                    clearModeSpecificFields();

                    LOG.info("账户模式未知或数据不完整，清空模式相关字段");
                }
            } else {
                // 非离线账户，只填充用户名，其他字段清空
                cboLoginMethod.setValue(i18n(LIVE_VERIFICATION_KEY));
                clearModeSpecificFields();

                LOG.info("非离线账户，仅填充用户名: " + account.getUsername());
            }
        }

        /**
         * @description: 清空所有输入字段
         */
        private void clearAllFields() {
            // 使用Platform.runLater确保UI更新正确执行
            Platform.runLater(() -> {
                isUpdatingUsernameSelection = true;
                cboUsername.setValue(null);
                cboUsername.getEditor().clear();
                // 重新设置promptText确保正确显示
                cboUsername.setPromptText(i18n(USERNAME_PROMPT_KEY));
                isUpdatingUsernameSelection = false;
            });

            cboLoginMethod.setValue(i18n(LIVE_VERIFICATION_KEY));
            clearModeSpecificFields();
        }

        /**
         * @description: 清空模式相关的输入字段
         */
        private void clearModeSpecificFields() {
            cboPlatform.setValue("抖音");
            txtRoomNumber.clear();
            txtCardKey.clear();
        }

        /**
         * @description: 更新登录方式的可见性
         * @param loginMethod 登录方式
         */
        private void updateLoginMethodVisibility(String loginMethod) {
            if (i18n(LIVE_VERIFICATION_KEY).equals(loginMethod)) {
                liveContainer.setVisible(true);
                liveContainer.setManaged(true);
                cardKeyContainer.setVisible(false);
                cardKeyContainer.setManaged(false);
            } else if (i18n(CARD_KEY_VERIFICATION_KEY).equals(loginMethod)) {
                liveContainer.setVisible(false);
                liveContainer.setManaged(false);
                cardKeyContainer.setVisible(true);
                cardKeyContainer.setManaged(true);
            } else {
                liveContainer.setVisible(false);
                liveContainer.setManaged(false);
                cardKeyContainer.setVisible(false);
                cardKeyContainer.setManaged(false);
            }
        }

        /**
         * @description: 获取输入数据
         * @return AccountInputData 输入数据
         */
        public AccountInputData getInputData() {
            return new AccountInputData(
                    getUsernameValue(), // 使用新的获取用户名方法
                    cboLoginMethod.getValue(),
                    cboPlatform.getValue(),
                    txtRoomNumber.getText(),
                    txtCardKey.getText()
            );
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