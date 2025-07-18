// 文件：DecoratorAnimatedPage.java
// 路径：HMCL/src/main/java/org/jackhuang/hmcl/ui/decorator/DecoratorAnimatedPage.java
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
package org.jackhuang.hmcl.ui.decorator;

import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.ui.FXUtils;

/**
 * @description: 装饰器动画页面，支持左侧栏和中心内容区域
 */
public class DecoratorAnimatedPage extends Control {

    /**
     * @description: 左侧栏容器
     */
    protected final VBox left = new VBox();

    /**
     * @description: 中心内容容器
     */
    protected final StackPane center = new StackPane();

    {
        getStyleClass().add("gray-background");
    }

    /**
     * @description: 设置左侧栏内容
     * @param children 子节点数组
     */
    protected void setLeft(Node... children) {
        left.getChildren().setAll(children);
    }

    /**
     * @description: 设置中心内容
     * @param children 子节点数组
     */
    protected void setCenter(Node... children) {
        center.getChildren().setAll(children);
    }

    /**
     * @description: 获取左侧栏容器
     * @return VBox 左侧栏容器
     */
    public VBox getLeft() {
        return left;
    }

    /**
     * @description: 获取中心内容容器
     * @return StackPane 中心内容容器
     */
    public StackPane getCenter() {
        return center;
    }

    /**
     * @description: 创建默认皮肤
     * @return Skin 皮肤对象
     */
    @Override
    protected Skin<?> createDefaultSkin() {
        return new DecoratorAnimatedPageSkin<>(this);
    }

    /**
     * @description: 装饰器动画页面皮肤实现类
     * @param <T> 页面类型
     */
    public static class DecoratorAnimatedPageSkin<T extends DecoratorAnimatedPage> extends SkinBase<T> {

        /**
         * @description: 构造函数，创建页面布局
         * @param control 控制器
         */
        protected DecoratorAnimatedPageSkin(T control) {
            super(control);

            BorderPane pane = new BorderPane();
            pane.setLeft(control.left);

            // 移除硬编码的宽度设置，使用默认宽度200像素
            // 让DecoratorController统一管理左侧栏宽度
            FXUtils.setLimitWidth(control.left, 200);

            pane.setCenter(control.center);
            getChildren().setAll(pane);
        }

        /**
         * @description: 设置左侧栏内容
         * @param children 子节点数组
         */
        protected void setLeft(Node... children) {
            getSkinnable().setLeft(children);
        }

        /**
         * @description: 设置中心内容
         * @param children 子节点数组
         */
        protected void setCenter(Node... children) {
            getSkinnable().setCenter(children);
        }
    }
}