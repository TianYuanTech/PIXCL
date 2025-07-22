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
package org.jackhuang.hmcl.game;

import com.jfoenix.controls.JFXButton;
import javafx.stage.Stage;
import mcpatch.McPatchClient;
import mcpatch.callback.ProgressCallback;
import org.jackhuang.hmcl.Launcher;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.AuthInfo;
import org.jackhuang.hmcl.auth.AuthenticationException;
import org.jackhuang.hmcl.auth.CredentialExpiredException;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.download.MaintainTask;
import org.jackhuang.hmcl.download.game.GameLibrariesTask;
import org.jackhuang.hmcl.download.game.GameVerificationFixTask;
import org.jackhuang.hmcl.download.game.LibraryDownloadException;
import org.jackhuang.hmcl.download.game.LibraryDownloadTask;
import org.jackhuang.hmcl.java.JavaManager;
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.launch.NotDecompressingNativesException;
import org.jackhuang.hmcl.launch.PermissionException;
import org.jackhuang.hmcl.launch.ProcessCreationException;
import org.jackhuang.hmcl.launch.ProcessListener;
import org.jackhuang.hmcl.mod.ModpackCompletionException;
import org.jackhuang.hmcl.mod.ModpackConfiguration;
import org.jackhuang.hmcl.mod.ModpackProvider;
import org.jackhuang.hmcl.setting.*;
import org.jackhuang.hmcl.task.*;
import org.jackhuang.hmcl.ui.*;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane.MessageType;
import org.jackhuang.hmcl.ui.construct.TaskExecutorDialogPane;
import org.jackhuang.hmcl.util.*;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.ResponseCodeException;
import org.jackhuang.hmcl.util.platform.*;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jackhuang.hmcl.util.versioning.VersionNumber;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static javafx.application.Platform.runLater;
import static javafx.application.Platform.setImplicitExit;
import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.util.DataSizeUnit.MEGABYTES;
import static org.jackhuang.hmcl.util.Lang.resolveException;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;
import static org.jackhuang.hmcl.util.platform.Platform.SYSTEM_PLATFORM;
import static org.jackhuang.hmcl.util.platform.Platform.isCompatibleWithX86Java;

public final class LauncherHelper {

    private final Profile profile;
    private Account account;
    private final String selectedVersion;
    private File scriptFile;
    private final VersionSetting setting;
    private LauncherVisibility launcherVisibility;
    private boolean showLogs;

    public LauncherHelper(Profile profile, Account account, String selectedVersion) {
        this.profile = Objects.requireNonNull(profile);
        this.account = Objects.requireNonNull(account);
        this.selectedVersion = Objects.requireNonNull(selectedVersion);
        this.setting = profile.getVersionSetting(selectedVersion);
        this.launcherVisibility = setting.getLauncherVisibility();
        this.showLogs = setting.isShowLogs();
        this.launchingStepsPane.setTitle(i18n("version.launch"));
    }

    private final TaskExecutorDialogPane launchingStepsPane = new TaskExecutorDialogPane(TaskCancellationAction.NORMAL);

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

    public void setTestMode() {
        launcherVisibility = LauncherVisibility.KEEP;
        showLogs = true;
    }

    public void setKeep() {
        launcherVisibility = LauncherVisibility.KEEP;
    }

    public void launch() {
        FXUtils.checkFxUserThread();

        LOG.info("Launching game version: " + selectedVersion);

        Controllers.dialog(launchingStepsPane);
        launch0();
    }

    public void makeLaunchScript(File scriptFile) {
        this.scriptFile = Objects.requireNonNull(scriptFile);

        launch();
    }

