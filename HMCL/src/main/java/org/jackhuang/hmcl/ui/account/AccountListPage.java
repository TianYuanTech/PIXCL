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

import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Skin;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorServer;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.AdvancedListItem;
import org.jackhuang.hmcl.ui.construct.ClassTitle;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.javafx.MappedObservableList;

import java.util.Locale;

import static org.jackhuang.hmcl.ui.versions.VersionPage.wrap;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.javafx.ExtendedProperties.createSelectedItemPropertyFor;

/**
 * @description: 账户列表页面，管理游戏账户的创建和选择
 */
public final class AccountListPage extends DecoratorAnimatedPage implements DecoratorPage {

    /**
     * @description: 账户列表项的观察列表
     */
    private final ObservableList<AccountListItem> items;

    /**
     * @description: 页面状态属性
     */
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(State.fromTitle(i18n("account.manage")));

    /**
     * @description: 账户列表属性
     */
    private final ListProperty<Account> accounts = new SimpleListProperty<>(this, "accounts", FXCollections.observableArrayList());

    /**
     * @description: 认证服务器列表属性
     */
    private final ListProperty<AuthlibInjectorServer> authServers = new SimpleListProperty<>(this, "authServers", FXCollections.observableArrayList());

    /**
     * @description: 当前选中的账户属性
     */
    private final ObjectProperty<Account> selectedAccount;

    /**
     * @description: 构造函数，初始化账户列表页面
     */
    public AccountListPage() {
        items = MappedObservableList.create(accounts, AccountListItem::new);
        selectedAccount = createSelectedItemPropertyFor(items, Account.class);
    }

    /**
     * @description: 获取选中账户的属性
     * @return ObjectProperty<Account> 选中账户的属性对象
     */
    public ObjectProperty<Account> selectedAccountProperty() {
        return selectedAccount;
    }

    /**
     * @description: 获取账户列表属性
     * @return ListProperty<Account> 账户列表属性对象
     */
    public ListProperty<Account> accountsProperty() {
        return accounts;
    }

    /**
     * @description: 获取页面状态属性
     * @return ReadOnlyObjectProperty<State> 页面状态属性对象
     */
    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    /**
     * @description: 获取认证服务器列表属性
     * @return ListProperty<AuthlibInjectorServer> 认证服务器列表属性对象
     */
    public ListProperty<AuthlibInjectorServer> authServersProperty() {
        return authServers;
    }

    /**
     * @description: 创建默认的皮肤
     * @return Skin<?> 页面皮肤对象
     */
    @Override
    protected Skin<?> createDefaultSkin() {
        return new AccountListPageSkin(this);
    }

    /**
     * @description: 账户列表页面的皮肤实现类
     */
    private static class AccountListPageSkin extends DecoratorAnimatedPageSkin<AccountListPage> {

        /**
         * @description: 构造函数，创建账户列表页面的皮肤
         * @param skinnable 可换肤的账户列表页面对象
         */
        public AccountListPageSkin(AccountListPage skinnable) {
            super(skinnable);

            // 创建左侧方法选择区域
            {
                VBox boxMethods = new VBox();
                {
                    boxMethods.getStyleClass().add("advanced-list-box-content");
                    FXUtils.setLimitWidth(boxMethods, 200);

                    // 创建离线账户选项
                    AdvancedListItem offlineItem = new AdvancedListItem();
                    offlineItem.getStyleClass().add("navigation-drawer-item");
                    offlineItem.setActionButtonVisible(false);
                    offlineItem.setTitle(i18n("account.methods.offline"));
                    offlineItem.setLeftGraphic(wrap(SVG.PERSON));
                    offlineItem.setOnAction(e -> Controllers.dialog(new CreateAccountPane(Accounts.FACTORY_OFFLINE, CreateAccountPane.AccountMode.OFFLINE)));

                    // 创建卡密模式选项
                    AdvancedListItem cardKeyItem = new AdvancedListItem();
                    cardKeyItem.getStyleClass().add("navigation-drawer-item");
                    cardKeyItem.setActionButtonVisible(false);
                    cardKeyItem.setTitle(i18n("account.methods.card.key"));
                    cardKeyItem.setLeftGraphic(wrap(SVG.EDIT));
                    cardKeyItem.setOnAction(e -> Controllers.dialog(new CreateAccountPane(Accounts.FACTORY_OFFLINE, CreateAccountPane.AccountMode.CARD_KEY)));

                    ClassTitle title = new ClassTitle(i18n("account.create").toUpperCase(Locale.ROOT));

                    // 将标题和两个选项添加到方法选择区域
                    boxMethods.getChildren().setAll(title, offlineItem, cardKeyItem);
                }

                ScrollPane scrollPane = new ScrollPane(boxMethods);
                VBox.setVgrow(scrollPane, Priority.ALWAYS);
                setLeft(scrollPane);
            }

            // 创建右侧账户列表区域
            ScrollPane scrollPane = new ScrollPane();
            VBox list = new VBox();
            {
                scrollPane.setFitToWidth(true);

                list.maxWidthProperty().bind(scrollPane.widthProperty());
                list.setSpacing(10);
                list.getStyleClass().add("card-list");

                Bindings.bindContent(list.getChildren(), skinnable.items);

                scrollPane.setContent(list);
                FXUtils.smoothScrolling(scrollPane);

                setCenter(scrollPane);
            }
        }
    }
}