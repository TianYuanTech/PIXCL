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
package org.jackhuang.hmcl.ui.account;

import com.jfoenix.controls.*;
import com.jfoenix.validation.base.ValidatorBase;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.NamedArg;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.*;
import javafx.util.Duration;
import org.jackhuang.hmcl.auth.AccountFactory;
import org.jackhuang.hmcl.auth.CharacterSelector;
import org.jackhuang.hmcl.auth.NoSelectedCharacterException;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorServer;
import org.jackhuang.hmcl.auth.offline.OfflineAccountFactory;
import org.jackhuang.hmcl.auth.yggdrasil.GameProfile;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilService;
import org.jackhuang.hmcl.game.OAuthServer;
import org.jackhuang.hmcl.game.TexturesLoader;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.WeakListenerHolder;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.upgrade.IntegrityChecker;
import org.jackhuang.hmcl.util.AuthorizationChecker;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.UUIDTypeAdapter;
import org.jackhuang.hmcl.util.javafx.BindingMapping;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Pattern;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.ui.FXUtils.setValidateWhileTextChanged;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/**
 * @description: 创建账户面板，支持多种账户类型的创建
 */
public class CreateAccountPane extends JFXDialogLayout implements DialogAware {

    /**
     * @description: 用户名验证正则表达式
     */
    private static final Pattern USERNAME_CHECKER_PATTERN = Pattern.compile("^[A-Za-z0-9_]+$");

    /**
     * @description: 账户模式枚举，用于区分不同的账户创建模式
     */
    public enum AccountMode {
        /**
         * @description: 离线模式
         */
        OFFLINE,
        /**
         * @description: 卡密模式
         */
        CARD_KEY
    }

    /**
     * @description: 是否显示方法切换器
     */
    private boolean showMethodSwitcher;

    /**
     * @description: 账户工厂
     */
    private AccountFactory<?> factory;

    /**
     * @description: 账户模式
     */
    private AccountMode accountMode;

    /**
     * @description: 错误消息标签
     */
    private final Label lblErrorMessage;

    /**
     * @description: 确认按钮
     */
    private final JFXButton btnAccept;

    /**
     * @description: 加载动画面板
     */
    private final SpinnerPane spinner;

    /**
     * @description: 主体内容节点
     */
    private final Node body;

    /**
     * @description: 详情面板节点
     */
    private Node detailsPane;

    /**
     * @description: 详情容器
     */
    private final Pane detailsContainer;

    /**
     * @description: 登录状态属性
     */
    private final BooleanProperty logging = new SimpleBooleanProperty();

    /**
     * @description: 设备代码属性
     */
    private final ObjectProperty<OAuthServer.GrantDeviceCodeEvent> deviceCode = new SimpleObjectProperty<>();

    /**
     * @description: 弱引用监听器持有者
     */
    private final WeakListenerHolder holder = new WeakListenerHolder();

    /**
     * @description: 登录任务执行器
     */
    private TaskExecutor loginTask;

    /**
     * @description: 默认构造函数
     */
    public CreateAccountPane() {
        this((AccountFactory<?>) null, AccountMode.OFFLINE);
    }

    /**
     * @description: 构造函数，指定账户工厂和账户模式
     * @param factory 账户工厂
     * @param accountMode 账户模式
     */
    public CreateAccountPane(AccountFactory<?> factory, AccountMode accountMode) {
        if (factory == null) {
            // 简化逻辑：直接使用离线账户，不显示方法切换器
            showMethodSwitcher = false;
            factory = Accounts.FACTORY_OFFLINE;
        } else {
            showMethodSwitcher = false;
        }
        this.factory = factory;
        this.accountMode = accountMode;

        // 设置对话框标题
        {
            String title;
            if (showMethodSwitcher) {
                title = "account.create";
            } else {
                if (accountMode == AccountMode.CARD_KEY) {
                    title = "account.mode.card.key";
                } else {
                    title = "account.create." + Accounts.getLoginType(factory);
                }
            }
            setHeading(new Label(i18n(title)));
        }

        // 创建底部按钮区域
        {
            lblErrorMessage = new Label();
            lblErrorMessage.setWrapText(true);
            lblErrorMessage.setMaxWidth(400);

            btnAccept = new JFXButton(i18n("account.login"));
            btnAccept.getStyleClass().add("dialog-accept");
            btnAccept.setOnAction(e -> onAccept());

            spinner = new SpinnerPane();
            spinner.getStyleClass().add("small-spinner-pane");
            spinner.setContent(btnAccept);

            JFXButton btnCancel = new JFXButton(i18n("button.cancel"));
            btnCancel.getStyleClass().add("dialog-cancel");
            btnCancel.setOnAction(e -> onCancel());
            onEscPressed(this, btnCancel::fire);

            HBox hbox = new HBox(spinner, btnCancel);
            hbox.setAlignment(Pos.CENTER_RIGHT);

            setActions(lblErrorMessage, hbox);
        }

        // 创建主体内容区域
        detailsContainer = new StackPane();
        detailsContainer.setPadding(new Insets(10, 0, 0, 0));
        body = detailsContainer;
        setBody(body);

        initDetailsPane();

        setPrefWidth(560);
    }