    /**
     * @description: 启动流程的核心方法，将McPatchClient文件更新检查集成到任务链开始位置
     */
    private void launch0() {
        HMCLGameRepository repository = profile.getRepository();
        DefaultDependencyManager dependencyManager = profile.getDependency();
        AtomicReference<Version> version = new AtomicReference<>(MaintainTask.maintain(repository, repository.getResolvedVersion(selectedVersion)));
        Optional<String> gameVersion = repository.getGameVersion(version.get());
        boolean integrityCheck = repository.unmarkVersionLaunchedAbnormally(selectedVersion);
        CountDownLatch launchingLatch = new CountDownLatch(1);
        List<String> javaAgents = new ArrayList<>(0);
        List<String> javaArguments = new ArrayList<>(0);

        AtomicReference<JavaRuntime> javaVersionRef = new AtomicReference<>();

        // 创建完整的任务链，McPatch任务位于开始位置
        TaskExecutor executor = createMcPatchTask()
                .thenComposeAsync(() -> checkGameState(profile, setting, version.get()))
                .thenComposeAsync(java -> {
                    javaVersionRef.set(Objects.requireNonNull(java));
                    version.set(NativePatcher.patchNative(repository, version.get(), gameVersion.orElse(null), java, setting, javaArguments));
                    if (setting.isNotCheckGame())
                        return null;
                    return Task.allOf(
                            dependencyManager.checkGameCompletionAsync(version.get(), integrityCheck),
                            Task.composeAsync(() -> {
                                try {
                                    ModpackConfiguration<?> configuration = ModpackHelper.readModpackConfiguration(repository.getModpackConfiguration(selectedVersion));
                                    ModpackProvider provider = ModpackHelper.getProviderByType(configuration.getType());
                                    if (provider == null) return null;
                                    else return provider.createCompletionTask(dependencyManager, selectedVersion);
                                } catch (IOException e) {
                                    return null;
                                }
                            }),
                            Task.composeAsync(() -> {
                                Renderer renderer = setting.getRenderer();
                                if (renderer != Renderer.DEFAULT && OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
                                    Library lib = NativePatcher.getMesaLoader(java);
                                    if (lib == null)
                                        return null;
                                    File file = dependencyManager.getGameRepository().getLibraryFile(version.get(), lib);
                                    if (file.getAbsolutePath().indexOf('=') >= 0) {
                                        LOG.warning("Invalid character '=' in the libraries directory path, unable to attach software renderer loader");
                                        return null;
                                    }

                                    String agent = file.getAbsolutePath() + "=" + renderer.name().toLowerCase(Locale.ROOT);

                                    if (GameLibrariesTask.shouldDownloadLibrary(repository, version.get(), lib, integrityCheck)) {
                                        return new LibraryDownloadTask(dependencyManager, file, lib)
                                                .thenRunAsync(() -> javaAgents.add(agent));
                                    } else {
                                        javaAgents.add(agent);
                                        return null;
                                    }
                                } else {
                                    return null;
                                }
                            })
                    );
                }).withStage("launch.state.dependencies")
                .thenComposeAsync(() -> gameVersion.map(s -> new GameVerificationFixTask(dependencyManager, s, version.get())).orElse(null))
                .thenComposeAsync(() -> logIn(account).withStage("launch.state.logging_in"))
                .thenComposeAsync(authInfo -> Task.supplyAsync(() -> {
                    LaunchOptions launchOptions = repository.getLaunchOptions(
                            selectedVersion, javaVersionRef.get(), profile.getGameDir(), javaAgents, javaArguments, scriptFile != null);

                    // 更新PixelLiveGame.json配置
                    updatePixelLiveGameConfig(authInfo, launchOptions);

                    LOG.info("Here's the structure of game mod directory:\n" + FileUtils.printFileStructure(repository.getModManager(selectedVersion).getModsDirectory(), 10));

                    return new HMCLGameLauncher(
                            repository,
                            version.get(),
                            authInfo,
                            launchOptions,
                            launcherVisibility == LauncherVisibility.CLOSE
                                    ? null
                                    : new HMCLProcessListener(repository, version.get(), authInfo, launchOptions, launchingLatch, gameVersion.isPresent())
                    );
                }).thenComposeAsync(launcher -> {
                    if (scriptFile == null) {
                        return Task.supplyAsync(launcher::launch);
                    } else {
                        return Task.supplyAsync(() -> {
                            launcher.makeLaunchScript(scriptFile);
                            return null;
                        });
                    }
                }).thenAcceptAsync(process -> {
                    if (scriptFile == null) {
                        PROCESSES.add(process);
                        if (launcherVisibility == LauncherVisibility.CLOSE)
                            Launcher.stopApplication();
                        else
                            launchingStepsPane.setCancel(new TaskCancellationAction(it -> {
                                process.stop();
                                it.fireEvent(new DialogCloseEvent());
                            }));
                    } else {
                        runLater(() -> {
                            launchingStepsPane.fireEvent(new DialogCloseEvent());
                            Controllers.dialog(i18n("version.launch_script.success", scriptFile.getAbsolutePath()));
                        });
                    }
                }).withFakeProgress(
                        i18n("message.doing"),
                        () -> launchingLatch.getCount() == 0, 6.95
                ).withStage("launch.state.waiting_launching"))
                .withStagesHint(Lang.immutableListOf(
                        "launch.state.files_updating",    // 文件更新检查阶段
                        "launch.state.java",              // Java环境检查
                        "launch.state.dependencies",      // 依赖处理
                        "launch.state.logging_in",        // 登录验证
                        "launch.state.waiting_launching")) // 等待启动
                .executor();

        launchingStepsPane.setExecutor(executor, false);
        executor.addTaskListener(new TaskListener() {
            @Override
            public void onStop(boolean success, TaskExecutor executor) {
                runLater(() -> {
                    if (!Controllers.isStopped()) {
                        launchingStepsPane.fireEvent(new DialogCloseEvent());
                        if (!success) {
                            Exception ex = executor.getException();
                            if (ex != null && !(ex instanceof CancellationException)) {
                                handleLaunchError(ex);
                            }
                        }
                    }
                    launchingStepsPane.setExecutor(null);
                });
            }
        });

        executor.start();
    }

    /**
     * @description: 创建支持取消操作的McPatch文件更新任务
     */
    private Task<Void> createMcPatchTask() {
        return new McPatchTask();
    }

    /**
     * @description: 支持取消操作的McPatch任务实现
     */
    private class McPatchTask extends Task<Void> {

        private volatile Thread mcPatchThread;
        private volatile boolean shouldCancel = false;

        public McPatchTask() {
            setStage("launch.state.files_updating");
            setName(i18n("mcpatch.task.name"));
            setSignificance(TaskSignificance.MAJOR);
        }

