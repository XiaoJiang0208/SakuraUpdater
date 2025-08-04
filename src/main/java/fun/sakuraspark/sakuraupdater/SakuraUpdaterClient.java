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
import fun.sakuraspark.sakuraupdater.gui.UpdateScreen;
import fun.sakuraspark.sakuraupdater.network.FileClient;
import fun.sakuraspark.sakuraupdater.utils.FileUtils;
import fun.sakuraspark.sakuraupdater.utils.MD5;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

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

    private boolean need_show = true;
    private boolean debug = true; // 是否开启调试模式

    SakuraUpdaterClient() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
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

    public Pair<List<File>, List<FileData>> integrityCheck() {
        if (getLastUpdateData() == null) {
            return null;
        }
        Gson gson = new Gson();
        LOGGER.info("Update list fetched successfully: {}", gson.toJson(last_update_data));
        Pair<List<File>, List<FileData>> remove_and_download = new Pair<List<File>, List<FileData>>(new ArrayList<>(),
                new ArrayList<>());
        for (PathData pathData : last_update_data.paths) {
            // 格式错误
            if (!pathData.model.equals("mirror") && !pathData.model.equals("push")) {
                LOGGER.warn("Unknown model: {}, skipping integrity check for this path.", pathData.model);
                continue;
            }
            // mirror需要删除
            if (pathData.model.equals("mirror")) {
                FileUtils.getAllFiles(new File(pathData.targetPath)).forEach(file -> {
                    if (!pathData.files.stream().anyMatch(fileData -> fileData.md5.equals(MD5.calculateMD5(file)))) {
                        LOGGER.warn("File {} will be deleted.", file.getName());
                        remove_and_download.getFirst().add(file);
                    }
                });
            }
            pathData.files.forEach(fileData -> {
                if (!FileUtils.getAllFiles(new File(pathData.targetPath)).stream()
                        .anyMatch(file -> MD5.calculateMD5(file).equals(fileData.md5))) {
                    LOGGER.warn("File {} will be downloaded.", fileData.sourcePath);
                    remove_and_download.getSecond().add(fileData);
                }
            });
        }
        return remove_and_download;
    }

    public void downloadUpdate() {
        if (last_update_data == null || last_update_data.paths.isEmpty()
                || last_update_data.paths.get(0).files.isEmpty()) {
            LOGGER.warn("No updates available to download.");
            return;
        }
        file_client.downloadFile(last_update_data.paths.get(0).files.get(0).sourcePath,
                last_update_data.paths.get(0).files.get(0).targetPath);
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
            event.setNewScreen(new UpdateScreen());
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
