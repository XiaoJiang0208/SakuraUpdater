package fun.sakuraspark.sakuraupdater.gui;

import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import fun.sakuraspark.sakuraupdater.SakuraUpdaterClient;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

public class FixScreen extends Screen {

    private static final Logger LOGGER = LogUtils.getLogger();

    int fixStatus = 0; // -1: error, 0: checking, 1: issues found, 2: no issues

    public FixScreen() {
        super(Component.translatable("gui.sakuraupdater.FixScreen"));
        LOGGER.info("start Verify Mods Integrity...");
        CompletableFuture.supplyAsync(() -> {
            // 这里运行在后台线程中
            try {
                // 强制进行完整性检查
                int result = SakuraUpdaterClient.getInstance().updateCheck();
                if (result == -1) {
                    return -1;
                }
                if (SakuraUpdaterClient.getInstance().integrityCheck()) {
                    return 1; // Need update
                }
                return 2; // No issues found
            } catch (Exception e) {
                LOGGER.error("Error during update check", e);
                return -1;
            }
        }, Util.backgroundExecutor()) // 使用 Minecraft 的后台线程池
                .thenAcceptAsync(result -> {
                    // 回到主线程更新UI
                    Minecraft.getInstance().execute(() -> {
                        fixStatus = result; // 更新状态
                        // 当状态变为1时，重建界面添加按钮
                        this.rebuildWidgets();
                    });
                });
    }

    @Override
    public void init() {
        super.init();
        if (fixStatus == -1) {
            this.addRenderableWidget(
                    Button.builder(Component.translatable("gui.sakuraupdater.FixScreen.retry"), button -> {
                        // 点击按钮后重新检查更新
                        Minecraft.getInstance().setScreen(new FixScreen());
                    }).bounds(this.width / 2 - 100, this.height - 50, 200, 20).build());
            this.addRenderableWidget(
                    Button.builder(Component.translatable("gui.sakuraupdater.FixScreen.cancel"), button -> {
                        // 点击按钮后关闭当前界面
                        Minecraft.getInstance().setScreen(new TitleScreen(true));
                    }).bounds(this.width / 2 - 100, this.height - 20, 200, 20).build());
        } else if (fixStatus == 1) {
            this.addRenderableWidget(
                    Button.builder(Component.translatable("gui.sakuraupdater.FixScreen.fix"), button -> {
                        // 点击按钮后打开更新界面
                        Minecraft.getInstance().setScreen(new UpdateScreen("FixScreen"));
                    }).bounds(this.width / 2 - 100, this.height - 50, 200, 20).build());
            this.addRenderableWidget(
                    Button.builder(Component.translatable("gui.sakuraupdater.FixScreen.cancel"), button -> {
                        // 点击按钮后关闭当前界面
                        Minecraft.getInstance().setScreen(new TitleScreen(true));
                    }).bounds(this.width / 2 - 100, this.height - 20, 200, 20).build());
        } else {
            this.addRenderableWidget(
                    Button.builder(Component.translatable("gui.sakuraupdater.FixScreen.ok"), button -> {
                        // 点击按钮后关闭当前界面
                        Minecraft.getInstance().setScreen(new TitleScreen(true));
                    }).bounds(this.width / 2 - 100, this.height - 20, 200, 20).build());
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // // 渲染背景
        // if (this.fadeInStart == 0L && this.fading) {
        //     this.fadeInStart = Util.getMillis();
        // }
        // float f = this.fading ? (float) (Util.getMillis() - this.fadeInStart) / 1000.0F : 1.0F;
        // this.panorama.render(partialTick, Mth.clamp(f, 0.0F, 1.0F));
        // guiGraphics.fill(0, 0, this.width, this.height, 0x20000000);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        if (fixStatus == 0) {
            guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2, 16777215);
        } else if (fixStatus == 1) {
            guiGraphics.drawCenteredString(this.font,
                    Component.translatable("gui.sakuraupdater.FixScreen.IssuesFound"), this.width / 2,
                    this.height / 2, 16711680); // Red color for issues found
        } else if (fixStatus == 2) {
            guiGraphics.drawCenteredString(this.font,
                    Component.translatable("gui.sakuraupdater.FixScreen.NoIssues"), this.width / 2,
                    this.height / 2, 65280); // Green color for no issues found
        } else if (fixStatus == -1) {
            guiGraphics.drawCenteredString(this.font,
                    Component.translatable("gui.sakuraupdater.FixScreen.error"), this.width / 2,
                    this.height / 2, 16711680); // Red color for error

        }
    }
}