        @Override
        public void execute() throws Exception {
            try {
                LOG.info("开始文件更新检查");
                updateMessage(i18n("mcpatch.connecting"));
                updateProgress(0.0);

                // 检查是否已被取消
                if (isCancelled() || shouldCancel) {
                    LOG.info("McPatch任务已被取消");
                    return;
                }

                // 创建进度回调
                McPatchProgressCallback progressCallback = new McPatchProgressCallback(this);

                // 在单独线程中执行McPatch，便于中断控制
                CompletableFuture<Boolean> mcPatchFuture = CompletableFuture.supplyAsync(() -> {
                    mcPatchThread = Thread.currentThread();
                    try {
                        return McPatchClient.modloaderWithProgress(true, true, progressCallback);
                    } catch (Exception e) {
                        if (Thread.currentThread().isInterrupted() || shouldCancel) {
                            LOG.info("McPatch执行被中断");
                            return false;
                        }
                        throw new RuntimeException(e);
                    }
                });

                // 等待执行完成，定期检查取消状态
                boolean hasUpdates = false;
                while (!mcPatchFuture.isDone()) {
                    if (isCancelled() || shouldCancel) {
                        LOG.info("检测到取消请求，正在中断McPatch任务");
                        shouldCancel = true;

                        // 中断McPatch线程
                        if (mcPatchThread != null) {
                            mcPatchThread.interrupt();
                        }

                        // 等待最多3秒让任务优雅退出
                        try {
                            hasUpdates = mcPatchFuture.get(3, TimeUnit.SECONDS);
                        } catch (TimeoutException e) {
                            LOG.warning("McPatch任务未能在3秒内响应中断，强制取消");
                            mcPatchFuture.cancel(true);
                        } catch (CancellationException | InterruptedException e) {
                            LOG.info("McPatch任务已被成功取消");
                        }

                        updateMessage(i18n("mcpatch.cancelled"));
                        return;
                    }

                    Thread.sleep(100); // 短暂等待
                }

                hasUpdates = mcPatchFuture.get();
                updateProgress(1.0);
                String resultMessage = hasUpdates ? i18n("mcpatch.completed") : i18n("mcpatch.up_to_date");
                updateMessage(resultMessage);

                LOG.info(resultMessage + (hasUpdates ? "，发现并应用了更新" : ""));

            } catch (CancellationException | InterruptedException e) {
                LOG.info("McPatch任务被用户取消");
                updateMessage(i18n("mcpatch.cancelled"));
                // 不重新抛出异常，让任务优雅结束
            } catch (Exception e) {
                if (shouldCancel || Thread.currentThread().isInterrupted()) {
                    LOG.info("McPatch任务在取消过程中发生异常: " + e.getMessage());
                    updateMessage(i18n("mcpatch.cancelled"));
                } else {
                    LOG.warning("文件更新过程中发生错误: " + e.getMessage(), e);
                    updateMessage(i18n("mcpatch.failed"));
                    throw e;
                }
            }
        }

        @Override
        public boolean isCancelled() {
            return super.isCancelled() || shouldCancel;
        }

        @Override
        public Collection<Task<?>> getDependents() {
            return Collections.emptySet();
        }

        @Override
        public Collection<Task<?>> getDependencies() {
            return Collections.emptySet();
        }
    }

    /**
     * @description: 支持取消检测的McPatch进度回调实现
     */
    private class McPatchProgressCallback implements ProgressCallback {

        private final McPatchTask task;

        public McPatchProgressCallback(McPatchTask task) {
            this.task = task;
        }

        @Override
        public void updateTitle(String title) {
            javafx.application.Platform.runLater(() -> {
                if (!task.isCancelled()) {
                    LOG.info("McPatch标题: " + title);
                    task.updateMessage(title);
                }
            });
        }

        @Override
        public void updateLabel(String label) {
            javafx.application.Platform.runLater(() -> {
                if (!task.isCancelled()) {
                    LOG.info("McPatch状态: " + label);
                    task.updateMessage(label);
                }
            });
        }

        @Override
        public void updateProgress(String text, int value) {
            javafx.application.Platform.runLater(() -> {
                if (!task.isCancelled()) {
                    double progress = value / 1000.0;
                    String displayText = parseProgressText(text);

                    task.updateMessage(displayText);
                    task.updateProgressImmediately(progress);

                    // 触发速度事件更新左下角显示
                    String speedText = extractSpeedFromText(text);
                    if (!speedText.isEmpty()) {
                        triggerSpeedEvent(speedText);
                    }
                }
            });
        }

        @Override
        public boolean shouldInterrupt() {
            return task.isCancelled() || Thread.currentThread().isInterrupted();
        }

        @Override
        public void showCompletionMessage(boolean hasUpdates) {
            String message = hasUpdates ? i18n("mcpatch.completed") : i18n("mcpatch.up_to_date");
            javafx.application.Platform.runLater(() -> {
                if (!task.isCancelled()) {
                    LOG.info(message);
                    task.updateMessage(message);
                    triggerSpeedEvent(""); // 清空速度显示
                }
            });
        }

        /**
         * @description: 解析进度文本，提取有用的显示内容
         */
        private String parseProgressText(String text) {
            if (text == null || text.isEmpty()) {
                return i18n("mcpatch.processing");
            }

            String[] parts = text.split("\\s+-\\s+");
            if (parts.length >= 2) {
                return parts[0] + " - " + parts[1];
            }

            return text.length() > 50 ? text.substring(0, 47) + "..." : text;
        }