    /**
     * @description: 构造函数，用于第三方认证服务器
     * @param authServer 认证服务器
     */
    public CreateAccountPane(AuthlibInjectorServer authServer) {
        // 注释掉第三方认证服务器支持，强制使用离线账户
        this(Accounts.FACTORY_OFFLINE, AccountMode.OFFLINE);
    }

    /**
     * @description: 处理确认按钮点击事件
     */
    private void onAccept() {
        spinner.showSpinner();
        lblErrorMessage.setText("");

        // 禁用主体内容
        body.setDisable(true);

        String username;
        String password;
        Object additionalData;
        if (detailsPane instanceof AccountDetailsInputPane) {
            AccountDetailsInputPane details = (AccountDetailsInputPane) detailsPane;

            if (accountMode == AccountMode.CARD_KEY) {
                if (AuthorizationChecker.checkCardAuthorization(details.getCardKey())) {
                    System.out.println("return cardKey success");
                } else {
                    System.out.println("return cardKey failed");
                    body.setDisable(false);
                    spinner.hideSpinner();
                    return;
                }
            } else {
                if (AuthorizationChecker.checkWebcastAuthorization(details.getPlatform(), details.getRoomNumber())) {
                    System.out.println("return room success");
                } else {
                    System.out.println("return room failed");
                    body.setDisable(false);
                    spinner.hideSpinner();
                    return;
                }
            }
            username = details.getUsername();
            password = details.getPassword();
            additionalData = details.getAdditionalData();
        } else {
            username = null;
            password = null;
            additionalData = null;
        }


        Runnable doCreate = () -> {
            logging.set(true);
            deviceCode.set(null);

            loginTask = Task.supplyAsync(() -> factory.create(new DialogCharacterSelector(), username, password, null, additionalData))
                    .whenComplete(Schedulers.javafx(), account -> {
                        int oldIndex = Accounts.getAccounts().indexOf(account);
                        if (oldIndex == -1) {
                            Accounts.getAccounts().add(account);
                        } else {
                            // 如果账户已存在，先删除旧账户再添加新账户
                            Accounts.getAccounts().remove(oldIndex);
                            Accounts.getAccounts().add(oldIndex, account);
                        }

                        // 选择新账户
                        Accounts.setSelectedAccount(account);

                        spinner.hideSpinner();
                        fireEvent(new DialogCloseEvent());
                    }, exception -> {
                        if (exception instanceof NoSelectedCharacterException) {
                            fireEvent(new DialogCloseEvent());
                        } else {
                            lblErrorMessage.setText(Accounts.localizeErrorMessage(exception));
                        }
                        body.setDisable(false);
                        spinner.hideSpinner();
                    }).executor(true);
        };

        // 处理离线账户用户名验证
        if (factory instanceof OfflineAccountFactory && username != null && !USERNAME_CHECKER_PATTERN.matcher(username).matches()) {
            JFXButton btnYes = new JFXButton(i18n("button.ok"));
            btnYes.getStyleClass().add("dialog-error");
            btnYes.setOnAction(e -> doCreate.run());
            btnYes.setDisable(true);

            int countdown = 10;
            KeyFrame[] keyFrames = new KeyFrame[countdown + 1];
            for (int i = 0; i < countdown; i++) {
                keyFrames[i] = new KeyFrame(Duration.seconds(i),
                        new KeyValue(btnYes.textProperty(), i18n("button.ok.countdown", countdown - i)));
            }
            keyFrames[countdown] = new KeyFrame(Duration.seconds(countdown),
                    new KeyValue(btnYes.textProperty(), i18n("button.ok")),
                    new KeyValue(btnYes.disableProperty(), false));

            Timeline timeline = new Timeline(keyFrames);
            Controllers.confirmAction(
                    i18n("account.methods.offline.name.invalid"), i18n("message.warning"),
                    MessageDialogPane.MessageType.WARNING,
                    btnYes,
                    () -> {
                        timeline.stop();
                        body.setDisable(false);
                        spinner.hideSpinner();
                    }
            );
            timeline.play();
        } else {
            doCreate.run();
        }
    }

