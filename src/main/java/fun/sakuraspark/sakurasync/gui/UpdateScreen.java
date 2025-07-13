package fun.sakuraspark.sakurasync.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class UpdateScreen extends Screen {

    public UpdateScreen() {
        super(Component.translatable("gui.sakurasync.screen.UpdateChecking"));
    }

    @Override
    public void init() {
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, this.height/2 , 16777215);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
}
