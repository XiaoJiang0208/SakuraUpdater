package fun.sakuraspark.sakuraupdater.mixin;

import com.mojang.logging.LogUtils;

import fun.sakuraspark.sakuraupdater.gui.UpdateCheckScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class TitleScreenMixin extends net.minecraft.client.gui.screens.Screen {
    protected  TitleScreenMixin(Component title) {
        super(title);
    }

    private static final Logger LOGGER = LogUtils.getLogger();

    // 在菜单添加一个按钮，点击后打开更新界面
    @Inject(method = "init", at = @At("TAIL"))
    private void sakuraUpdater$addUpdateButton(CallbackInfo callbackInfo) {
        // 这里直接添加一个按钮，点击后打开更新界面
        // 你可以根据需要调整按钮的位置和样式
        this.addRenderableWidget(Button.builder(Component.translatable("gui.sakuraupdater.TitleScreen.checkupdate"), button -> {
            this.minecraft.setScreen(new UpdateCheckScreen());
        }).bounds(5, 5, 60, 20).build());
    }
}