        /**
         * @description: 从进度文本中提取速度信息
         */
        private String extractSpeedFromText(String text) {
            if (text == null || !text.contains("/s")) {
                return "";
            }

            String[] parts = text.split("\\s+-\\s+");
            for (String part : parts) {
                if (part.trim().endsWith("/s")) {
                    return part.trim();
                }
            }
            return "";
        }

        /**
         * @description: 触发速度事件以更新左下角显示
         */
        private void triggerSpeedEvent(String speedText) {
            try {
                int speedValue = parseSpeedValue(speedText);
                FileDownloadTask.speedEvent.channel(FileDownloadTask.SpeedEvent.class)
                        .fireEvent(new FileDownloadTask.SpeedEvent(this, speedValue));
            } catch (Exception e) {
                LOG.debug("触发速度事件时发生错误: " + e.getMessage());
            }
        }

        /**
         * @description: 解析速度文本为字节数值
         */
        private int parseSpeedValue(String speedText) {
            if (speedText == null || speedText.isEmpty()) {
                return 0;
            }

            try {
                String cleanText = speedText.replace("/s", "").trim().toLowerCase();

                if (cleanText.endsWith("mib") || cleanText.endsWith("mb")) {
                    double value = Double.parseDouble(cleanText.replaceAll("(mib|mb)", ""));
                    return (int) (value * 1024 * 1024);
                } else if (cleanText.endsWith("kib") || cleanText.endsWith("kb")) {
                    double value = Double.parseDouble(cleanText.replaceAll("(kib|kb)", ""));
                    return (int) (value * 1024);
                } else if (cleanText.endsWith("b")) {
                    return (int) Double.parseDouble(cleanText.replace("b", ""));
                }
            } catch (NumberFormatException e) {
                LOG.debug("解析速度值失败: " + speedText);
            }

            return 0;
        }
    }

    /**
     * @description: 更新PixelLiveGame.json配置文件
     * @param authInfo - 认证信息对象
     * @param launchOptions - 启动选项对象
     */
    private void updatePixelLiveGameConfig(AuthInfo authInfo, LaunchOptions launchOptions) {
        try {
            // 获取游戏目录
            File gameDir = profile.getGameDir();

            // 更新配置文件
            PixelLiveGameConfig.updatePixelLiveGameConfig(account, gameDir);

            LOG.info("Successfully updated PixelLiveGame.json for user: " + authInfo.getUsername());
        } catch (Exception e) {
            LOG.warning("Failed to update PixelLiveGame.json", e);
            // 这里不抛出异常，避免影响游戏启动
        }
    }

    /**
     * @description: 处理启动错误的方法
     * @param ex - 异常对象
     */
    private void handleLaunchError(Exception ex) {
        String message;
        if (ex instanceof ModpackCompletionException) {
            if (ex.getCause() instanceof FileNotFoundException)
                message = i18n("modpack.type.curse.not_found");
            else
                message = i18n("modpack.type.curse.error");
        } else if (ex instanceof PermissionException) {
            message = i18n("launch.failed.executable_permission");
        } else if (ex instanceof ProcessCreationException) {
            message = i18n("launch.failed.creating_process") + "\n" + ex.getLocalizedMessage();
        } else if (ex instanceof NotDecompressingNativesException) {
            message = i18n("launch.failed.decompressing_natives") + "\n" + ex.getLocalizedMessage();
        } else if (ex instanceof LibraryDownloadException) {
            message = i18n("launch.failed.download_library", ((LibraryDownloadException) ex).getLibrary().getName()) + "\n";
            if (ex.getCause() instanceof ResponseCodeException) {
                ResponseCodeException rce = (ResponseCodeException) ex.getCause();
                int responseCode = rce.getResponseCode();
                URL url = rce.getUrl();
                if (responseCode == 404)
                    message += i18n("download.code.404", url);
                else
                    message += i18n("download.failed", url, responseCode);
            } else {
                message += StringUtils.getStackTrace(ex.getCause());
            }
        } else {
            message = StringUtils.getStackTrace(ex);
        }

        Controllers.dialog(message,
                scriptFile == null ? i18n("launch.failed") : i18n("version.launch_script.failed"),
                MessageType.ERROR);
    }

