package com.estie.mobarmory.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class MultilineTextBox extends AbstractWidget {
    private static final int MAX_LENGTH = 4096;
    
    private final Font font;
    private final StringBuilder text = new StringBuilder();
    
    private int cursorPos = 0;
    private int selectionStart = -1;
    private boolean dragging = false;
    
    public MultilineTextBox(Font font, int x, int y, int width, int height, String initial) {
        super(x, y, width, height, Component.literal(""));
        this.font = font;
        
        if (initial != null) {
            text.append(initial);
            cursorPos = text.length();
        }
    }
    
    public String getValue() {
        return text.toString();
    }
    
    public void setValue(String value) {
        text.setLength(0);
        
        if (value != null) {
            text.append(value, 0, Math.min(value.length(), MAX_LENGTH));
        }
        
        cursorPos = text.length();
        selectionStart = -1;
    }
    
    private List<FormattedCharSequence> wrappedLines() {
        List<FormattedCharSequence> result = new ArrayList<>();
        int innerWidth = this.width - 8;
        
        for (String rawLine : text.toString().split("\n", -1)) {
            if (rawLine.isEmpty()) {
                result.add(FormattedCharSequence.EMPTY);
            } else {
                result.addAll(font.split(Component.literal(rawLine), innerWidth));
            }
        }
        
        return result;
    }
    
    /**
     * Returns the raw-string index where a visual line begins.
     * <p>
     * This is needed because font.split() can wrap a single raw line
     * into multiple visual lines.
     */
    private List<LineInfo> getLines() {
        List<LineInfo> result = new ArrayList<>();
        int innerWidth = this.width - 8;
        
        int rawStart = 0;
        
        while (rawStart <= text.length()) {
            int newline = text.indexOf("\n", rawStart);
            
            String rawLine;
            int rawEnd;
            
            if (newline == -1) {
                rawEnd = text.length();
                rawLine = text.substring(rawStart);
            } else {
                rawEnd = newline;
                rawLine = text.substring(rawStart, newline);
            }
            
            if (rawLine.isEmpty()) {
                result.add(new LineInfo(rawStart, rawEnd, ""));
            } else {
                List<FormattedCharSequence> wrapped =
                        font.split(Component.literal(rawLine), innerWidth);
                
                int offset = 0;
                
                for (FormattedCharSequence line : wrapped) {
                    String plain = getPlainText(line);
                    
                    result.add(new LineInfo(
                            rawStart + offset,
                            rawStart + offset + plain.length(),
                            plain
                    ));
                    
                    offset += plain.length();
                }
            }
            
            if (newline == -1) {
                break;
            }
            
            rawStart = newline + 1;
        }
        
        return result;
    }
    
    private String getPlainText(FormattedCharSequence sequence) {
        StringBuilder result = new StringBuilder();
        
        sequence.accept((index, style, codePoint) -> {
            result.appendCodePoint(codePoint);
            return true;
        });
        
        return result.toString();
    }
    
    private int getLineForCursor(List<LineInfo> lines) {
        for (int i = 0; i < lines.size(); i++) {
            LineInfo line = lines.get(i);
            
            if (cursorPos >= line.start && cursorPos <= line.end) {
                return i;
            }
        }
        
        return Math.max(0, lines.size() - 1);
    }
    
    @Override
    protected void renderWidget(
            GuiGraphics gfx,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        gfx.fill(
                getX(),
                getY(),
                getX() + width,
                getY() + height,
                0xFF000000
        );
        
        gfx.renderOutline(
                getX(),
                getY(),
                width,
                height,
                isFocused() ? 0xFFFFFFFF : 0xFF808080
        );
        
        List<LineInfo> lines = getLines();
        
        int lineY = getY() + 4;
        
        for (LineInfo line : lines) {
            if (lineY + font.lineHeight > getY() + height) {
                break;
            }
            
            drawSelection(gfx, line, lineY);
            
            gfx.drawString(
                    font,
                    Component.literal(line.text),
                    getX() + 4,
                    lineY,
                    0xFFFFFF
            );
            
            lineY += font.lineHeight;
        }
        
        if (isFocused() && (System.currentTimeMillis() / 500) % 2 == 0) {
            drawCursor(gfx, lines);
        }
    }
    
    private void drawCursor(GuiGraphics gfx, List<LineInfo> lines) {
        if (lines.isEmpty()) {
            return;
        }
        
        int lineIndex = getLineForCursor(lines);
        LineInfo line = lines.get(lineIndex);
        
        int localPos = Math.max(
                0,
                Math.min(cursorPos - line.start, line.text.length())
        );
        
        String beforeCursor = line.text.substring(0, localPos);
        
        int cursorX = getX() + 4 + font.width(beforeCursor);
        int cursorY = getY() + 4 + lineIndex * font.lineHeight;
        
        gfx.fill(
                cursorX,
                cursorY,
                cursorX + 1,
                cursorY + font.lineHeight,
                0xFFFFFFFF
        );
    }
    
    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!isFocused() || text.length() >= MAX_LENGTH) {
            return false;
        }
        
        if (chr == '\n' || chr == '\r') {
            return false;
        }
        
        deleteSelection();
        
        if (text.length() >= MAX_LENGTH) {
            return false;
        }
        
        text.insert(cursorPos, chr);
        cursorPos++;
        
        return true;
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isFocused()) {
            return false;
        }
        
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        
        if (ctrl) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_A -> {
                    selectionStart = 0;
                    cursorPos = text.length();
                    return true;
                }
                
                case GLFW.GLFW_KEY_C -> {
                    if (hasSelection()) {
                        Minecraft.getInstance().keyboardHandler.setClipboard(getSelectedText());
                    }
                    return true;
                }
                
                case GLFW.GLFW_KEY_X -> {
                    if (hasSelection()) {
                        Minecraft.getInstance().keyboardHandler.setClipboard(getSelectedText());
                        deleteSelection();
                    }
                    return true;
                }
                
                case GLFW.GLFW_KEY_V -> {
                    String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
                    
                    if (clipboard != null && !clipboard.isEmpty()) {
                        clipboard = clipboard.replace("\r\n", "\n").replace('\r', '\n');
                        
                        deleteSelection();
                        
                        int remaining = MAX_LENGTH - text.length();
                        if (remaining > 0) {
                            clipboard = clipboard.substring(
                                    0,
                                    Math.min(clipboard.length(), remaining)
                            );
                            
                            text.insert(cursorPos, clipboard);
                            cursorPos += clipboard.length();
                        }
                    }
                    
                    return true;
                }
            }
        }
        
        switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (hasSelection()) {
                    deleteSelection();
                } else if (cursorPos > 0) {
                    int start = ctrl
                            ? getPreviousWordBoundary(cursorPos)
                            : cursorPos - 1;
                    
                    text.delete(start, cursorPos);
                    cursorPos = start;
                }
                
                return true;
            }
            
            case GLFW.GLFW_KEY_DELETE -> {
                if (hasSelection()) {
                    deleteSelection();
                } else if (cursorPos < text.length()) {
                    int end = ctrl
                            ? getNextWordBoundary(cursorPos)
                            : cursorPos + 1;
                    
                    text.delete(cursorPos, end);
                }
                
                return true;
            }
            
            case GLFW.GLFW_KEY_LEFT -> {
                if (ctrl) {
                    cursorPos = getPreviousWordBoundary(cursorPos);
                } else if (cursorPos > 0) {
                    cursorPos--;
                }
                
                return true;
            }
            
            case GLFW.GLFW_KEY_RIGHT -> {
                if (ctrl) {
                    cursorPos = getNextWordBoundary(cursorPos);
                } else if (cursorPos < text.length()) {
                    cursorPos++;
                }
                
                return true;
            }
            
            case GLFW.GLFW_KEY_HOME -> {
                cursorPos = getLineStart(cursorPos);
                return true;
            }
            
            case GLFW.GLFW_KEY_END -> {
                cursorPos = getLineEnd(cursorPos);
                return true;
            }
            
            case GLFW.GLFW_KEY_UP -> {
                moveCursorVertical(-1);
                return true;
            }
            
            case GLFW.GLFW_KEY_DOWN -> {
                moveCursorVertical(1);
                return true;
            }
            
            case GLFW.GLFW_KEY_ENTER,
                 GLFW.GLFW_KEY_KP_ENTER -> {
                if (text.length() < MAX_LENGTH) {
                    text.insert(cursorPos, '\n');
                    cursorPos++;
                }
                
                return true;
            }
            
            case GLFW.GLFW_KEY_ESCAPE -> {
                setFocused(false);
                return true;
            }
        }
        
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    private int getLineStart(int pos) {
        int newline = text.lastIndexOf("\n", Math.max(0, pos - 1));
        return newline == -1 ? 0 : newline + 1;
    }
    
    private int getLineEnd(int pos) {
        int newline = text.indexOf("\n", pos);
        return newline == -1 ? text.length() : newline;
    }
    
    private int getPreviousWordBoundary(int pos) {
        if (pos <= 0) {
            return 0;
        }
        
        int i = pos;
        
        while (i > 0 && Character.isWhitespace(text.charAt(i - 1))) {
            i--;
        }
        
        while (i > 0 && !Character.isWhitespace(text.charAt(i - 1))) {
            i--;
        }
        
        return i;
    }
    
    private int getNextWordBoundary(int pos) {
        int i = pos;
        
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        
        while (i < text.length() && !Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        
        return i;
    }
    
    private void moveCursorVertical(int direction) {
        List<LineInfo> lines = getLines();
        
        if (lines.isEmpty()) {
            return;
        }
        
        int currentLine = getLineForCursor(lines);
        int targetLine = currentLine + direction;
        
        if (targetLine < 0 || targetLine >= lines.size()) {
            return;
        }
        
        LineInfo current = lines.get(currentLine);
        LineInfo target = lines.get(targetLine);
        
        int currentOffset = Math.max(
                0,
                Math.min(cursorPos - current.start, current.text.length())
        );
        
        int targetOffset = Math.min(
                currentOffset,
                target.text.length()
        );
        
        cursorPos = target.start + targetOffset;
    }
    
    @Override
    public void onClick(double mouseX, double mouseY) {
        setFocused(true);
        
        List<LineInfo> lines = getLines();
        
        if (lines.isEmpty()) {
            cursorPos = 0;
            return;
        }
        
        int clickedLine = (int)((mouseY - getY() - 4) / font.lineHeight);
        
        clickedLine = Mth.clamp(
                clickedLine,
                0,
                lines.size() - 1
        );
        
        LineInfo line = lines.get(clickedLine);
        
        float relativeX = (float)(mouseX - getX() - 4);
        
        if (relativeX <= 0) {
            cursorPos = line.start;
            selectionStart = cursorPos;
            dragging = true;
            return;
        }
        
        int bestPos = line.start;
        float bestDistance = Float.MAX_VALUE;
        
        for (int i = 0; i <= line.text.length(); i++) {
            String before = line.text.substring(0, i);
            float x = font.width(before);
            
            float distance = Math.abs(x - relativeX);
            
            if (distance < bestDistance) {
                bestDistance = distance;
                bestPos = line.start + i;
            }
        }
        
        cursorPos = bestPos;
        selectionStart = bestPos;
        dragging = true;
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!dragging) return false;
        
        List<LineInfo> lines = getLines();
        
        if (lines.isEmpty()) return true;
        
        int clickedLine = (int)((mouseY - getY() - 4) / font.lineHeight);
        clickedLine = Mth.clamp(clickedLine, 0, lines.size() - 1);
        
        LineInfo line = lines.get(clickedLine);
        
        float relativeX = (float)(mouseX - getX() - 4);
        
        if (relativeX <= 0) {
            cursorPos = line.start;
            return true;
        }
        
        int bestPos = line.start;
        float bestDistance = Float.MAX_VALUE;
        
        for (int i = 0; i <= line.text.length(); i++) {
            String before = line.text.substring(0, i);
            float x = font.width(before);
            
            float distance = Math.abs(x - relativeX);
            
            if (distance < bestDistance) {
                bestDistance = distance;
                bestPos = line.start + i;
            }
        }
        
        cursorPos = bestPos;
        return true;
    }
    
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return true;
    }
    
    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(
                NarratedElementType.TITLE,
                Component.literal("NBT input")
        );
    }
    
    private boolean hasSelection() {
        return selectionStart != -1 && selectionStart != cursorPos;
    }
    
    private void drawSelection(GuiGraphics gfx, LineInfo line, int lineY) {
        if (!hasSelection()) return;
        
        int selectionStartPos = Math.min(selectionStart, cursorPos);
        int selectionEndPos = Math.max(selectionStart, cursorPos);
        
        int start = Math.max(selectionStartPos, line.start);
        int end = Math.min(selectionEndPos, line.end);
        
        if (start >= end) {
            return;
        }
        
        int startLocal = start - line.start;
        int endLocal = end - line.start;
        
        String before = line.text.substring(0, startLocal);
        String selected = line.text.substring(startLocal, endLocal);
        
        int x1 = getX() + 4 + font.width(before);
        int x2 = x1 + font.width(selected);
        
        gfx.fill(
                x1,
                lineY,
                x2,
                lineY + font.lineHeight,
                0xFF5555AA
        );
    }
    
    private String getSelectedText() {
        int start = Math.min(selectionStart, cursorPos);
        int end = Math.max(selectionStart, cursorPos);
        return text.substring(start, end);
    }
    
    private void deleteSelection() {
        if (!hasSelection()) {
            return;
        }
        
        int start = Math.min(selectionStart, cursorPos);
        int end = Math.max(selectionStart, cursorPos);
        
        text.delete(start, end);
        cursorPos = start;
        selectionStart = -1;
    }
    
    private record LineInfo(
            int start,
            int end,
            String text
    ) {}
}