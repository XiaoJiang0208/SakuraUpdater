package fun.sakuraspark.sakurasync;

import net.minecraftforge.api.distmarker.Dist;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import fun.sakuraspark.sakurasync.network.FileServer;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;


@Mod.EventBusSubscriber(value = Dist.DEDICATED_SERVER, modid = SakuraSync.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SakuraSyncServer {

    private static final Logger LOGGER = LogUtils.getLogger();

    //private final FileServer fileServer;

    SakuraSyncServer() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ConfigServer.SPEC);
        //fileServer = new FileServer(ConfigServer.SERVER_PORT.get(), ConfigServer.SYNC_DIR.get());
        LOGGER.info("SakuraSync Server is running!");
    }

}
