// 文件：MainPage.java
// 路径：HMCL/src/main/java/org/jackhuang/hmcl/ui/main/MainPage.java
/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * 在原有代码基础上添加启动前的账户验证逻辑
 */
package org.jackhuang.hmcl.ui.main;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXPopup;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.CharacterSelector;
import org.jackhuang.hmcl.auth.NoSelectedCharacterException;
import org.jackhuang.hmcl.auth.offline.OfflineAccountFactory;
import org.jackhuang.hmcl.auth.yggdrasil.GameProfile;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilService;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Profiles;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.PopupMenu;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.ui.versions.GameItem;
import org.jackhuang.hmcl.ui.versions.Versions;
import org.jackhuang.hmcl.upgrade.RemoteVersion;
import org.jackhuang.hmcl.upgrade.UpdateChecker;
import org.jackhuang.hmcl.upgrade.UpdateHandler;
import org.jackhuang.hmcl.util.AuthorizationChecker;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.javafx.BindingMapping;
import org.jackhuang.hmcl.util.javafx.MappedObservableList;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.ui.FXUtils.SINE;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @description: 主页面，集成了启动前的账户验证逻辑
 */
public final class MainPage extends StackPane implements DecoratorPage {
    private static final String ANNOUNCEMENT = "announcement";

    /**
     * @description: 用户名验证正则表达式
     */
    private static final Pattern USERNAME_CHECKER_PATTERN = Pattern.compile("^[A-Za-z0-9_]{1,16}$");

    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>();

    private final PopupMenu menu = new PopupMenu();

    private final StackPane popupWrapper = new StackPane(menu);
    private final JFXPopup popup = new JFXPopup(popupWrapper);

    private final StringProperty currentGame = new SimpleStringProperty(this, "currentGame");
    private final BooleanProperty showUpdate = new SimpleBooleanProperty(this, "showUpdate");
    private final ObjectProperty<RemoteVersion> latestVersion = new SimpleObjectProperty<>(this, "latestVersion");
    private final ObservableList<Version> versions = FXCollections.observableArrayList();
    private final ObservableList<Node> versionNodes;
    private Profile profile;

    private TransitionPane announcementPane;
    private final StackPane updatePane;
    private final JFXButton menuButton;

