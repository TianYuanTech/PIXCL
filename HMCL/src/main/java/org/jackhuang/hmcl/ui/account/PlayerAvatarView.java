// 文件：PlayerAvatarView.java
// 路径：HMCL/src/main/java/org/jackhuang/hmcl/ui/account/PlayerAvatarView.java
package org.jackhuang.hmcl.ui.account;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.game.TexturesLoader;

/**
 * @description: 玩家头像显示组件，专门用于显示玩家头像
 */
public class PlayerAvatarView extends StackPane {

    /**
     * @description: 头像画布，用于绘制玩家头像
     */
    private final Canvas avatarCanvas;

    /**
     * @description: 构造函数，初始化头像组件
     */
    public PlayerAvatarView() {
        // 创建头像画布，尺寸为原来的四倍 (32x32 -> 128x128)
        avatarCanvas = new Canvas(128, 128);

        // 设置StackPane的对齐方式为居中
        setAlignment(Pos.CENTER);

        // 调整组件的尺寸，考虑到增加的内边距
        setPrefSize(150, 150); // 高度从150增加到210（128 + 30*2 + 额外空间）
        setMinSize(150, 150);
        setMaxSize(150, 150);

        // 设置StackPane的样式，确保居中显示
        setStyle("-fx-alignment: center;");

        // 将画布添加到容器中，并使用StackPane.setAlignment确保居中
        getChildren().add(avatarCanvas);
        StackPane.setAlignment(avatarCanvas, Pos.CENTER);

        // 初始化显示默认头像
        updateAvatar();
    }    /**
     * @description: 当前账户属性，用于监听账户变化并更新头像
     */
    private final ObjectProperty<Account> account = new SimpleObjectProperty<Account>() {
        @Override
        protected void invalidated() {
            updateAvatar();
        }
    };

    /**
     * @description: 更新头像显示
     */
    private void updateAvatar() {
        Account currentAccount = account.get();

        if (currentAccount == null) {
            // 没有账户时，解绑并显示默认头像
            TexturesLoader.unbindAvatar(avatarCanvas);
            TexturesLoader.drawAvatar(avatarCanvas, TexturesLoader.getDefaultSkinImage());
        } else {
            // 有账户时，绑定账户头像
            TexturesLoader.bindAvatar(avatarCanvas, currentAccount);
        }
    }

    /**
     * @description: 获取账户属性
     * @return ObjectProperty<Account> 账户属性
     */
    public ObjectProperty<Account> accountProperty() {
        return account;
    }

    /**
     * @description: 获取当前账户
     * @return Account 当前账户
     */
    public Account getAccount() {
        return account.get();
    }

    /**
     * @description: 设置账户
     * @param account 要设置的账户
     */
    public void setAccount(Account account) {
        this.account.set(account);
    }


}