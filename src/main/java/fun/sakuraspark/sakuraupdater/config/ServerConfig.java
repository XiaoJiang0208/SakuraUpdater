package fun.sakuraspark.sakuraupdater.config;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import fun.sakuraspark.sakuraupdater.SakuraUpdater;
import fun.sakuraspark.sakuraupdater.SakuraUpdaterServer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.config.ModConfigEvent;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Forge's config APIs
@EventBusSubscriber(value = Dist.DEDICATED_SERVER, modid = SakuraUpdater.MODID, bus = EventBusSubscriber.Bus.MOD)
public class ServerConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // private static final ForgeConfigSpec.ConfigValue<String> UPDATE_TIME = BUILDER
    //         .comment(
    //                 "The last update time of the server, used for synchronization. Don't change this unless you know what you're doing.")
    //         .define("update_time", "2000-1-1_12:00:00");

    
    // post
    private static final ModConfigSpec.IntValue PORT = BUILDER
            .comment("The port of the file server, default is 25564.").defineInRange("port", 25564, 1, 65535);

    // a list of strings that are treated as sync directories
    private static final ModConfigSpec.ConfigValue<List<? extends String>> SYNC_DIR = BUILDER
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
    // static final ModConfigSpec for the server config

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static String update_time;

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
}