    {
        HBox titleNode = new HBox(8);
        titleNode.setPadding(new Insets(0, 0, 0, 2));
        titleNode.setAlignment(Pos.CENTER_LEFT);

        ImageView titleIcon = new ImageView(FXUtils.newBuiltinImage("/assets/img/icon-title.png"));
        Label titleLabel = new Label(Metadata.FULL_TITLE);
        titleLabel.getStyleClass().add("jfx-decorator-title");
        titleNode.getChildren().setAll(titleIcon, titleLabel);

        state.setValue(new State(null, titleNode, false, false, true));

        setPadding(new Insets(20));

        if (Metadata.isNightly() || (Metadata.isDev() && !Objects.equals(Metadata.VERSION, config().getShownTips().get(ANNOUNCEMENT)))) {
            String title;
            String content;
            if (Metadata.isNightly()) {
                title = i18n("update.channel.nightly.title");
                content = i18n("update.channel.nightly.hint");
            } else {
                title = i18n("update.channel.dev.title");
                content = i18n("update.channel.dev.hint");
            }

            VBox announcementCard = new VBox();

            BorderPane titleBar = new BorderPane();
            titleBar.getStyleClass().add("title");
            titleBar.setLeft(new Label(title));

            Node hideNode = SVG.CLOSE.createIcon(Theme.blackFill(), 20);
            hideNode.setCursor(Cursor.HAND);
            titleBar.setRight(hideNode);
            FXUtils.onClicked(hideNode, () -> {
                if (announcementPane != null) {
                    if (Metadata.isDev()) {
                        config().getShownTips().put(ANNOUNCEMENT, Metadata.VERSION);
                    }

                    announcementPane.setContent(new StackPane(), ContainerAnimations.FADE);
                }
            });

            TextFlow body = FXUtils.segmentToTextFlow(content, Controllers::onHyperlinkAction);
            body.setLineSpacing(4);

            announcementCard.getChildren().setAll(titleBar, body);
            announcementCard.setSpacing(16);
            announcementCard.getStyleClass().addAll("card", "announcement");

            VBox announcementBox = new VBox(16);
            announcementBox.getChildren().add(announcementCard);

            announcementPane = new TransitionPane();
            announcementPane.setContent(announcementBox, ContainerAnimations.NONE);

            getChildren().add(announcementPane);
        }

        updatePane = new StackPane();
        updatePane.setVisible(false);
        updatePane.getStyleClass().add("bubble");
        FXUtils.setLimitWidth(updatePane, 230);
        FXUtils.setLimitHeight(updatePane, 55);
        StackPane.setAlignment(updatePane, Pos.TOP_RIGHT);
        FXUtils.onClicked(updatePane, this::onUpgrade);
        FXUtils.onChange(showUpdateProperty(), this::showUpdate);
        // 绑定更新状态到UI属性
        showUpdate.bind(UpdateChecker.outdatedProperty());
        latestVersion.bind(UpdateChecker.latestVersionProperty());

        {
            HBox hBox = new HBox();
            hBox.setSpacing(12);
            hBox.setAlignment(Pos.CENTER_LEFT);
            StackPane.setAlignment(hBox, Pos.CENTER_LEFT);
            StackPane.setMargin(hBox, new Insets(9, 12, 9, 16));
            {
                Label lblIcon = new Label();
                lblIcon.setGraphic(SVG.UPDATE.createIcon(Theme.whiteFill(), 20));

                TwoLineListItem prompt = new TwoLineListItem();
                prompt.setSubtitle(i18n("update.bubble.subtitle"));
                prompt.setPickOnBounds(false);
                prompt.titleProperty().bind(BindingMapping.of(latestVersionProperty()).map(latestVersion ->
                        latestVersion == null ? "" : i18n("update.bubble.title", latestVersion.getVersion())));

                hBox.getChildren().setAll(lblIcon, prompt);
            }

            JFXButton closeUpdateButton = new JFXButton();
            closeUpdateButton.setGraphic(SVG.CLOSE.createIcon(Theme.whiteFill(), 10));
            StackPane.setAlignment(closeUpdateButton, Pos.TOP_RIGHT);
            closeUpdateButton.getStyleClass().add("toggle-icon-tiny");
            StackPane.setMargin(closeUpdateButton, new Insets(5));
            closeUpdateButton.setOnAction(e -> closeUpdateBubble());

            updatePane.getChildren().setAll(hBox, closeUpdateButton);
        }

        StackPane launchPane = new StackPane();
        launchPane.getStyleClass().add("launch-pane");
        launchPane.setMaxWidth(230);
        launchPane.setMaxHeight(55);
        launchPane.setOnScroll(event -> {
            int index = IntStream.range(0, versions.size())
                    .filter(i -> versions.get(i).getId().equals(getCurrentGame()))
                    .findFirst().orElse(-1);
            if (index < 0) return;
            if (event.getDeltaY() > 0) {
                index--;
            } else {
                index++;
            }
            profile.setSelectedVersion(versions.get((index + versions.size()) % versions.size()).getId());
        });
        StackPane.setAlignment(launchPane, Pos.BOTTOM_RIGHT);
        {
            JFXButton launchButton = new JFXButton();
            launchButton.setPrefWidth(230);
            launchButton.setPrefHeight(55);
            launchButton.setOnAction(e -> launch());
            launchButton.setDefaultButton(true);
            launchButton.setClip(new Rectangle(-100, -100, 310, 200));
            {
                VBox graphic = new VBox();
                graphic.setAlignment(Pos.CENTER);
                graphic.setTranslateX(-7);
                graphic.setMaxWidth(200);
                Label launchLabel = new Label(i18n("version.launch"));
                launchLabel.setStyle("-fx-font-size: 16px;");
                Label currentLabel = new Label();
                currentLabel.setStyle("-fx-font-size: 12px;");
                currentLabel.textProperty().bind(Bindings.createStringBinding(() -> {
                    if (getCurrentGame() == null) {
                        return i18n("version.empty");
                    } else {
                        return getCurrentGame();
                    }
                }, currentGameProperty()));
                graphic.getChildren().setAll(launchLabel, currentLabel);

                launchButton.setGraphic(graphic);
            }

            Rectangle separator = new Rectangle();
            separator.setWidth(1);
            separator.setHeight(57);
            separator.setTranslateX(95);
            separator.setMouseTransparent(true);

            menuButton = new JFXButton();
            menuButton.setPrefHeight(55);
            menuButton.setPrefWidth(230);
            menuButton.setStyle("-fx-font-size: 15px;");
            menuButton.setOnAction(e -> onMenu());
            menuButton.setClip(new Rectangle(211, -100, 100, 200));
            StackPane graphic = new StackPane();
            Node svg = SVG.ARROW_DROP_UP.createIcon(Theme.foregroundFillBinding(), 30);
            StackPane.setAlignment(svg, Pos.CENTER_RIGHT);
            graphic.getChildren().setAll(svg);
            graphic.setTranslateX(6);
            FXUtils.installFastTooltip(menuButton, i18n("version.switch"));
            menuButton.setGraphic(graphic);

            EventHandler<MouseEvent> secondaryClickHandle = event -> {
                if (event.getButton() == MouseButton.SECONDARY && event.getClickCount() == 1) {
                    menuButton.fire();
                    event.consume();
                }
            };
            launchButton.addEventHandler(MouseEvent.MOUSE_CLICKED, secondaryClickHandle);
            menuButton.addEventHandler(MouseEvent.MOUSE_CLICKED, secondaryClickHandle);

            launchPane.getChildren().setAll(launchButton, separator, menuButton);
        }

        getChildren().addAll(updatePane, launchPane);

        menu.setMaxHeight(365);
        menu.setMaxWidth(545);
        menu.setAlwaysShowingVBar(true);
        FXUtils.onClicked(menu, popup::hide);
        versionNodes = MappedObservableList.create(versions, version -> {
            Node node = PopupMenu.wrapPopupMenuItem(new GameItem(profile, version.getId()));
            FXUtils.onClicked(node, () -> profile.setSelectedVersion(version.getId()));
            return node;
        });
        Bindings.bindContent(menu.getContent(), versionNodes);
    }

