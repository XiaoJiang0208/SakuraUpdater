package fun.sakuraspark.sakuraupdater.config;

import java.util.Set;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import fun.sakuraspark.sakuraupdater.SakuraUpdater;
import fun.sakuraspark.sakuraupdater.SakuraUpdaterClient;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.config.ModConfigEvent;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Forge's config APIs
@EventBusSubscriber(value = Dist.CLIENT, modid = SakuraUpdater.MODID, bus = EventBusSubscriber.Bus.MOD)
public class ClientConfig {
	private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

	
	private static final ModConfigSpec.ConfigValue<String> HOST = BUILDER
			.comment("The host of the file server, default is 'localhost'.")
			.define("host", "localhost");

	private static final ModConfigSpec.IntValue PORT = BUILDER
			.comment("The port of the file server, default is 25564.")
			.defineInRange("port", 25564, 1, 65535);

	private static final ModConfigSpec.ConfigValue<String> now_version = BUILDER
			.comment("The current version of the client, used for update check. Don't change this unless you know what you're doing.")
			.define("now_version", "");

	public static final ModConfigSpec SPEC = BUILDER.build();

	public static int port;
	public static String host;

	@SubscribeEvent
	public static void onLoad(final ModConfigEvent event) {
		port = PORT.get();
		host = HOST.get();
		while (SakuraUpdaterClient.getInstance() == null);
		SakuraUpdaterClient.getInstance().connectToServer();
	}

	public static String getNowVersion() {
		return now_version.get();
	}

	public static void setNowVersion(String version) {
		now_version.set(version);
		SPEC.save();
	}
}
