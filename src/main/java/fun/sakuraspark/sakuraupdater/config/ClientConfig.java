package fun.sakuraspark.sakuraupdater.config;

import java.util.Set;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import fun.sakuraspark.sakuraupdater.SakuraUpdater;
import fun.sakuraspark.sakuraupdater.SakuraUpdaterClient;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Forge's config APIs
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = SakuraUpdater.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientConfig {
	private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

	
	private static final ForgeConfigSpec.ConfigValue<String> HOST = BUILDER
			.comment("The host of the file server, default is 'localhost'.")
			.define("host", "localhost");

	private static final ForgeConfigSpec.IntValue PORT = BUILDER
			.comment("The port of the file server, default is 25564.")
			.defineInRange("port", 25564, 1, 65535);

	private static final ForgeConfigSpec.ConfigValue<String> now_version = BUILDER
			.comment("The current version of the client, used for update check. Don't change this unless you know what you're doing.")
			.define("now_version", "");

	public static final ForgeConfigSpec SPEC = BUILDER.build();

	public static int port;
	public static String host;

	@SubscribeEvent
	public static void onLoad(final ModConfigEvent event) {
		port = PORT.get();
		host = HOST.get();
		SakuraUpdaterClient.getInstance().connectToServer();
	}

	public static String getNowVersion() {
		return now_version.get();
	}

	public static void setNowVersion(String version) {
		now_version.set(version);
	}
}
