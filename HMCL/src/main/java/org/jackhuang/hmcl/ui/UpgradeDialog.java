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
package org.jackhuang.hmcl.ui;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialogLayout;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.upgrade.RemoteVersion;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jackhuang.hmcl.Metadata.CHANGELOG_URL;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @description: 升级对话框类，用于显示更新日志并处理用户的升级操作
 */
public final class UpgradeDialog extends JFXDialogLayout {

    /**
     * @description: 升级对话框构造函数
     * @param remoteVersion 远程版本信息对象
     * @param updateRunnable 执行更新操作的可运行对象
     */
    public UpgradeDialog(RemoteVersion remoteVersion, Runnable updateRunnable) {
        // 设置对话框的最大宽高，根据场景窗口大小动态调整
        maxWidthProperty().bind(Controllers.getScene().widthProperty().multiply(0.7));
        maxHeightProperty().bind(Controllers.getScene().heightProperty().multiply(0.7));

        // 设置对话框标题
        setHeading(new Label(i18n("update.changelog")));

        // 初始显示进度指示器，表示正在加载更新日志
        setBody(new ProgressIndicator());

        // 获取更新日志的URL地址
        String url = CHANGELOG_URL;

        // 异步任务：获取并解析JSON格式的更新日志
        Task.supplyAsync(Schedulers.io(), () -> {
            try {
                // 获取JSON内容
                String jsonContent = fetchJsonContent(url);

                // 解析JSON获取updateDescription字段
                String updateDescription = parseUpdateDescription(jsonContent);

                if (updateDescription == null || updateDescription.trim().isEmpty()) {
                    throw new IOException("JSON中未找到有效的updateDescription字段");
                }

                // 创建文本显示组件
                return createTextFlow(updateDescription);

            } catch (Exception e) {
                LOG.warning("解析更新日志失败: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }).whenComplete(Schedulers.javafx(), (result, exception) -> {
            if (exception == null) {
                // 成功获取更新描述，创建滚动面板显示内容
                ScrollPane scrollPane = new ScrollPane(result);
                scrollPane.setFitToWidth(true);
                FXUtils.smoothScrolling(scrollPane);
                setBody(scrollPane);
            } else {
                // 获取失败时，尝试在浏览器中打开链接，并清空对话框内容
                LOG.warning("加载更新日志失败，尝试在浏览器中打开");
                FXUtils.openLink(url);
                setBody();
            }
        }).start();

        // 创建"在浏览器中查看"超链接
//        JFXHyperlink openInBrowser = new JFXHyperlink(i18n("web.view_in_browser"));
//        openInBrowser.setExternalLink(url);

        // 创建"接受更新"按钮
        JFXButton updateButton = new JFXButton(i18n("update.accept"));
        updateButton.getStyleClass().add("dialog-accept");
        updateButton.setOnAction(e -> updateRunnable.run());

        // 创建"取消"按钮
        JFXButton cancelButton = new JFXButton(i18n("button.cancel"));
        cancelButton.getStyleClass().add("dialog-cancel");
        cancelButton.setOnAction(e -> fireEvent(new DialogCloseEvent()));

        // 设置对话框底部的操作按钮
        setActions(
//                openInBrowser,
                updateButton
                //,cancelButton  // 根据需要可以取消注释
        );
        //onEscPressed(this, cancelButton::fire);  // 根据需要可以取消注释
    }

    /**
     * @description: 从指定URL获取JSON内容
     * @param url 目标URL地址
     * @return String JSON字符串内容
     * @throws IOException 网络请求异常或读取异常
     */
    private String fetchJsonContent(String url) throws IOException {
        HttpURLConnection connection = null;
        BufferedReader reader = null;

        try {
            // 建立HTTP连接
            URL urlObject = new URL(url);
            connection = (HttpURLConnection) urlObject.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(30000); // 30秒连接超时
            connection.setReadTimeout(30000);    // 30秒读取超时
            connection.setRequestProperty("User-Agent", "HMCL");

            // 检查响应状态码
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                throw new IOException("HTTP请求失败，状态码: " + responseCode);
            }

            // 读取响应内容
            reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8));

            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }

            return content.toString();

        } finally {
            // 确保资源正确关闭
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    LOG.warning("关闭读取器失败: " + e.getMessage());
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * @description: 从JSON字符串中解析updateDescription字段
     * @param jsonContent JSON字符串内容
     * @return String updateDescription字段的值，如果未找到则返回null
     */
    private String parseUpdateDescription(String jsonContent) {
        if (jsonContent == null || jsonContent.trim().isEmpty()) {
            return null;
        }

        try {
            // 使用正则表达式提取updateDescription字段
            // 这里使用简单的正则匹配，避免引入复杂的JSON解析库依赖
            Pattern pattern = Pattern.compile("\"updateDescription\"\\s*:\\s*\"([^\"]*(?:\\\\.[^\"]*)*)\"");
            Matcher matcher = pattern.matcher(jsonContent);

            if (matcher.find()) {
                String description = matcher.group(1);
                // 处理JSON转义字符
                return unescapeJsonString(description);
            }

            // 如果上面的模式没有匹配到，尝试处理可能的多行字符串情况
            Pattern multiLinePattern = Pattern.compile("\"updateDescription\"\\s*:\\s*\"([^\"]*(?:\\\\.[^\"]*)*|[\\s\\S]*?)\"");
            Matcher multiLineMatcher = multiLinePattern.matcher(jsonContent);

            if (multiLineMatcher.find()) {
                String description = multiLineMatcher.group(1);
                return unescapeJsonString(description);
            }

            return null;

        } catch (Exception e) {
            LOG.warning("解析updateDescription字段时出错: " + e.getMessage());
            return null;
        }
    }

    /**
     * @description: 处理JSON字符串中的转义字符
     * @param jsonString 包含转义字符的JSON字符串
     * @return String 处理后的正常字符串
     */
    private String unescapeJsonString(String jsonString) {
        if (jsonString == null) {
            return null;
        }

        return jsonString
                .replace("\\\"", "\"")     // 处理引号转义
                .replace("\\\\", "\\")     // 处理反斜杠转义
                .replace("\\n", "\n")      // 处理换行符转义
                .replace("\\r", "\r")      // 处理回车符转义
                .replace("\\t", "\t")      // 处理制表符转义
                .replace("\\/", "/");      // 处理斜杠转义
    }

    /**
     * @description: 根据文本内容创建TextFlow组件用于显示
     * @param content 要显示的文本内容
     * @return TextFlow 可显示的文本流组件
     */
    private TextFlow createTextFlow(String content) {
        TextFlow textFlow = new TextFlow();

        // 处理文本内容，保持原有格式
        String[] lines = content.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            // 创建文本节点
            Text text = new Text(line);
            text.getStyleClass().add("changelog-text");
            textFlow.getChildren().add(text);

            // 如果不是最后一行，添加换行
            if (i < lines.length - 1) {
                textFlow.getChildren().add(new Text("\n"));
            }
        }

        return textFlow;
    }
}