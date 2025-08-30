package fun.sakuraspark.sakuraupdater.gui;

import java.util.concurrent.CompletableFuture;

import fun.sakuraspark.sakuraupdater.SakuraUpdater;
import fun.sakuraspark.sakuraupdater.SakuraUpdaterClient;
import fun.sakuraspark.sakuraupdater.SakuraUpdaterServer;
import fun.sakuraspark.sakuraupdater.gui.components.MarkdownBox;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.CubeMap;
import net.minecraft.client.renderer.PanoramaRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class UpdateCheckScreen extends Screen {

    // public static final CubeMap CUBE_MAP = new CubeMap(new
    // ResourceLocation("textures/gui/title/background/panorama"));

    // private final PanoramaRenderer panorama = new PanoramaRenderer(CUBE_MAP);
    // private boolean fading = true;
    // private long fadeInStart;

    private int updateStatus = 0; // -1: error, 0: checking, 1: need update, 2: no update 3: only server update

    public UpdateCheckScreen() {
        super(Component.translatable("gui.sakuraupdater.UpdateCheckScreen"));
        CompletableFuture.supplyAsync(() -> {
            // 这里运行在后台线程中
            try {
                int result = SakuraUpdaterClient.getInstance().updateCheck();
                if (result == -1) {
                    return -1;
                } else if (result == 0) {
                    return 2; // No update
                } else {
                    if (SakuraUpdaterClient.getInstance().integrityCheck()) {
                        return 1; // Need update
                    }
                    return 3; // Only server update
                }
            } catch (Exception e) {
                return -1;
            }
        }, Util.backgroundExecutor()) // 使用 Minecraft 的后台线程池
                .thenAcceptAsync(result -> {
                    // 回到主线程更新UI
                    Minecraft.getInstance().execute(() -> {
                        updateStatus = result; // 更新状态
                        // 当状态变为1时，重建界面添加按钮
                        this.rebuildWidgets();
                    });
                });
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false; // 禁止ESC关闭
    }

    @Override
    public void init() {
        super.init();
        if (updateStatus == -1) {
            this.addRenderableWidget(
                    Button.builder(Component.translatable("gui.sakuraupdater.UpdateCheckScreen.retry"), button -> {
                        // 点击按钮后重新检查更新
                        Minecraft.getInstance().setScreen(new UpdateCheckScreen());
                    }).bounds(this.width / 2 - 100, this.height - 50, 200, 20).build());
            this.addRenderableWidget(
                    Button.builder(Component.translatable("gui.sakuraupdater.UpdateCheckScreen.cancel"), button -> {
                        // 点击按钮后关闭当前界面
                        SakuraUpdaterClient.getInstance().disconnectFromServer();
                        Minecraft.getInstance().setScreen(new TitleScreen(true));
                    }).bounds(this.width / 2 - 100, this.height - 20, 200, 20).build());
        } else if (updateStatus == 1) {
            this.addRenderableWidget(
                    Button.builder(Component.translatable("gui.sakuraupdater.UpdateCheckScreen.update"), button -> {
                        // 点击按钮后打开更新界面
                        Minecraft.getInstance().setScreen(new UpdateScreen());
                    }).bounds(this.width / 2 - 100, this.height - 50, 200, 20).build());
            this.addRenderableWidget(
                    Button.builder(Component.translatable("gui.sakuraupdater.UpdateCheckScreen.cancel"), button -> {
                        // 点击按钮后关闭当前界面
                        SakuraUpdaterClient.getInstance().disconnectFromServer();
                        Minecraft.getInstance().setScreen(new TitleScreen(true));
                    }).bounds(this.width / 2 - 100, this.height - 20, 200, 20).build());
        } else {
            this.addRenderableWidget(
                    Button.builder(Component.translatable("gui.sakuraupdater.UpdateCheckScreen.ok"), button -> {
                        // 点击按钮后关闭当前界面
                        SakuraUpdaterClient.getInstance().disconnectFromServer();
                        Minecraft.getInstance().setScreen(new TitleScreen(true));
                    }).bounds(this.width / 2 - 100, this.height - 20, 200, 20).build());
        }
        if (updateStatus == 1 || updateStatus == 3) {
            this.addRenderableWidget(new MarkdownBox(this.width / 2 - 125, this.height / 2 - 70, 250, 140,
                    SakuraUpdaterClient.getInstance().getLastUpdateData().description));
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

        if (updateStatus == 0) {
            guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2, 16777215);
        } else if (updateStatus == 1 || updateStatus == 3) {
            guiGraphics.drawCenteredString(this.font,
                    Component.literal(SakuraUpdaterClient.getInstance().getLastUpdateData().version), this.width / 2,
                    30, 16711680); // Red color for need update
        } else if (updateStatus == 2) {
            guiGraphics.drawCenteredString(this.font,
                    Component.translatable("gui.sakuraupdater.UpdateCheckScreen.NoUpdate"), this.width / 2,
                    this.height / 2, 65280); // Green color for no update
        } else if (updateStatus == -1) {
            guiGraphics.drawCenteredString(this.font,
                    Component.translatable("gui.sakuraupdater.UpdateCheckScreen.Error"), this.width / 2,
                    this.height / 2, 16711680); // Red color for error

        }
    }
}
