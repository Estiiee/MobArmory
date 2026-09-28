package com.estie.mobarmory.client.gui.widget;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;

public class MultilineTextBox {
    private static final int MAX_LENGTH = 4096;
    
    private final FontRenderer font;
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    
    private final StringBuilder text = new StringBuilder();
    
    private int cursorPos = 0;
    private boolean focused = false;
    
    public MultilineTextBox(
            FontRenderer font,
            int x,
            int y,
            int width,
            int height,
            String initial) {
        
        this.font = font;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        
        if (initial != null) {
            text.append(initial, 0, Math.min(initial.length(), MAX_LENGTH));
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
    }
    
    public void setFocused(boolean focused) {
        this.focused = focused;
    }
    
    public boolean isFocused() {
        return focused;
    }
    
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
                List<String> wrapped = font.listFormattedStringToWidth(rawLine, innerWidth);
                
                int offset = 0;
                
                for (String line : wrapped) {
                    result.add(new LineInfo(
                            rawStart + offset,
                            rawStart + offset + line.length(),
                            line
                    ));
                    
                    offset += line.length();
                }
            }
            
            if (newline == -1) {
                break;
            }
            
            rawStart = newline + 1;
        }
        
        return result;
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
    
    public void draw() {
        Gui.drawRect(
                x,
                y,
                x + width,
                y + height,
                0xFF000000
        );
        
        int borderColor = focused ? 0xFFFFFFFF : 0xFF808080;
        
        Gui.drawRect(x, y, x + width, y + 1, borderColor);
        Gui.drawRect(x, y + height - 1, x + width, y + height, borderColor);
        Gui.drawRect(x, y, x + 1, y + height, borderColor);
        Gui.drawRect(x + width - 1, y, x + width, y + height, borderColor);
        
        List<LineInfo> lines = getLines();
        
        int lineY = y + 4;
        
        for (LineInfo line : lines) {
            if (lineY + font.FONT_HEIGHT > y + height) {
                break;
            }
            
            font.drawString(
                    line.text,
                    x + 4,
                    lineY,
                    0xFFFFFF
            );
            
            lineY += font.FONT_HEIGHT;
        }
        
        if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
            drawCursor(lines);
        }
    }
    
    private void drawCursor(List<LineInfo> lines) {
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
        
        int cursorX = x + 4 + font.getStringWidth(beforeCursor);
        int cursorY = y + 4 + lineIndex * font.FONT_HEIGHT;
        
        Gui.drawRect(
                cursorX,
                cursorY,
                cursorX + 1,
                cursorY + font.FONT_HEIGHT,
                0xFFFFFFFF
        );
    }
    
    public boolean keyTyped(char typedChar, int keyCode) {
        if (!focused) {
            return false;
        }
        
        boolean ctrl = GuiScreen.isCtrlKeyDown();
        
        switch (keyCode) {
            case Keyboard.KEY_BACK:
                if (cursorPos > 0) {
                    int start = ctrl
                            ? getPreviousWordBoundary(cursorPos)
                            : cursorPos - 1;
                    
                    text.delete(start, cursorPos);
                    cursorPos = start;
                }
                
                return true;
            
            case Keyboard.KEY_DELETE:
                if (cursorPos < text.length()) {
                    int end = ctrl
                            ? getNextWordBoundary(cursorPos)
                            : cursorPos + 1;
                    
                    text.delete(cursorPos, end);
                }
                
                return true;
            
            case Keyboard.KEY_LEFT:
                if (ctrl) {
                    cursorPos = getPreviousWordBoundary(cursorPos);
                } else if (cursorPos > 0) {
                    cursorPos--;
                }
                
                return true;
            
            case Keyboard.KEY_RIGHT:
                if (ctrl) {
                    cursorPos = getNextWordBoundary(cursorPos);
                } else if (cursorPos < text.length()) {
                    cursorPos++;
                }
                
                return true;
            
            case Keyboard.KEY_HOME:
                cursorPos = getLineStart(cursorPos);
                return true;
            
            case Keyboard.KEY_END:
                cursorPos = getLineEnd(cursorPos);
                return true;
            
            case Keyboard.KEY_UP:
                moveCursorVertical(-1);
                return true;
            
            case Keyboard.KEY_DOWN:
                moveCursorVertical(1);
                return true;
            
            case Keyboard.KEY_RETURN:
                if (text.length() < MAX_LENGTH) {
                    text.insert(cursorPos, '\n');
                    cursorPos++;
                }
                
                return true;
            
            case Keyboard.KEY_ESCAPE:
                focused = false;
                return true;
        }
        
        if (typedChar >= 32 && text.length() < MAX_LENGTH) {
            text.insert(cursorPos, typedChar);
            cursorPos++;
            return true;
        }
        
        return false;
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
    
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0) {
            return;
        }
        
        if (mouseX < x || mouseX >= x + width
                || mouseY < y || mouseY >= y + height) {
            focused = false;
            return;
        }
        
        focused = true;
        
        List<LineInfo> lines = getLines();
        
        if (lines.isEmpty()) {
            cursorPos = 0;
            return;
        }
        
        int clickedLine = (mouseY - y - 4) / font.FONT_HEIGHT;
        
        clickedLine = MathHelper.clamp(
                clickedLine,
                0,
                lines.size() - 1
        );
        
        LineInfo line = lines.get(clickedLine);
        
        int relativeX = mouseX - x - 4;
        
        if (relativeX <= 0) {
            cursorPos = line.start;
            return;
        }
        
        int bestPos = line.start;
        int bestDistance = Integer.MAX_VALUE;
        
        for (int i = 0; i <= line.text.length(); i++) {
            String before = line.text.substring(0, i);
            int stringWidth = font.getStringWidth(before);
            
            int distance = Math.abs(stringWidth - relativeX);
            
            if (distance < bestDistance) {
                bestDistance = distance;
                bestPos = line.start + i;
            }
        }
        
        cursorPos = bestPos;
    }
    
    private static class LineInfo {
        final int start;
        final int end;
        final String text;
        
        LineInfo(int start, int end, String text) {
            this.start = start;
            this.end = end;
            this.text = text;
        }
    }
}