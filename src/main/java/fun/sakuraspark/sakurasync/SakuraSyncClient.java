package fun.sakuraspark.sakurasync;

import org.slf4j.Logger;

import com.google.gson.Gson;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.logging.LogUtils;

import fun.sakuraspark.sakurasync.config.ClientConfig;
import fun.sakuraspark.sakurasync.config.DataConfig.Data;
import fun.sakuraspark.sakurasync.gui.TestScreen;
import fun.sakuraspark.sakurasync.gui.UpdateScreen;
import fun.sakuraspark.sakurasync.network.FileClient;

import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

import static fun.sakuraspark.sakurasync.utils.CommandUtils.sendSuccessMessage;
import static net.minecraft.commands.Commands.*;

public class SakuraSyncClient {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static SakuraSyncClient INSTANCE;

    private FileClient file_client;
    private Data last_update_data = null; // 上次更新的数据

    private boolean need_show = true;
    private boolean debug = true; // 是否开启调试模式

    SakuraSyncClient() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        INSTANCE = this;
        LOGGER.info("SakuraSync Client is running!");
        LOGGER.debug("tttestttttttttttttttt");
    }

    //获取示例
    public static SakuraSyncClient getInstance() {
        return INSTANCE;
    }

    public boolean updateCheck() {
        last_update_data = file_client.getUpdateList();
        if (last_update_data == null) {
            LOGGER.error("Failed to fetch update list from server.");
            return false;
        }
        Gson gson = new Gson();
        LOGGER.info("Update list fetched successfully: {}", gson.toJson(last_update_data));
        return true;
    }

    public void downloadUpdate() {
        if (last_update_data == null || last_update_data.files.isEmpty()) {
            LOGGER.warn("No updates available to download.");
            return;
        }
        file_client.downloadFile(last_update_data.files.get(0).path, last_update_data.files.get(0).targetPath);
    }
    
    public void connectToServer() {
        if(file_client != null) {
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
        LOGGER.info("Connected to SakuraSync Server at {}:{}", ClientConfig.host, ClientConfig.port);
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
                literal("ssync")
                        .then(createReloadCommand()));
        // .then(LiteralArgumentBuilder.<CommandSourceStack>literal("reload")
        // .then(LiteralArgumentBuilder.<CommandSourceStack>literal("client")
        // .requires(source -> source.hasPermission(0))
        // .executes(ctx -> {
        // ctx.getSource().sendSuccess(
        // () -> net.minecraft.network.chat.Component
        // .literal("SakuraSync 客户端配置已重载！"),
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
                            sendSuccessMessage(ctx.getSource(), "SakuraSync 客户端配置已重载！");
                            ClientConfig.onLoad(null); // 重新加载配置
                            return 1;
                        }));
    }
}