    /**
     * @description: 处理取消按钮点击事件
     */
    private void onCancel() {
        if (loginTask != null) {
            loginTask.cancel();
        }
        fireEvent(new DialogCloseEvent());
    }

    /**
     * @description: 初始化详情面板
     */
    private void initDetailsPane() {
        if (detailsPane != null) {
            btnAccept.disableProperty().unbind();
            detailsContainer.getChildren().remove(detailsPane);
            lblErrorMessage.setText("");
        }

        // 创建账户详情输入面板
        detailsPane = new AccountDetailsInputPane(factory, accountMode, btnAccept::fire);
        btnAccept.disableProperty().bind(((AccountDetailsInputPane) detailsPane).validProperty().not());

        detailsContainer.getChildren().add(detailsPane);
    }

    /**
     * @description: 账户详情输入面板内部类
     */
    private static class AccountDetailsInputPane extends GridPane {

        /**
         * @description: 账户工厂
         */
        private final AccountFactory<?> factory;

        /**
         * @description: 账户模式
         */
        private final AccountMode accountMode;

        /**
         * @description: 用户名输入框
         */
        private @Nullable JFXTextField txtUsername;

        /**
         * @description: 密码输入框
         */
        private @Nullable JFXPasswordField txtPassword;

        /**
         * @description: UUID输入框
         */
        private @Nullable JFXTextField txtUUID;

        /**
         * @description: 平台选择框
         */
        private @Nullable JFXComboBox<String> cboPlatform;

        /**
         * @description: 房间号输入框
         */
        private @Nullable JFXTextField txtRoomNumber;

        /**
         * @description: 卡密输入框
         */
        private @Nullable JFXTextField txtCardKey;

        /**
         * @description: 验证状态绑定
         */
        private final BooleanBinding valid;

