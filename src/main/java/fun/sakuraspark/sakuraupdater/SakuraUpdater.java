package fun.sakuraspark.sakuraupdater;

import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(SakuraUpdater.MODID)
public class SakuraUpdater {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "sakuraupdater";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();

    SakuraUpdaterServer serverInstance;
    SakuraUpdaterClient clientInstance;

    public SakuraUpdater() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        LOGGER.info("Is debug enabled? {}", LOGGER.isDebugEnabled());
        LOGGER.debug("iiiiiiiiiiiiiiiiii");
        // Register the commonSetup method for modloading
        // modEventBus.addListener(this::commonSetup);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);

        if (FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
            LOGGER.info("Try run SakuraUpdater Server");
            serverInstance = new SakuraUpdaterServer();
            MinecraftForge.EVENT_BUS.register(serverInstance);
        }
        if (FMLEnvironment.dist == Dist.CLIENT) {
            LOGGER.info("Try run SakuraUpdater Client");
            clientInstance = new SakuraUpdaterClient();
            MinecraftForge.EVENT_BUS.register(clientInstance);
        }
    }

    // private void commonSetup(final FMLCommonSetupEvent event)
    // {
    // // Some common setup code
    // LOGGER.info("HELLO FROM COMMON SETUP");

    // if (Config.logDirtBlock)
    // LOGGER.info("DIRT BLOCK >> {}", ForgeRegistries.BLOCKS.getKey(Blocks.DIRT));

    // LOGGER.info(Config.magicNumberIntroduction + Config.magicNumber);

    // Config.items.forEach((item) -> LOGGER.info("ITEM >> {}", item.toString()));
    // }

}
