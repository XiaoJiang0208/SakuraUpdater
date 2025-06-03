package fun.sakuraspark.sakurasync;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.logging.LogUtils;

import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Forge's config APIs
@Mod.EventBusSubscriber(value = Dist.DEDICATED_SERVER, modid = SakuraSync.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ConfigServer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.ConfigValue<String> UPDATE_TIME = BUILDER
            .comment(
                    "The last update time of the server, used for synchronization. Don't change this unless you know what you're doing.")
            .define("update_time", "2000-1-1T12:00:00Z");

    // a list of strings that are treated as sync directories
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> SYNC_DIR = BUILDER
            .comment(
                    "A list of sync directories, each entry should be in the format 'sourcepath:mode[:targetpath]', e.g. 'mod:mirror' or 'clientconfig:push:config'.\n"
                            + "The 'sourcepath' is the source path of sync, and 'targetpath' is optional.\n"
                            + "'mode' can be 'mirror' or 'push'. 'mirror' will delete files in the target directory that are not in the source directory, while 'push' will not.\n"
                            + "This is used to determine which directories to sync with the client.")
            .defineListAllowEmpty("SYNC_DIR", List.of(), ConfigServer::validateKeyMap);
    // static final ForgeConfigSpec for the server config

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static String update_time;
    public static Map<String, String> sync_map;

    private static boolean validateKeyMap(final Object obj) {
        if (obj instanceof String path && path.split(":").length >= 2) {
            if (path.split(":")[0] == null) {
                LOGGER.warn("{} format error, sourcepath is missing. Please check the config file",
                        (String) obj);
                return false;
            }
            if (!"mirror".equals(path.split(":")[1]) || !"push".equals(path.split(":")[1])) {
                LOGGER.warn("{} format error, mode should be 'mirror' or 'push'. Please check the config file",
                        (String) obj);
                return false;
            }
            return true;
        }
        LOGGER.warn("{} format error, should be 'sourcepath:mode[:targetpath]'. Please check the config file",
                (String) obj);
        return false;
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        update_time = UPDATE_TIME.get();
        // convert the list of strings into a set of items
        sync_map = SYNC_DIR.get().stream()
                .map(s -> s.split(":"))
                .collect(Collectors.toMap(
                        parts -> parts[0].trim(),
                        parts -> parts.length > 1 ? parts[1].trim() : "sync"));
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                LiteralArgumentBuilder.<CommandSourceStack>literal("ssreloadserver")
                        .requires(source -> source.hasPermission(2)) // 2 = OP权限
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(
                                    () -> net.minecraft.network.chat.Component
                                            .literal("SakuraSync server config reloaded!"),
                                    true);
                            onLoad(null); // reload config
                            return 1;
                        }));
    }
}