        /**
         * @description: 构造函数
         * @param factory 账户工厂
         * @param accountMode 账户模式
         * @param onAction 动作回调
         */
        public AccountDetailsInputPane(AccountFactory<?> factory, AccountMode accountMode, Runnable onAction) {
            this.factory = factory;
            this.accountMode = accountMode;

            setVgap(22);
            setHgap(15);
            setAlignment(Pos.CENTER);

            ColumnConstraints col0 = new ColumnConstraints();
            col0.setMinWidth(USE_PREF_SIZE);
            getColumnConstraints().add(col0);
            ColumnConstraints col1 = new ColumnConstraints();
            col1.setHgrow(Priority.ALWAYS);
            getColumnConstraints().add(col1);

            int rowIndex = 0;

            // 非官方版本警告
            if (!IntegrityChecker.isOfficial() && !(factory instanceof OfflineAccountFactory)) {
                HintPane hintPane = new HintPane(MessageDialogPane.MessageType.WARNING);
                hintPane.setSegment(i18n("unofficial.hint"));
                GridPane.setColumnSpan(hintPane, 2);
                add(hintPane, 0, rowIndex);
                rowIndex++;
            }

            // 用户名输入
            if (factory.getLoginType().requiresUsername) {
                Label lblUsername = new Label(i18n("account.username"));
                setHalignment(lblUsername, HPos.LEFT);
                add(lblUsername, 0, rowIndex);

                txtUsername = new JFXTextField();
                txtUsername.setValidators(
                        new RequiredValidator(),
                        new Validator(i18n("input.email"), username -> {
                            if (requiresEmailAsUsername()) {
                                return username.contains("@");
                            } else {
                                return true;
                            }
                        }));
                setValidateWhileTextChanged(txtUsername, true);
                txtUsername.setOnAction(e -> onAction.run());
                add(txtUsername, 1, rowIndex);
                rowIndex++;

                // 根据账户模式显示不同的输入控件
                if (factory instanceof OfflineAccountFactory) {
                    if (accountMode == AccountMode.OFFLINE) {
                        // 离线模式：显示直播平台选择器
                        Label lblPlatform = new Label(i18n("account.live.platform"));
                        setHalignment(lblPlatform, HPos.LEFT);
                        add(lblPlatform, 0, rowIndex);

                        cboPlatform = new JFXComboBox<>();
                        cboPlatform.getItems().addAll("抖音", "快手", "BiliBili", "小红书","Twitch", "TikTok");
                        cboPlatform.setPromptText(i18n("account.live.platform.prompt"));
                        cboPlatform.setMaxWidth(Double.MAX_VALUE);
                        add(cboPlatform, 1, rowIndex);
                        rowIndex++;

                        // 直播房间号输入框
                        Label lblRoomNumber = new Label(i18n("account.live.room.number"));
                        setHalignment(lblRoomNumber, HPos.LEFT);
                        add(lblRoomNumber, 0, rowIndex);

                        txtRoomNumber = new JFXTextField();
                        txtRoomNumber.setPromptText(i18n("account.live.room.number.prompt"));
                        txtRoomNumber.setValidators(new RequiredValidator());
                        setValidateWhileTextChanged(txtRoomNumber, true);
                        txtRoomNumber.setOnAction(e -> onAction.run());
                        add(txtRoomNumber, 1, rowIndex);
                        rowIndex++;
                    } else if (accountMode == AccountMode.CARD_KEY) {
                        // 卡密模式：显示卡密输入框
                        Label lblCardKey = new Label(i18n("account.input.card.key"));
                        setHalignment(lblCardKey, HPos.LEFT);
                        add(lblCardKey, 0, rowIndex);

                        txtCardKey = new JFXTextField();
                        txtCardKey.setPromptText(i18n("account.input.card.key.prompt"));
                        txtCardKey.setValidators(new RequiredValidator());
                        setValidateWhileTextChanged(txtCardKey, true);
                        txtCardKey.setOnAction(e -> onAction.run());
                        add(txtCardKey, 1, rowIndex);
                        rowIndex++;
                    }
                }
            }

            // 密码输入
            if (factory.getLoginType().requiresPassword) {
                Label lblPassword = new Label(i18n("account.password"));
                setHalignment(lblPassword, HPos.LEFT);
                add(lblPassword, 0, rowIndex);

                txtPassword = new JFXPasswordField();
                txtPassword.setValidators(new RequiredValidator());
                setValidateWhileTextChanged(txtPassword, true);
                txtPassword.setOnAction(e -> onAction.run());
                add(txtPassword, 1, rowIndex);
                rowIndex++;
            }

            // 离线账户的高级设置
            if (factory instanceof OfflineAccountFactory) {
                txtUsername.setPromptText(i18n("account.methods.offline.name.special_characters"));
                FXUtils.installFastTooltip(txtUsername, i18n("account.methods.offline.name.special_characters"));

                rowIndex++;

                HBox box = new HBox();
                MenuUpDownButton advancedButton = new MenuUpDownButton();
                box.getChildren().setAll(advancedButton);
                advancedButton.setText(i18n("settings.advanced"));
                GridPane.setColumnSpan(box, 2);
                add(box, 0, rowIndex);
                rowIndex++;

                Label lblUUID = new Label(i18n("account.methods.offline.uuid"));
                lblUUID.managedProperty().bind(advancedButton.selectedProperty());
                lblUUID.visibleProperty().bind(advancedButton.selectedProperty());
                setHalignment(lblUUID, HPos.LEFT);
                add(lblUUID, 0, rowIndex);

                txtUUID = new JFXTextField();
                txtUUID.managedProperty().bind(advancedButton.selectedProperty());
                txtUUID.visibleProperty().bind(advancedButton.selectedProperty());
                txtUUID.setValidators(new UUIDValidator());
                txtUUID.promptTextProperty().bind(BindingMapping.of(txtUsername.textProperty()).map(name -> OfflineAccountFactory.getUUIDFromUserName(name).toString()));
                txtUUID.setOnAction(e -> onAction.run());
                add(txtUUID, 1, rowIndex);
                rowIndex++;

                HintPane hintPane = new HintPane(MessageDialogPane.MessageType.WARNING);
                hintPane.managedProperty().bind(advancedButton.selectedProperty());
                hintPane.visibleProperty().bind(advancedButton.selectedProperty());
                hintPane.setText(i18n("account.methods.offline.uuid.hint"));
                GridPane.setColumnSpan(hintPane, 2);
                add(hintPane, 0, rowIndex);
                rowIndex++;
            }

            // 创建验证绑定
            valid = new BooleanBinding() {
                {
                    if (txtUsername != null)
                        bind(txtUsername.textProperty());
                    if (txtPassword != null)
                        bind(txtPassword.textProperty());
                    if (txtUUID != null)
                        bind(txtUUID.textProperty());
                    if (cboPlatform != null)
                        bind(cboPlatform.valueProperty());
                    if (txtRoomNumber != null)
                        bind(txtRoomNumber.textProperty());
                    if (txtCardKey != null)
                        bind(txtCardKey.textProperty());
                }

                @Override
                protected boolean computeValue() {
                    if (txtUsername != null && !txtUsername.validate())
                        return false;
                    if (txtPassword != null && !txtPassword.validate())
                        return false;
                    if (txtUUID != null && !txtUUID.validate())
                        return false;
                    if (txtCardKey != null && !txtCardKey.validate())
                        return false;
                    if (txtRoomNumber != null && !txtRoomNumber.validate())
                        return false;
                    return true;
                }
            };
        }

