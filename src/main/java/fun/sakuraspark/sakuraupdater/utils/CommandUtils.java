package fun.sakuraspark.sakuraupdater.utils;

import net.minecraft.commands.CommandSourceStack;

public class CommandUtils {

    public static void sendSuccessMessage(CommandSourceStack source, String message) {
        source.sendSuccess(
                () -> net.minecraft.network.chat.Component.literal(message),
                true);
    }

    public static void sendFailureMessage(CommandSourceStack source, String message) {
        source.sendFailure(net.minecraft.network.chat.Component.literal(message));
    }
}