    private static Task<JavaRuntime> checkGameState(Profile profile, VersionSetting setting, Version version) {
        LibraryAnalyzer analyzer = LibraryAnalyzer.analyze(version, profile.getRepository().getGameVersion(version).orElse(null));
        GameVersionNumber gameVersion = GameVersionNumber.asGameVersion(analyzer.getVersion(LibraryAnalyzer.LibraryType.MINECRAFT));

        Task<JavaRuntime> getJavaTask = Task.supplyAsync(() -> {
            try {
                return setting.getJava(gameVersion, version);
            } catch (InterruptedException e) {
                throw new CancellationException();
            }
        });
        Task<JavaRuntime> task;
        if (setting.isNotCheckJVM()) {
            task = getJavaTask.thenApplyAsync(java -> Lang.requireNonNullElse(java, JavaRuntime.getDefault()));
        } else if (setting.getJavaVersionType() == JavaVersionType.AUTO || setting.getJavaVersionType() == JavaVersionType.VERSION) {
            task = getJavaTask.thenComposeAsync(Schedulers.javafx(), java -> {
                if (java != null) {
                    return Task.completed(java);
                }

                // Reset invalid java version
                CompletableFuture<JavaRuntime> future = new CompletableFuture<>();
                Task<JavaRuntime> result = Task.fromCompletableFuture(future);
                Runnable breakAction = () -> future.completeExceptionally(new CancellationException("No accepted java"));
                List<GameJavaVersion> supportedVersions = GameJavaVersion.getSupportedVersions(SYSTEM_PLATFORM);

                GameJavaVersion targetJavaVersion = null;
                if (setting.getJavaVersionType() == JavaVersionType.VERSION) {
                    try {
                        int targetJavaVersionMajor = Integer.parseInt(setting.getJavaVersion());
                        GameJavaVersion minimumJavaVersion = GameJavaVersion.getMinimumJavaVersion(gameVersion);

                        if (minimumJavaVersion != null && targetJavaVersionMajor < minimumJavaVersion.getMajorVersion()) {
                            Controllers.dialog(
                                    i18n("launch.failed.java_version_too_low"),
                                    i18n("message.error"),
                                    MessageType.ERROR,
                                    breakAction
                            );
                            return result;
                        }

                        targetJavaVersion = GameJavaVersion.get(targetJavaVersionMajor);
                    } catch (NumberFormatException ignored) {
                    }
                } else
                    targetJavaVersion = version.getJavaVersion();

                if (targetJavaVersion != null && supportedVersions.contains(targetJavaVersion)) {
                    downloadJava(targetJavaVersion, profile)
                            .whenCompleteAsync((downloadedJava, exception) -> {
                                if (exception == null) {
                                    future.complete(downloadedJava);
                                } else {
                                    LOG.warning("Failed to download java", exception);
                                    Controllers.confirm(i18n("launch.failed.no_accepted_java"), i18n("message.warning"), MessageType.WARNING,
                                            () -> future.complete(JavaRuntime.getDefault()),
                                            breakAction);
                                }
                            }, Schedulers.javafx());
                } else {
                    Controllers.confirm(i18n("launch.failed.no_accepted_java"), i18n("message.warning"), MessageType.WARNING,
                            () -> future.complete(JavaRuntime.getDefault()),
                            breakAction);
                }

                return result;
            });
        } else {
            task = getJavaTask.thenComposeAsync(java -> {
                Set<JavaVersionConstraint> violatedMandatoryConstraints = EnumSet.noneOf(JavaVersionConstraint.class);
                Set<JavaVersionConstraint> violatedSuggestedConstraints = EnumSet.noneOf(JavaVersionConstraint.class);

                if (java != null) {
                    for (JavaVersionConstraint constraint : JavaVersionConstraint.ALL) {
                        if (constraint.appliesToVersion(gameVersion, version, java, analyzer)) {
                            if (!constraint.checkJava(gameVersion, version, java)) {
                                if (constraint.isMandatory()) {
                                    violatedMandatoryConstraints.add(constraint);
                                } else {
                                    violatedSuggestedConstraints.add(constraint);
                                }
                            }
                        }
                    }
                }

                CompletableFuture<JavaRuntime> future = new CompletableFuture<>();
                Task<JavaRuntime> result = Task.fromCompletableFuture(future);
                Runnable breakAction = () -> future.completeExceptionally(new CancellationException("Launch operation was cancelled by user"));

                if (java == null || !violatedMandatoryConstraints.isEmpty()) {
                    JavaRuntime suggestedJava = JavaManager.findSuitableJava(gameVersion, version);
                    if (suggestedJava != null) {
                        FXUtils.runInFX(() -> {
                            Controllers.confirm(i18n("launch.advice.java.auto"), i18n("message.warning"), () -> {
                                setting.setJavaAutoSelected();
                                future.complete(suggestedJava);
                            }, breakAction);
                        });
                        return result;
                    } else if (java == null) {
                        FXUtils.runInFX(() -> Controllers.dialog(
                                i18n("launch.invalid_java"),
                                i18n("message.error"),
                                MessageType.ERROR,
                                breakAction
                        ));
                        return result;
                    } else {
                        GameJavaVersion gameJavaVersion;
                        if (violatedMandatoryConstraints.contains(JavaVersionConstraint.GAME_JSON))
                            gameJavaVersion = version.getJavaVersion();
                        else if (violatedMandatoryConstraints.contains(JavaVersionConstraint.VANILLA))
                            gameJavaVersion = GameJavaVersion.getMinimumJavaVersion(gameVersion);
                        else
                            gameJavaVersion = null;

                        if (gameJavaVersion != null) {
                            FXUtils.runInFX(() -> downloadJava(gameJavaVersion, profile).whenCompleteAsync((downloadedJava, throwable) -> {
                                if (throwable == null) {
                                    setting.setJavaAutoSelected();
                                    future.complete(downloadedJava);
                                } else {
                                    LOG.warning("Failed to download java", throwable);
                                    breakAction.run();
                                }
                            }, Schedulers.javafx()));
                            return result;
                        }

                        if (violatedMandatoryConstraints.contains(JavaVersionConstraint.VANILLA_LINUX_JAVA_8)) {
                            if (setting.getNativesDirType() == NativesDirectoryType.VERSION_FOLDER) {
                                FXUtils.runInFX(() -> Controllers.dialog(i18n("launch.advice.vanilla_linux_java_8"), i18n("message.error"), MessageType.ERROR, breakAction));
                                return result;
                            } else {
                                violatedMandatoryConstraints.remove(JavaVersionConstraint.VANILLA_LINUX_JAVA_8);
                            }
                        }

                        if (violatedMandatoryConstraints.contains(JavaVersionConstraint.LAUNCH_WRAPPER)) {
                            FXUtils.runInFX(() -> Controllers.dialog(
                                    i18n("launch.advice.java9") + "\n" + i18n("launch.advice.uncorrected"),
                                    i18n("message.error"),
                                    MessageType.ERROR,
                                    breakAction
                            ));
                            return result;
                        }

                        if (!violatedMandatoryConstraints.isEmpty()) {
                            FXUtils.runInFX(() -> Controllers.dialog(
                                    i18n("launch.advice.unknown") + "\n" + violatedMandatoryConstraints,
                                    i18n("message.error"),
                                    MessageType.ERROR,
                                    breakAction
                            ));
                            return result;
                        }
                    }
                }

                List<String> suggestions = new ArrayList<>();

                if (Architecture.SYSTEM_ARCH == Architecture.X86_64 && java.getPlatform().getArchitecture() == Architecture.X86) {
                    suggestions.add(i18n("launch.advice.different_platform"));
                }

                // 32-bit JVM cannot make use of too much memory.
                if (java.getBits() == Bits.BIT_32 && setting.getMaxMemory() > 1.5 * 1024) {
                    // 1.5 * 1024 is an inaccurate number.
                    // Actual memory limit depends on operating system and memory.
                    suggestions.add(i18n("launch.advice.too_large_memory_for_32bit"));
                }

                for (JavaVersionConstraint violatedSuggestedConstraint : violatedSuggestedConstraints) {
                    switch (violatedSuggestedConstraint) {
                        case MODDED_JAVA_7:
                            suggestions.add(i18n("launch.advice.java.modded_java_7"));
                            break;
                        case MODDED_JAVA_8:
                            // Minecraft>=1.7.10+Forge accepts Java 8
                            if (java.getParsedVersion() < 8)
                                suggestions.add(i18n("launch.advice.newer_java"));
                            else
                                suggestions.add(i18n("launch.advice.modded_java", 8, gameVersion));
                            break;
                        case MODDED_JAVA_16:
                            // Minecraft<=1.17.1+Forge[37.0.0,37.0.60) not compatible with Java 17
                            String forgePatchVersion = analyzer.getVersion(LibraryAnalyzer.LibraryType.FORGE).orElse(null);
                            if (forgePatchVersion != null && VersionNumber.compare(forgePatchVersion, "37.0.60") < 0)
                                suggestions.add(i18n("launch.advice.forge37_0_60"));
                            else
                                suggestions.add(i18n("launch.advice.modded_java", 16, gameVersion));
                            break;
                        case MODDED_JAVA_17:
                            suggestions.add(i18n("launch.advice.modded_java", 17, gameVersion));
                            break;
                        case MODDED_JAVA_21:
                            suggestions.add(i18n("launch.advice.modded_java", 21, gameVersion));
                            break;
                        case VANILLA_JAVA_8_51:
                            suggestions.add(i18n("launch.advice.java8_51_1_13"));
                            break;
                        case MODLAUNCHER_8:
                            suggestions.add(i18n("launch.advice.modlauncher8"));
                            break;
                        case VANILLA_X86:
                            if (setting.getNativesDirType() == NativesDirectoryType.VERSION_FOLDER
                                    && isCompatibleWithX86Java()) {
                                suggestions.add(i18n("launch.advice.vanilla_x86.translation"));
                            }
                            break;
                        default:
                            suggestions.add(violatedSuggestedConstraint.name());
                    }
                }

                // Cannot allocate too much memory exceeding free space.
                long totalMemorySizeMB = (long) MEGABYTES.convertFromBytes(SystemInfo.getTotalMemorySize());
                if (totalMemorySizeMB > 0 && totalMemorySizeMB < setting.getMaxMemory()) {
                    suggestions.add(i18n("launch.advice.not_enough_space", totalMemorySizeMB));
                }

                VersionNumber forgeVersion = analyzer.getVersion(LibraryAnalyzer.LibraryType.FORGE)
                        .map(VersionNumber::asVersion)
                        .orElse(null);

                // Forge 2760~2773 will crash game with LiteLoader.
                boolean hasForge2760 = forgeVersion != null && (forgeVersion.compareTo("1.12.2-14.23.5.2760") >= 0) && (forgeVersion.compareTo("1.12.2-14.23.5.2773") < 0);
                boolean hasLiteLoader = version.getLibraries().stream().anyMatch(it -> it.is("com.mumfrey", "liteloader"));
                if (hasForge2760 && hasLiteLoader && gameVersion.compareTo("1.12.2") == 0) {
                    suggestions.add(i18n("launch.advice.forge2760_liteloader"));
                }

                // OptiFine 1.14.4 is not compatible with Forge 28.2.2 and later versions.
                boolean hasForge28_2_2 = forgeVersion != null && (forgeVersion.compareTo("1.14.4-28.2.2") >= 0);
                boolean hasOptiFine = version.getLibraries().stream().anyMatch(it -> it.is("optifine", "OptiFine"));
                if (hasForge28_2_2 && hasOptiFine && gameVersion.compareTo("1.14.4") == 0) {
                    suggestions.add(i18n("launch.advice.forge28_2_2_optifine"));
                }

                if (suggestions.isEmpty()) {
                    if (!future.isDone()) {
                        future.complete(java);
                    }
                } else {
                    String message;
                    if (suggestions.size() == 1) {
                        message = i18n("launch.advice", suggestions.get(0));
                    } else {
                        message = i18n("launch.advice.multi", suggestions.stream().map(it -> "→ " + it).collect(Collectors.joining("\n")));
                    }

                    FXUtils.runInFX(() -> Controllers.confirm(
                            message,
                            i18n("message.warning"),
                            MessageType.WARNING,
                            () -> future.complete(java),
                            breakAction));
                }

                return result;
            });
        }

        return task.withStage("launch.state.java");
    }