        /**
         * @description: 检查是否需要邮箱格式的用户名
         * @return boolean 是否需要邮箱格式
         */
        private boolean requiresEmailAsUsername() {
            return false;
        }

        /**
         * @description: 获取额外数据
         * @return Object 额外数据对象
         */
        public Object getAdditionalData() {
            if (factory instanceof OfflineAccountFactory) {
                UUID uuid = txtUUID == null ? null : StringUtils.isBlank(txtUUID.getText()) ? null : UUIDTypeAdapter.fromString(txtUUID.getText());

                if (accountMode == AccountMode.CARD_KEY) {
                    // 卡密模式：使用便捷构造函数创建包含卡密信息的AdditionalData
                    String cardKey = txtCardKey == null ? null : txtCardKey.getText();
                    return new OfflineAccountFactory.AdditionalData(uuid, null, null,
                            cardKey != null ? cardKey.trim() : null, null, "CARD_KEY");
                } else {
                    // 离线模式：使用便捷构造函数创建包含直播信息的AdditionalData
                    String liveType = cboPlatform == null ? null : cboPlatform.getValue();
                    String liveRoom = txtRoomNumber == null ? null : txtRoomNumber.getText();
                    // 使用便捷构造函数：(UUID uuid, Skin skin, String liveType, String singleRoomNumber, String cardKey, String accountMode)
                    return new OfflineAccountFactory.AdditionalData(uuid, null, liveType,
                            liveRoom != null ? liveRoom.trim() : null, null, "LIVE");
                }
            } else {
                return null;
            }
        }

        /**
         * @description: 获取直播平台
         * @return String 直播平台名称
         */
        public @Nullable String getPlatform() {
            return cboPlatform == null ? null : cboPlatform.getValue();
        }

        /**
         * @description: 获取房间号
         * @return String 房间号
         */
        public @Nullable String getRoomNumber() {
            return txtRoomNumber == null ? null : txtRoomNumber.getText();
        }

        /**
         * @description: 获取卡密
         * @return String 卡密
         */
        public @Nullable String getCardKey() {
            return txtCardKey == null ? null : txtCardKey.getText();
        }

        /**
         * @description: 获取用户名
         * @return String 用户名
         */
        public @Nullable String getUsername() {
            return txtUsername == null ? null : txtUsername.getText();
        }

        /**
         * @description: 获取密码
         * @return String 密码
         */
        public @Nullable String getPassword() {
            return txtPassword == null ? null : txtPassword.getText();
        }

        /**
         * @description: 获取验证状态属性
         * @return BooleanBinding 验证状态绑定
         */
        public BooleanBinding validProperty() {
            return valid;
        }

