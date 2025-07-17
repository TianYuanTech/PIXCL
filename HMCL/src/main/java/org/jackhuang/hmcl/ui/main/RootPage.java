// 文件：RootPage.java
// 路径：HMCL/src/main/java/org/jackhuang/hmcl/ui/main/RootPage.java
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
package org.jackhuang.hmcl.ui.main;

import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXTextField;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
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
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.account.AccountAdvancedListItem;
import org.jackhuang.hmcl.ui.construct.AdvancedListItem;
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
import static org.jackhuang.hmcl.ui.versions.VersionPage.wrap;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

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
     * @description: 获取页面状态属性
     * @return ReadOnlyObjectProperty<State> 页面状态
     */
    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return getMainPage().stateProperty();
    }

    /**
     * @description: 创建默认皮肤
     * @return Skin 页面皮肤
     */
    @Override
    protected Skin createDefaultSkin() {
        return new Skin(this);
    }

    /**
     * @description: 获取主页面实例
     * @return MainPage 主页面
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
     * @description: 皮肤实现类，包含账户输入界面的左侧栏
     */
    private static class Skin extends DecoratorAnimatedPageSkin<RootPage> {

        /**
         * @description: 构造函数，创建包含账户输入界面的左侧栏
         * @param control 控制器
         */
        protected Skin(RootPage control) {
            super(control);

            // 创建左侧栏，调整宽度以适应新组件
            VBox leftSidebar = new VBox();
            leftSidebar.getStyleClass().add("advanced-list-box-content");
            FXUtils.setLimitWidth(leftSidebar, 350); // 增加宽度以适应新组件

            // first item in left sidebar
            AccountAdvancedListItem accountListItem = new AccountAdvancedListItem();
            accountListItem.setOnAction(e -> Controllers.navigate(Controllers.getAccountListPage()));
            accountListItem.accountProperty().bind(Accounts.selectedAccountProperty());


            // 创建账户输入控件
            accountInputControls = new AccountInputControls();

            // 添加组件到左侧栏
            leftSidebar.getChildren().addAll(
                    accountListItem,
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

        public String getUsername() { return username; }
        public String getLoginMethod() { return loginMethod; }
        public String getPlatform() { return platform; }
        public String getRoomNumber() { return roomNumber; }
        public String getCardKey() { return cardKey; }
    }

    /**
     * @description: 账户输入控件类，只负责界面输入，不包含验证逻辑
     */
    /**
     * @description: 账户输入控件类，支持自动填充上次使用的账户数据
     */
    private static class AccountInputControls extends VBox {

        /**
         * @description: 用户名输入框
         */
        private final JFXTextField txtUsername;

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
         * @description: 构造函数，创建账户输入控件并设置默认值
         */
        public AccountInputControls() {
            setSpacing(10);
            setPadding(new Insets(15, 20, 15, 20));

            // 用户名输入框
            txtUsername = new JFXTextField();
            txtUsername.setPromptText("请输入用户名");
            txtUsername.setValidators(new RequiredValidator());
            txtUsername.setPrefWidth(310);
            setValidateWhileTextChanged(txtUsername, true);

            // 登录方式选择器
            cboLoginMethod = new JFXComboBox<>();
            cboLoginMethod.getItems().addAll("直播间验证", "卡密验证");
            cboLoginMethod.setPromptText("登录方式");
            cboLoginMethod.setPrefWidth(310);
            cboLoginMethod.setMaxWidth(310);
            // 设置默认选择为直播间验证
            cboLoginMethod.setValue("直播间验证");

            // 直播平台选择器
            cboPlatform = new JFXComboBox<>();
            cboPlatform.getItems().addAll("抖音", "快手", "BiliBili", "Twitch", "TikTok");
            cboPlatform.setPromptText("平台");
            cboPlatform.setPrefWidth(100);

            // 直播间号输入框
            txtRoomNumber = new JFXTextField();
            txtRoomNumber.setPromptText("直播间号");
            txtRoomNumber.setValidators(new RequiredValidator());
            txtRoomNumber.setPrefWidth(205);
            setValidateWhileTextChanged(txtRoomNumber, true);
            HBox.setHgrow(txtRoomNumber, Priority.ALWAYS);

            // 直播间验证容器
            liveContainer = new HBox(5);
            liveContainer.setAlignment(Pos.CENTER_LEFT);
            liveContainer.getChildren().addAll(cboPlatform, txtRoomNumber);
            // 由于默认选择直播间验证，所以直接显示
            liveContainer.setVisible(true);
            liveContainer.setManaged(true);

            // 卡密输入框
            txtCardKey = new JFXTextField();
            txtCardKey.setPromptText("卡密");
            txtCardKey.setValidators(new RequiredValidator());
            txtCardKey.setPrefWidth(310);
            setValidateWhileTextChanged(txtCardKey, true);

            // 卡密验证容器
            cardKeyContainer = new VBox();
            cardKeyContainer.getChildren().add(txtCardKey);
            // 默认隐藏卡密验证容器
            cardKeyContainer.setVisible(false);
            cardKeyContainer.setManaged(false);

            // 设置登录方式变化监听器
            cboLoginMethod.valueProperty().addListener(new ChangeListener<String>() {
                @Override
                public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
                    updateLoginMethodVisibility(newValue);
                }
            });

            // 创建验证绑定
            validBinding = new BooleanBinding() {
                {
                    bind(txtUsername.textProperty());
                    bind(cboLoginMethod.valueProperty());
                    bind(cboPlatform.valueProperty());
                    bind(txtRoomNumber.textProperty());
                    bind(txtCardKey.textProperty());
                }

                @Override
                protected boolean computeValue() {
                    if (!txtUsername.validate()) return false;

                    String loginMethod = cboLoginMethod.getValue();
                    if (loginMethod == null) return false;

                    if ("直播间验证".equals(loginMethod)) {
                        return cboPlatform.getValue() != null && txtRoomNumber.validate();
                    } else if ("卡密验证".equals(loginMethod)) {
                        return txtCardKey.validate();
                    }

                    return false;
                }
            };

            // 绑定到当前选中的账户
            currentAccount.bind(Accounts.selectedAccountProperty());

            // 添加所有组件
            getChildren().addAll(
                    txtUsername,
                    cboLoginMethod,
                    liveContainer,
                    cardKeyContainer
            );
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

            // 填充用户名
            txtUsername.setText(account.getUsername());

            // 如果是离线账户，尝试提取额外信息
            if (account instanceof OfflineAccount) {
                OfflineAccount offlineAccount = (OfflineAccount) account;

                String accountMode = offlineAccount.getAccountMode();
                String liveType = offlineAccount.getLiveType();
                String liveRoom = offlineAccount.getLiveRoom();
                String cardKey = offlineAccount.getCardKey();

                if ("LIVE".equals(accountMode) && liveType != null && liveRoom != null) {
                    // 设置为直播间验证模式
                    cboLoginMethod.setValue("直播间验证");
                    cboPlatform.setValue(liveType);
                    txtRoomNumber.setText(liveRoom);

                    LOG.info("自动填充直播间验证数据: " + liveType + " - " + liveRoom);

                } else if ("CARD_KEY".equals(accountMode) && cardKey != null) {
                    // 设置为卡密验证模式
                    cboLoginMethod.setValue("卡密验证");
                    txtCardKey.setText(cardKey);

                    LOG.info("自动填充卡密验证数据");

                } else {
                    // 未知模式或数据不完整，清空相关字段
                    cboLoginMethod.setValue(null);
                    clearModeSpecificFields();

                    LOG.info("账户模式未知或数据不完整，清空模式相关字段");
                }
            } else {
                // 非离线账户，只填充用户名，其他字段清空
                cboLoginMethod.setValue(null);
                clearModeSpecificFields();

                LOG.info("非离线账户，仅填充用户名: " + account.getUsername());
            }
        }

        /**
         * @description: 清空所有输入字段
         */
        private void clearAllFields() {
            txtUsername.clear();
            cboLoginMethod.setValue(null);
            clearModeSpecificFields();
        }

        /**
         * @description: 清空模式相关的输入字段
         */
        private void clearModeSpecificFields() {
            cboPlatform.setValue(null);
            txtRoomNumber.clear();
            txtCardKey.clear();
        }

        /**
         * @description: 更新登录方式的可见性
         * @param loginMethod 登录方式
         */
        private void updateLoginMethodVisibility(String loginMethod) {
            if ("直播间验证".equals(loginMethod)) {
                liveContainer.setVisible(true);
                liveContainer.setManaged(true);
                cardKeyContainer.setVisible(false);
                cardKeyContainer.setManaged(false);
            } else if ("卡密验证".equals(loginMethod)) {
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
                    txtUsername.getText(),
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

    /**
     * @description: 检查是否已加载模组包的标志
     */
    private boolean checkedModpack = false;

    /**
     * @description: 当版本刷新时的处理方法
     * @param repository 游戏仓库
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
}