    private static CompletableFuture<JavaRuntime> downloadJava(GameJavaVersion javaVersion, Profile profile) {
        CompletableFuture<JavaRuntime> future = new CompletableFuture<>();
        Controllers.dialog(new MessageDialogPane.Builder(
                i18n("launch.advice.require_newer_java_version", javaVersion.getMajorVersion()),
                i18n("message.warning"),
                MessageType.QUESTION)
                .yesOrNo(() -> {
                    DownloadProvider downloadProvider = profile.getDependency().getDownloadProvider();
                    Controllers.taskDialog(JavaManager.getDownloadJavaTask(downloadProvider, SYSTEM_PLATFORM, javaVersion)
                            .whenComplete(Schedulers.javafx(), (result, exception) -> {
                                if (exception == null) {
                                    future.complete(result);
                                } else {
                                    Throwable resolvedException = resolveException(exception);
                                    LOG.warning("Failed to download java", exception);
                                    if (!(resolvedException instanceof CancellationException)) {
                                        Controllers.dialog(DownloadProviders.localizeErrorMessage(resolvedException), i18n("install.failed"));
                                    }
                                    future.completeExceptionally(new CancellationException());
                                }
                            }), i18n("download.java"), new TaskCancellationAction(() -> future.completeExceptionally(new CancellationException())));
                }, () -> future.completeExceptionally(new CancellationException())).build());

        return future;
    }