    private void showUpdate(boolean show) {
        doAnimation(show);

        if (show && getLatestVersion() != null && !Objects.equals(config().getPromptedVersion(), getLatestVersion().getVersion())) {
            Controllers.dialog(new MessageDialogPane.Builder("", i18n("update.bubble.title", getLatestVersion().getVersion()), MessageDialogPane.MessageType.INFO)
                    .addAction(i18n("button.view"), () -> {
                        config().setPromptedVersion(getLatestVersion().getVersion());
                        onUpgrade();
                    })
                    .addCancel(null)
                    .build());
        }
    }

    private void doAnimation(boolean show) {
        if (AnimationUtils.isAnimationEnabled()) {
            Duration duration = Duration.millis(320);
            Timeline nowAnimation = new Timeline();
            nowAnimation.getKeyFrames().addAll(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(updatePane.translateXProperty(), show ? 260 : 0, SINE)),
                    new KeyFrame(duration,
                            new KeyValue(updatePane.translateXProperty(), show ? 0 : 260, SINE)));
            if (show) nowAnimation.getKeyFrames().add(
                    new KeyFrame(Duration.ZERO, e -> updatePane.setVisible(true)));
            else nowAnimation.getKeyFrames().add(
                    new KeyFrame(duration, e -> updatePane.setVisible(false)));
            nowAnimation.play();
        } else {
            updatePane.setVisible(show);
        }
    }

    /**
     * @description: 启动游戏，集成账户验证和创建逻辑
     */
    private void launch() {
        // 获取左侧栏的输入数据
        RootPage.AccountInputData inputData = RootPage.getAccountInputData();

        if (inputData == null ||
                StringUtils.isBlank(inputData.getUsername()) ||
                StringUtils.isBlank(inputData.getLoginMethod())) {

            Controllers.dialog("请输入用户名并选择登陆方式以创建账户",
                    "启动失败", MessageDialogPane.MessageType.ERROR);
            return;
        }

        String username = inputData.getUsername();
        String loginMethod = inputData.getLoginMethod();

        // 第一步：验证用户名格式
        if (!USERNAME_CHECKER_PATTERN.matcher(username).matches()) {
            Controllers.dialog("用户名格式不正确，仅支持英文字母丶数字及下划线，且长度不超过16个字符。",
                    "输入错误", MessageDialogPane.MessageType.ERROR);
            return;
        }

        // 第二步：根据登录方式进行权限验证
        boolean authResult = false;
        String accountMode = "";
        String liveType = null;
        String liveRoom = null;
        String cardKey = null;

        if ("直播间验证".equals(loginMethod)) {
            String platform = inputData.getPlatform();
            String roomNumber = inputData.getRoomNumber();

            if (StringUtils.isBlank(platform) || StringUtils.isBlank(roomNumber)) {
                Controllers.dialog("请选择直播平台并输入直播间号",
                        "输入错误", MessageDialogPane.MessageType.ERROR);
                return;
            }

            LOG.info("开始直播间验证: platform=" + platform + ", roomNumber=" + roomNumber);
            authResult = AuthorizationChecker.checkWebcastAuthorization(platform, roomNumber);

            if (authResult) {
                accountMode = "LIVE";
                liveType = platform;
                liveRoom = roomNumber;
                LOG.info("直播间验证成功");
            } else {
                Controllers.dialog("直播间验证失败，请检查平台选择和直播间号是否正确，或确认该直播间是否已获得授权",
                        "验证失败", MessageDialogPane.MessageType.ERROR);
                LOG.info("直播间验证失败");
                return;
            }

        } else if ("卡密验证".equals(loginMethod)) {
            cardKey = inputData.getCardKey();

            if (StringUtils.isBlank(cardKey)) {
                Controllers.dialog("请输入卡密",
                        "输入错误", MessageDialogPane.MessageType.ERROR);
                return;
            }

            LOG.info("开始卡密验证");
            authResult = AuthorizationChecker.checkCardAuthorization(cardKey);

            if (authResult) {
                accountMode = "CARD_KEY";
                LOG.info("卡密验证成功");
            } else {
                Controllers.dialog("卡密验证失败，请检查卡密是否正确或是否已过期",
                        "验证失败", MessageDialogPane.MessageType.ERROR);
                LOG.info("卡密验证失败");
                return;
            }
        } else {
            Controllers.dialog("请选择登录方式",
                    "输入错误", MessageDialogPane.MessageType.ERROR);
            return;
        }

        // 验证通过，创建账户然后启动游戏
        createAccountAndLaunch(username, accountMode, liveType, liveRoom, cardKey);
    }

    /**
     * @description: 创建账户并启动游戏
     * @param username 用户名
     * @param accountMode 账户模式
     * @param liveType 直播类型
     * @param liveRoom 直播房间
     * @param cardKey 卡密
     */
    private void createAccountAndLaunch(String username, String accountMode,
                                        String liveType, String liveRoom, String cardKey) {

        LOG.info("开始创建账户并启动游戏: username=" + username + ", accountMode=" + accountMode);

        // 创建额外数据
        UUID uuid = OfflineAccountFactory.getUUIDFromUserName(username);
        OfflineAccountFactory.AdditionalData additionalData =
                new OfflineAccountFactory.AdditionalData(uuid, null, liveType, liveRoom, cardKey, accountMode);

        // 创建账户任务
        Task<Account> createAccountTask = Task.supplyAsync(() -> {
            try {
                return Accounts.FACTORY_OFFLINE.create(new SimpleCharacterSelector(), username, null, null, additionalData);
            } catch (Exception e) {
                throw new RuntimeException("创建账户失败: " + e.getMessage(), e);
            }
        });

        createAccountTask.whenComplete(Schedulers.javafx(), account -> {
            try {
                // 检查是否已存在账户，如果存在则替换
                int oldIndex = Accounts.getAccounts().indexOf(account);
                if (oldIndex == -1) {
                    Accounts.getAccounts().add(account);
                    LOG.info("添加新账户: " + username);
                } else {
                    // 替换现有账户
                    Accounts.getAccounts().remove(oldIndex);
                    Accounts.getAccounts().add(oldIndex, account);
                    LOG.info("替换现有账户: " + username);
                }

                // 选择新账户
                Accounts.setSelectedAccount(account);

                LOG.info("账户创建完成，开始启动游戏");

                // 启动游戏
                Versions.launch(Profiles.getSelectedProfile());

            } catch (Exception e) {
                LOG.warning("Failed to process account", e);
                Controllers.dialog("处理账户时发生错误: " + e.getMessage(),
                        "处理失败", MessageDialogPane.MessageType.ERROR);
            }
        }, exception -> {
            if (exception instanceof NoSelectedCharacterException) {
                // 用户取消了字符选择，直接关闭
                LOG.info("用户取消了字符选择");
            } else if (!(exception instanceof CancellationException)) {
                LOG.warning("Failed to create account", exception);
                Controllers.dialog("创建账户失败: " + Accounts.localizeErrorMessage(exception),
                        "创建失败", MessageDialogPane.MessageType.ERROR);
            }
        }).start();
    }

    /**
     * @description: 简单字符选择器，用于离线账户
     */
    private static class SimpleCharacterSelector implements CharacterSelector {
        @Override
        public GameProfile select(YggdrasilService service, List<GameProfile> profiles) throws NoSelectedCharacterException {
            // 对于离线账户，通常只有一个配置文件，直接返回第一个
            if (profiles.isEmpty()) {
                throw new NoSelectedCharacterException();
            }
            return profiles.get(0);
        }
    }

    private void onMenu() {
        Node contentNode;
        if (menu.getContent().isEmpty()) {
            Label placeholder = new Label(i18n("version.empty"));
            placeholder.setStyle("-fx-padding: 10px; -fx-text-fill: gray; -fx-font-style: italic;");
            contentNode = placeholder;
        } else {
            contentNode = menu;
        }

        popupWrapper.getChildren().setAll(contentNode);

        if (popup.isShowing()) {
            popup.hide();
        }
        popup.show(
                menuButton,
                JFXPopup.PopupVPosition.BOTTOM,
                JFXPopup.PopupHPosition.RIGHT,
                0,
                -menuButton.getHeight()
        );
    }

    private void onUpgrade() {
        RemoteVersion target = UpdateChecker.getLatestVersion();
        if (target == null) {
            return;
        }
        UpdateHandler.updateFrom(target);
    }

    private void closeUpdateBubble() {
        showUpdate.unbind();
        showUpdate.set(false);
    }

    @Override
    public ReadOnlyObjectWrapper<State> stateProperty() {
        return state;
    }

    public String getCurrentGame() {
        return currentGame.get();
    }

    public StringProperty currentGameProperty() {
        return currentGame;
    }

    public void setCurrentGame(String currentGame) {
        this.currentGame.set(currentGame);
    }

    public boolean isShowUpdate() {
        return showUpdate.get();
    }

    public BooleanProperty showUpdateProperty() {
        return showUpdate;
    }

    public void setShowUpdate(boolean showUpdate) {
        this.showUpdate.set(showUpdate);
    }

    public RemoteVersion getLatestVersion() {
        return latestVersion.get();
    }

    public ObjectProperty<RemoteVersion> latestVersionProperty() {
        return latestVersion;
    }

    public void setLatestVersion(RemoteVersion latestVersion) {
        this.latestVersion.set(latestVersion);
    }

    public void initVersions(Profile profile, List<Version> versions) {
        FXUtils.checkFxUserThread();
        this.profile = profile;
        this.versions.setAll(versions);
    }
}