package fun.sakuraspark.sakurasync;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

public class SakuraSyncClient {

    private static final Logger LOGGER = LogUtils.getLogger();

    SakuraSyncClient() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ConfigClient.SPEC);
        LOGGER.info("SakuraSync Client is running!");
        
    }
}