    private static Task<AuthInfo> logIn(Account account) {
        return Task.composeAsync(() -> {
            try {
                return Task.completed(account.logIn());
            } catch (CredentialExpiredException e) {
                LOG.info("Credential has expired", e);

                return Task.completed(DialogController.logIn(account));
            } catch (AuthenticationException e) {
                LOG.warning("Authentication failed, try skipping refresh", e);

                CompletableFuture<Task<AuthInfo>> future = new CompletableFuture<>();
                runInFX(() -> {
                    JFXButton loginOfflineButton = new JFXButton(i18n("account.login.skip"));
                    loginOfflineButton.setOnAction(event -> {
                        try {
                            future.complete(Task.completed(account.playOffline()));
                        } catch (AuthenticationException e2) {
                            future.completeExceptionally(e2);
                        }
                    });
                    JFXButton retryButton = new JFXButton(i18n("account.login.retry"));
                    retryButton.setOnAction(event -> {
                        future.complete(logIn(account));
                    });
                    Controllers.dialog(new MessageDialogPane.Builder(i18n("account.failed.server_disconnected"), i18n("account.failed"), MessageType.ERROR)
                            .addAction(loginOfflineButton)
                            .addAction(retryButton)
                            .addCancel(() ->
                                    future.completeExceptionally(new CancellationException()))
                            .build());
                });
                return Task.fromCompletableFuture(future).thenComposeAsync(task -> task);
            }
        });
    }

    private void checkExit() {
        switch (launcherVisibility) {
            case HIDE_AND_REOPEN:
                runLater(() -> {
                    Optional.ofNullable(Controllers.getStage())
                            .ifPresent(Stage::show);
                });
                break;
            case KEEP:
                // No operations here
                break;
            case CLOSE:
                throw new Error("Never get to here");
            case HIDE:
                runLater(() -> {
                    // Shut down the platform when user closed log window.
                    setImplicitExit(true);
                    // If we use Launcher.stop(), log window will be halt immediately.
                    Launcher.stopWithoutPlatform();
                });
                break;
        }
    }

    /**
     * The managed process listener.
     * Guarantee that one [JavaProcess], one [HMCLProcessListener].
     * Because every time we launched a game, we generates a new [HMCLProcessListener]
     */
    private final class HMCLProcessListener implements ProcessListener {

        private final HMCLGameRepository repository;
        private final Version version;
        private final LaunchOptions launchOptions;
        private ManagedProcess process;
        private volatile boolean lwjgl;
        private LogWindow logWindow;
        private final boolean detectWindow;
        private final CircularArrayList<Log> logs;
        private final CountDownLatch launchingLatch;
        private final String forbiddenAccessToken;
        private Thread submitLogThread;
        private LinkedBlockingQueue<Log> logBuffer;

