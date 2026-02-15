package fun.sakuraspark.sakuraupdater.config;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import fun.sakuraspark.sakuraupdater.SakuraUpdater;
import fun.sakuraspark.sakuraupdater.SakuraUpdaterServer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Forge's config APIs
@Mod.EventBusSubscriber(value = Dist.DEDICATED_SERVER, modid = SakuraUpdater.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ServerConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // private static final ForgeConfigSpec.ConfigValue<String> UPDATE_TIME = BUILDER
    //         .comment(
    //                 "The last update time of the server, used for synchronization. Don't change this unless you know what you're doing.")
    //         .define("update_time", "2000-1-1_12:00:00");

    
    // post
    private static final ForgeConfigSpec.IntValue PORT = BUILDER
            .comment("----IMPORTANT!!! Needs to restart!!!----\nThe port of the file server, default is 25564.").defineInRange("port", 25564, 1, 65535);

    // a list of strings that are treated as sync directories
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> SYNC_DIR = BUILDER
            .comment(
                    "A list of sync directories, each entry should be in the format 'targetpath:mode[:sourcepath:sourcepath2:...]', e.g. 'mod:mirror' or 'config:push:clientconfig'.\n"
                            + "The 'targetpath' is the client target path of sync, and 'sourcepath' is the server source path of sync.\n"
                            + "The 'sourcepath' is optional, if not provided, it will be the same as 'targetpath'.\n"
                            + "'mode' can be 'mirror' or 'push'. 'mirror' will delete files in the target directory that are not in the source directory, while 'push' will not.\n"
                            + "This is used to determine which directories to sync with the client.\n"
                            + "SYNC_DIR = [\n"
                            + "    'example:mirror', // it same as 'example:mirror:example'\n"
                            + "    'mods:mirror:mods:clientmods',\n"
                            + "    'config:push:clientconfig'\n"
                            + "]")
            .defineListAllowEmpty("SYNC_DIR", List.of(), ServerConfig::validateKeyMap);
    // static final ForgeConfigSpec for the server config

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    public static int port;

    private static boolean validateKeyMap(final Object obj) {
        if (obj instanceof String path && path.split(":").length >= 2) {
            if (path.split(":")[0] == null) {
                LOGGER.warn("{} format error, targetpath is missing. Please check the config file",
                        (String) obj);
                return false;
            }
            if (!"mirror".equals(path.split(":")[1]) && !"push".equals(path.split(":")[1])) {
                LOGGER.warn("{} format error, mode should be 'mirror' or 'push'. Please check the config file",
                        (String) obj);
                return false;
            }
            return true;
        }
        LOGGER.warn("{} format error, should be 'targetpath:mode[:sourcepath]'. Please check the config file",
                (String) obj);
        return false;
    }

    public static List<String> getSyncDirs() {
        return SYNC_DIR.get().stream()
                .collect(Collectors.toList());
    }

    @SubscribeEvent
    public static void onLoad(final ModConfigEvent.Loading event) {
        port = PORT.get();
        SakuraUpdaterServer.getInstance().runServer();
    }

    /**
     * 独立模式初始化 - 已弃用
     * <p>
     * 此方法不可行：ServerConfig 的静态字段依赖 ForgeConfigSpec，
     * 类加载时即会触发 NoClassDefFoundError。
     * </p>
     * <p>
     * 请使用 {@link StandaloneServerConfig#initialize()} 作为脱离 Forge 的替代方案，
     * 它使用 YAML 配置文件，完全不依赖 Forge/Minecraft。
     * </p>
     *
     * @deprecated 使用 {@link StandaloneServerConfig} 替代
     */
    @Deprecated
    public static void initializeStandalone() {
        throw new UnsupportedOperationException(
                "ServerConfig 依赖 ForgeConfigSpec，无法在脱离 Forge 时使用。" +
                "请使用 StandaloneServerConfig.initialize() 替代。");
    }
}
