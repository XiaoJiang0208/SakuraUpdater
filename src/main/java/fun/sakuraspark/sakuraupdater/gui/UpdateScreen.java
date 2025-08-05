package fun.sakuraspark.sakuraupdater.gui;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.CubeMap;
import net.minecraft.client.renderer.PanoramaRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class UpdateScreen extends Screen {

    public static final CubeMap CUBE_MAP = new CubeMap(new ResourceLocation("textures/gui/title/background/panorama"));

    private final PanoramaRenderer panorama = new PanoramaRenderer(CUBE_MAP);
    private boolean fading = true;
    private long fadeInStart;

    public UpdateScreen() {
        super(Component.translatable("gui.sakuraupdater.screen.UpdateScreen"));
    }

    @Override
    public void init() {
        
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 渲染背景
        if (this.fadeInStart == 0L && this.fading) {
            this.fadeInStart = Util.getMillis();
        }
        float f = this.fading ? (float) (Util.getMillis() - this.fadeInStart) / 1000.0F : 1.0F;
        this.panorama.render(partialTick, Mth.clamp(f, 0.0F, 1.0F));
        guiGraphics.fill(0, 0, this.width, this.height, 0x20000000);
        
        renderBackground(guiGraphics);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, this.height/2 , 16777215);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
}