        public HMCLProcessListener(HMCLGameRepository repository, Version version, AuthInfo authInfo, LaunchOptions launchOptions, CountDownLatch launchingLatch, boolean detectWindow) {
            this.repository = repository;
            this.version = version;
            this.launchOptions = launchOptions;
            this.launchingLatch = launchingLatch;
            this.detectWindow = detectWindow;
            this.forbiddenAccessToken = authInfo != null ? authInfo.getAccessToken() : null;
            this.logs = new CircularArrayList<>(Log.getLogLines() + 1);
        }

        @Override
        public void setProcess(ManagedProcess process) {
            this.process = process;

            String command = new CommandBuilder().addAll(process.getCommands()).toString();

            LOG.info("Launched process: " + command);

            String classpath = process.getClasspath();
            if (classpath != null) {
                LOG.info("Process ClassPath: " + classpath);
            }

            if (showLogs) {
                CountDownLatch logWindowLatch = new CountDownLatch(1);
                runLater(() -> {
                    logWindow = new LogWindow(process, logs);
                    logWindow.show();
                    logWindowLatch.countDown();
                });

                logBuffer = new LinkedBlockingQueue<>();
                submitLogThread = Lang.thread(new Runnable() {
                    private final ArrayList<Log> currentLogs = new ArrayList<>();
                    private final Semaphore semaphore = new Semaphore(0);

                    private void submitLogs() {
                        if (currentLogs.size() == 1) {
                            Log log = currentLogs.get(0);
                            runLater(() -> logWindow.logLine(log));
                        } else {
                            runLater(() -> {
                                logWindow.logLines(currentLogs);
                                semaphore.release();
                            });
                            semaphore.acquireUninterruptibly();
                        }
                        currentLogs.clear();
                    }

                    @Override
                    public void run() {
                        while (true) {
                            try {
                                currentLogs.add(logBuffer.take());
                                //noinspection BusyWait
                                Thread.sleep(200); // Wait for more logs
                            } catch (InterruptedException e) {
                                break;
                            }

                            logBuffer.drainTo(currentLogs);
                            submitLogs();
                        }

                        do {
                            submitLogs();
                        } while (logBuffer.drainTo(currentLogs) > 0);
                    }
                }, "Game Log Submitter", true);

                try {
                    logWindowLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        private void finishLaunch() {
            switch (launcherVisibility) {
                case HIDE_AND_REOPEN:
                    runLater(() -> {
                        // If application was stopped and execution services did not finish termination,
                        // these codes will be executed.
                        if (Controllers.getStage() != null) {
                            Controllers.getStage().hide();
                            launchingLatch.countDown();
                        }
                    });
                    break;
                case CLOSE:
                    // Never come to here.
                    break;
                case KEEP:
                    runLater(launchingLatch::countDown);
                    break;
                case HIDE:
                    launchingLatch.countDown();
                    runLater(() -> {
                        // If application was stopped and execution services did not finish termination,
                        // these codes will be executed.
                        if (Controllers.getStage() != null) {
                            Controllers.getStage().close();
                            Controllers.shutdown();
                            Schedulers.shutdown();
                            System.gc();
                        }
                    });
                    break;
            }
        }

        @Override
        public void onLog(String log, boolean isErrorStream) {
            if (isErrorStream)
                System.err.println(log);
            else
                System.out.println(log);

            log = StringUtils.parseEscapeSequence(log);
            if (forbiddenAccessToken != null)
                log = log.replace(forbiddenAccessToken, "<access token>");

            Log4jLevel level = isErrorStream && !log.startsWith("[authlib-injector]") ? Log4jLevel.ERROR : null;
            if (showLogs) {
                if (level == null)
                    level = Lang.requireNonNullElse(Log4jLevel.guessLevel(log), Log4jLevel.INFO);
                logBuffer.add(new Log(log, level));
            } else {
                synchronized (this) {
                    logs.addLast(new Log(log, level));
                    if (logs.size() > Log.getLogLines())
                        logs.removeFirst();
                }
            }

            if (!lwjgl) {
                String lowerCaseLog = log.toLowerCase(Locale.ROOT);
                if (!detectWindow || lowerCaseLog.contains("lwjgl version") || lowerCaseLog.contains("lwjgl openal")) {
                    synchronized (this) {
                        if (!lwjgl) {
                            lwjgl = true;
                            finishLaunch();
                        }
                    }
                }
            }
        }

        @Override
        public void onExit(int exitCode, ExitType exitType) {
            if (showLogs) {
                logBuffer.add(new Log(String.format("[HMCL ProcessListener] Minecraft exit with code %d(0x%x), type is %s.", exitCode, exitCode, exitType), Log4jLevel.INFO));
                submitLogThread.interrupt();
                try {
                    submitLogThread.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            launchingLatch.countDown();

            if (exitType == ExitType.INTERRUPTED)
                return;

            // Game crashed before opening the game window.
            if (!lwjgl) {
                synchronized (this) {
                    if (!lwjgl)
                        finishLaunch();
                }
            }

            if (exitType != ExitType.NORMAL) {
                repository.markVersionLaunchedAbnormally(version.getId());
                runLater(() -> new GameCrashWindow(process, exitType, repository, version, launchOptions, logs).show());
            }

            checkExit();
        }
    }

    public static final Queue<ManagedProcess> PROCESSES = new ConcurrentLinkedQueue<>();

    public static void stopManagedProcesses() {
        while (!PROCESSES.isEmpty())
            Optional.ofNullable(PROCESSES.poll()).ifPresent(ManagedProcess::stop);
    }
}