        /**
         * @description: 设置焦点到用户名输入框
         */
        public void focus() {
            if (txtUsername != null) {
                txtUsername.requestFocus();
            }
        }
    }

    /**
     * @description: 对话框字符选择器内部类
     */
    private static class DialogCharacterSelector extends BorderPane implements CharacterSelector {

        /**
         * @description: 高级列表框
         */
        private final AdvancedListBox listBox = new AdvancedListBox();

        /**
         * @description: 取消按钮
         */
        private final JFXButton cancel = new JFXButton();

        /**
         * @description: 倒计时锁
         */
        private final CountDownLatch latch = new CountDownLatch(1);

        /**
         * @description: 选中的游戏档案
         */
        private GameProfile selectedProfile = null;

        /**
         * @description: 构造函数
         */
        public DialogCharacterSelector() {
            setStyle("-fx-padding: 8px;");

            cancel.setText(i18n("button.cancel"));
            StackPane.setAlignment(cancel, Pos.BOTTOM_RIGHT);
            cancel.setOnAction(e -> latch.countDown());

            listBox.startCategory(i18n("account.choose").toUpperCase(Locale.ROOT));

            setCenter(listBox);

            HBox hbox = new HBox();
            hbox.setAlignment(Pos.CENTER_RIGHT);
            hbox.getChildren().add(cancel);
            setBottom(hbox);

            onEscPressed(this, cancel::fire);
        }

        /**
         * @description: 选择游戏档案
         * @param service Yggdrasil服务
         * @param profiles 游戏档案列表
         * @return GameProfile 选中的游戏档案
         * @throws NoSelectedCharacterException 未选择字符异常
         */
        @Override
        public GameProfile select(YggdrasilService service, List<GameProfile> profiles) throws NoSelectedCharacterException {
            Platform.runLater(() -> {
                for (GameProfile profile : profiles) {
                    Canvas portraitCanvas = new Canvas(32, 32);
                    TexturesLoader.bindAvatar(portraitCanvas, service, profile.getId());

                    IconedItem accountItem = new IconedItem(portraitCanvas, profile.getName());
                    FXUtils.onClicked(accountItem, () -> {
                        selectedProfile = profile;
                        latch.countDown();
                    });
                    listBox.add(accountItem);
                }
                Controllers.dialog(this);
            });

            try {
                latch.await();

                if (selectedProfile == null)
                    throw new NoSelectedCharacterException();

                return selectedProfile;
            } catch (InterruptedException ignored) {
                throw new NoSelectedCharacterException();
            } finally {
                Platform.runLater(() -> fireEvent(new DialogCloseEvent()));
            }
        }
    }

    /**
     * @description: 对话框显示时的回调
     */
    @Override
    public void onDialogShown() {
        if (detailsPane instanceof AccountDetailsInputPane) {
            ((AccountDetailsInputPane) detailsPane).focus();
        }
    }

    /**
     * @description: UUID验证器内部类
     */
    private static class UUIDValidator extends ValidatorBase {

        /**
         * @description: 默认构造函数
         */
        public UUIDValidator() {
            this(i18n("account.methods.offline.uuid.malformed"));
        }

        /**
         * @description: 构造函数
         * @param message 错误消息
         */
        public UUIDValidator(@NamedArg("message") String message) {
            super(message);
        }

        /**
         * @description: 执行验证
         */
        @Override
        protected void eval() {
            if (srcControl.get() instanceof TextInputControl) {
                evalTextInputField();
            }
        }

        /**
         * @description: 验证文本输入字段
         */
        private void evalTextInputField() {
            TextInputControl textField = ((TextInputControl) srcControl.get());
            if (StringUtils.isBlank(textField.getText())) {
                hasErrors.set(false);
                return;
            }

            try {
                UUIDTypeAdapter.fromString(textField.getText());
                hasErrors.set(false);
            } catch (IllegalArgumentException ignored) {
                hasErrors.set(true);
            }
        }
    }

    /**
     * @description: 微软账户编辑档案URL常量
     */
    private static final String MICROSOFT_ACCOUNT_EDIT_PROFILE_URL = "https://support.microsoft.com/account-billing/837badbc-999e-54d2-2617-d19206b9540a";
}