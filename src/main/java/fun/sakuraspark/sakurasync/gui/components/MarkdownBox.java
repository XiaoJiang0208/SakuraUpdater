package fun.sakuraspark.sakurasync.gui.components;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractScrollWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

import java.util.ArrayList;
import java.util.List;

public class MarkdownBox extends AbstractScrollWidget {
    private String markdownText;
    private List<String> lines;

    public MarkdownBox(int x, int y, int width, int height, String markdownText) {
        super(x, y, width, height, null);
        this.markdownText = markdownText;
        this.lines = parseMarkdown(markdownText);
    }

    // 简单的Markdown解析，仅支持# 标题、**加粗**、*斜体*和普通文本
    private List<String> parseMarkdown(String text) {
        List<String> result = new ArrayList<>();
        String[] rawLines = text.split("\n");
        for (String line : rawLines) {
            // 这里只做简单处理，复杂的可以用第三方库
            if (line.startsWith("# ")) {
                result.add("§l" + line.substring(2)); // §l为Minecraft粗体
            } else if (line.startsWith("## ")) {
                result.add("§n" + line.substring(3)); // §n为Minecraft下划线
            } else {
                // 替换**加粗**
                line = line.replace("**", "§l").replace("*", "§o");
                result.add(line);
            }
        }
        return result;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        this.defaultButtonNarrationText(narrationElementOutput);
    }

    @Override
    protected int getInnerHeight() {
        Font font = Minecraft.getInstance().font;
        return lines.size() * (font.lineHeight + 2);
    }

    @Override
    protected double scrollRate() {
        return 10.0; // 每次滚动10像素
    }

    @Override
    protected void renderContents(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        int yOffset = 0;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            guiGraphics.drawString(font, line, this.getX() + 4, this.getY() + 4 + yOffset, 0xFFFFFF);
            yOffset += font.lineHeight + 2;
        }
    }
}
