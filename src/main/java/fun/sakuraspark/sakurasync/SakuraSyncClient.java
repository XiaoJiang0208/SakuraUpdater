package fun.sakuraspark.sakurasync;

import org.slf4j.Logger;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.logging.LogUtils;

import fun.sakuraspark.sakurasync.config.ClientConfig;
import fun.sakuraspark.sakurasync.gui.UpdateScreen;
import fun.sakuraspark.sakurasync.network.FileClient;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

public class SakuraSyncClient {

    private static final Logger LOGGER = LogUtils.getLogger();

    private FileClient file_client;

    private boolean need_show = true;

    SakuraSyncClient() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);

        LOGGER.info("SakuraSync Client is running!");

    }

    public void updateCheck() {
        
    }

    @SubscribeEvent
    public void onScreenOpenning(ScreenEvent.Opening event) {
        if (need_show) {
            event.setNewScreen(new UpdateScreen());
            need_show = false;
        }

    }
    
    @SubscribeEvent
    public void onConfigLoaded(final ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() != ClientConfig.SPEC) {
            return;
        }
        ClientConfig.onLoad(event);
        file_client = new FileClient(ClientConfig.host, ClientConfig.port);
        file_client.connect();
    }
    
	@SubscribeEvent
	public void onRegisterCommands(RegisterCommandsEvent event) {
		event.getDispatcher().register(
				LiteralArgumentBuilder.<CommandSourceStack>literal("ssreloadclient")
						.requires(source -> source.hasPermission(0))
						.executes(ctx -> {
							ctx.getSource().sendSuccess(
									() -> net.minecraft.network.chat.Component
											.literal("SakuraSync 客户端配置已重载！"),
									true);
							ClientConfig.onLoad(null); // 重新加载配置
							return 1;
						}));
	}
}
