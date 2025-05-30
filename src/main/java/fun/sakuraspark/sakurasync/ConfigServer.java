package fun.sakuraspark.sakurasync;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.logging.LogUtils;

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
            .comment("A list of sync directories, each entry should be in the format 'path:mode', e.g. 'mod:sync'")
            .defineListAllowEmpty("SYNC_DIR", List.of(), ConfigServer::validateKeyMap);
    // static final ForgeConfigSpec for the server config

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static String update_time;
    public static Map<String, String> sync_map;

    private static boolean validateKeyMap(final Object obj) {
        if (obj instanceof String path && path.split(":").length == 2) {
            return true;
        }
        LOGGER.warn("{} format error, should be 'path:mode', e.g. 'mod:sync'. Please check the config file",
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
                                    () -> net.minecraft.network.chat.Component.literal("SakuraSync 服务端配置已重载！"), true);
                            onLoad(null); // 重新加载配置
                            return 1;
                        }));
    }
}
