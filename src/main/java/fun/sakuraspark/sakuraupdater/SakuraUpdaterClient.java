package fun.sakuraspark.sakuraupdater;

import org.slf4j.Logger;

import com.google.gson.Gson;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;

import fun.sakuraspark.sakuraupdater.config.ClientConfig;
import fun.sakuraspark.sakuraupdater.config.DataConfig.Data;
import fun.sakuraspark.sakuraupdater.config.DataConfig.FileData;
import fun.sakuraspark.sakuraupdater.config.DataConfig.PathData;
import fun.sakuraspark.sakuraupdater.gui.TestScreen;
import fun.sakuraspark.sakuraupdater.gui.UpdateCheckScreen;
import fun.sakuraspark.sakuraupdater.network.FileClient;
import fun.sakuraspark.sakuraupdater.utils.FileUtils;
import fun.sakuraspark.sakuraupdater.utils.MD5;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;

import static fun.sakuraspark.sakuraupdater.utils.CommandUtils.sendSuccessMessage;
import static net.minecraft.commands.Commands.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SakuraUpdaterClient {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static SakuraUpdaterClient INSTANCE;

    private FileClient file_client;
    private Data last_update_data = null; // 上次更新的数据

    private Pair<Integer, Integer> update_progress = new Pair<>(-1, -1); // 更新进度
    private int download_failures = 0; // 更新失败次数
    Pair<List<File>, List<FileData>> integrityCheckResult;

    private boolean need_show = true;
    private boolean debug = false; // 是否开启调试模式

    SakuraUpdaterClient() {
        ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        INSTANCE = this;
        LOGGER.info("SakuraUpdater Client is running!");
    }

    // 获取示例
    public static SakuraUpdaterClient getInstance() {
        return INSTANCE;
    }

    public Data getLastUpdateData() {
        if (last_update_data == null) {
            last_update_data = file_client.getUpdateList();
            if (last_update_data == null) {
                LOGGER.error("Failed to fetch update list from server.");
                return null;
            }
        }
        return last_update_data;
    }

    public int updateCheck() {
        if (getLastUpdateData() == null) {
            return -1;
        }
        Gson gson = new Gson();
        LOGGER.info("Update list fetched successfully: {}", gson.toJson(last_update_data));
        if (last_update_data.version.equals(ClientConfig.getNowVersion())) {
            LOGGER.info("Client is up to date.");
            return 0;
        }
        LOGGER.warn("Client is outdated. Latest version: {}", last_update_data.version);
        return 1;
    }

    public boolean integrityCheck() {
        update_progress = new Pair<>(-1, -1); // 重置进度
        integrityCheckResult = new Pair<List<File>, List<FileData>>(new ArrayList<>(), new ArrayList<>());
        if (getLastUpdateData() == null) {
            return false;
        }
        Gson gson = new Gson();
        LOGGER.info("Update list fetched successfully: {}", gson.toJson(last_update_data));
        for (PathData pathData : last_update_data.paths) {
            // 格式错误
            if (!pathData.model.equals("mirror") && !pathData.model.equals("push")) {
                LOGGER.warn("Unknown model: {}, skipping integrity check for this path.", pathData.model);
                continue;
            }
            // mirror需要删除
            if (pathData.model.equals("mirror")) {
                FileUtils.getAllFiles(new File(pathData.targetPath)).forEach(file -> {
                    if (!pathData.files.stream().anyMatch(fileData -> file.toString().equals(fileData.targetPath) && fileData.md5.equals(MD5.calculateMD5(file)))) { // 对比md5
                        LOGGER.warn("File {} will be deleted.", file.getName());
                        integrityCheckResult.getFirst().add(file);
                    }
                });
            }
            // 需要下载
            pathData.files.forEach(fileData -> {
                if (!FileUtils.getAllFiles(new File(pathData.targetPath)).stream()
                        .anyMatch(file -> file.toString().equals(fileData.targetPath) && fileData.md5.equals(MD5.calculateMD5(file)))) { // 判断是否存在和对比md5
                    LOGGER.warn("File {} will be downloaded to {}", fileData.sourcePath+":"+fileData.md5, MD5.calculateMD5(fileData.targetPath));
                    integrityCheckResult.getSecond().add(fileData);
                }
            });
        }
        
        download_failures = 0; // 重置失败次数
        if (integrityCheckResult.getFirst().isEmpty() && integrityCheckResult.getSecond().isEmpty()) {
            LOGGER.info("No files to remove or download.");
            update_progress = new Pair<>(0, 0);
            ClientConfig.setNowVersion(last_update_data.version); // 不需要更新文件但是还是需要更新本地版本号
            return false;
        }

        update_progress = new Pair<>(0, integrityCheckResult.getFirst().size()+integrityCheckResult.getSecond().size()); // 更新进度
        return true;
    }

    public void downloadUpdate() {
        // 删除不需要的文件
        integrityCheckResult.getFirst().forEach(file -> {
            if (file.delete()) {
                LOGGER.info("Deleted file: {}", file);
                update_progress = new Pair<>(update_progress.getFirst() + 1, update_progress.getSecond());
            } else {
                LOGGER.error("Failed to delete file: {}", file);
            }
        });

        // 下载需要的文件
        integrityCheckResult.getSecond().forEach(fileData -> {
            if (file_client.downloadFile(fileData.sourcePath, fileData.targetPath)) {
                LOGGER.info("Downloaded file: {}", fileData.sourcePath);
            } else {
                download_failures++;
                LOGGER.error("Failed to download file: {}", fileData.sourcePath);
            }
            update_progress = new Pair<>(update_progress.getFirst() + 1, update_progress.getSecond());
        });
        if (download_failures == 0) {
            LOGGER.info("All files downloaded successfully.");
            ClientConfig.setNowVersion(last_update_data.version);
        }
    }

    public Pair<Integer, Integer> getUpdateProgress() {
        return update_progress;
    }

    public int getDownloadFailures() {
        return download_failures;
    }

    public void connectToServer() {
        if (file_client != null) {
            if (file_client.isConnected()) {
                LOGGER.warn("Already connected to the server.");
                return;
            }
            file_client.disconnect();
            file_client.connect();
            return;
        }
        file_client = new FileClient(ClientConfig.host, ClientConfig.port);
        file_client.connect();
        LOGGER.info("Connected to SakuraUpdater Server at {}:{}", ClientConfig.host, ClientConfig.port);
    }

    public void disconnectFromServer() {
        if (file_client == null || !file_client.isConnected()) {
            LOGGER.warn("Not connected to the server.");
            return;
        }
        file_client.disconnect();
        LOGGER.info("Disconnected from SakuraUpdater Server.");
    }

    @SubscribeEvent
    public void onScreenOpenning(ScreenEvent.Opening event) {
        // 主菜单渲染完成
        if (!need_show)
            return;
        need_show = false;

        if (debug) {
            // event.setNewScreen(new TestScreen());
            event.setNewScreen(new TestScreen());
        } else {
            event.setNewScreen(new UpdateCheckScreen());
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                literal("sakuraupdater")
                        .then(createReloadCommand()));
        // .then(LiteralArgumentBuilder.<CommandSourceStack>literal("reload")
        // .then(LiteralArgumentBuilder.<CommandSourceStack>literal("client")
        // .requires(source -> source.hasPermission(0))
        // .executes(ctx -> {
        // ctx.getSource().sendSuccess(
        // () -> net.minecraft.network.chat.Component
        // .literal("SakuraUpdater 客户端配置已重载！"),
        // true);
        // ClientConfig.onLoad(null); // 重新加载配置
        // return 1;
        // }))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> createReloadCommand() {
        return literal("reload")
                .then(literal("client")
                        .requires(source -> source.hasPermission(0))
                        .executes(ctx -> {
                            sendSuccessMessage(ctx.getSource(), "SakuraUpdater 客户端配置已重载！");
                            ClientConfig.onLoad(null); // 重新加载配置
                            return 1;
                        }));
    }
}
