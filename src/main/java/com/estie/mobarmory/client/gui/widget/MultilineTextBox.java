package com.estie.mobarmory.client.gui.widget;

import net.minecraft.client.Minecraft;
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
    
    private int selectionStart = -1;
    private boolean dragging = false;
    
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
        selectionStart = -1;
    }
    
    public void setFocused(boolean focused) {
        this.focused = focused;
        
        if (!focused) {
            dragging = false;
        }
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
            
            drawSelection(line, lineY);
            
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
        boolean shift = GuiScreen.isShiftKeyDown();
        
        // Ctrl+A/C/X/V
        if (ctrl) {
            switch (keyCode) {
                case Keyboard.KEY_A:
                    selectionStart = 0;
                    cursorPos = text.length();
                    return true;
                
                case Keyboard.KEY_C:
                    if (hasSelection()) {
                        GuiScreen.setClipboardString(getSelectedText());
                    }
                    return true;
                
                case Keyboard.KEY_X:
                    if (hasSelection()) {
                        GuiScreen.setClipboardString(getSelectedText());
                        deleteSelection();
                    }
                    return true;
                
                case Keyboard.KEY_V:
                    String clipboard = GuiScreen.getClipboardString();
                    
                    if (clipboard != null && !clipboard.isEmpty()) {
                        clipboard = clipboard
                                .replace("\r\n", "\n")
                                .replace('\r', '\n');
                        
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
        
        switch (keyCode) {
            case Keyboard.KEY_BACK:
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
            
            case Keyboard.KEY_DELETE:
                if (hasSelection()) {
                    deleteSelection();
                } else if (cursorPos < text.length()) {
                    int end = ctrl
                            ? getNextWordBoundary(cursorPos)
                            : cursorPos + 1;
                    
                    text.delete(cursorPos, end);
                }
                
                return true;
            
            case Keyboard.KEY_LEFT:
                moveCursorHorizontal(
                        ctrl
                                ? getPreviousWordBoundary(cursorPos)
                                : Math.max(0, cursorPos - 1),
                        shift
                );
                return true;
            
            case Keyboard.KEY_RIGHT:
                moveCursorHorizontal(
                        ctrl
                                ? getNextWordBoundary(cursorPos)
                                : Math.min(text.length(), cursorPos + 1),
                        shift
                );
                return true;
            
            case Keyboard.KEY_HOME:
                moveCursorHorizontal(getLineStart(cursorPos), shift);
                return true;
            
            case Keyboard.KEY_END:
                moveCursorHorizontal(getLineEnd(cursorPos), shift);
                return true;
            
            case Keyboard.KEY_UP:
                moveCursorVertical(-1, shift);
                return true;
            
            case Keyboard.KEY_DOWN:
                moveCursorVertical(1, shift);
                return true;
            
            case Keyboard.KEY_RETURN:
                if (hasSelection()) {
                    deleteSelection();
                }
                
                if (text.length() < MAX_LENGTH) {
                    text.insert(cursorPos, '\n');
                    cursorPos++;
                }
                
                return true;
            
            case Keyboard.KEY_ESCAPE:
                focused = false;
                dragging = false;
                return true;
        }
        
        if (typedChar >= 32) {
            if (hasSelection()) {
                deleteSelection();
            }
            
            if (text.length() < MAX_LENGTH) {
                text.insert(cursorPos, typedChar);
                cursorPos++;
                return true;
            }
        }
        
        return false;
    }
    
    public void mouseDragged(int mouseX, int mouseY) {
        if (!dragging) {
            return;
        }
        
        if (mouseX < x || mouseX >= x + width
                || mouseY < y || mouseY >= y + height) {
            return;
        }
        
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
    
    private void moveCursorHorizontal(int newPos, boolean shift) {
        if (shift) {
            if (selectionStart == -1) {
                selectionStart = cursorPos;
            }
        } else {
            selectionStart = -1;
        }
        
        cursorPos = newPos;
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
    
    private void moveCursorVertical(int direction, boolean shift) {
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
        
        int newPos = target.start + targetOffset;
        
        if (shift) {
            if (selectionStart == -1) {
                selectionStart = cursorPos;
            }
        } else {
            selectionStart = -1;
        }
        
        cursorPos = newPos;
    }
    
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0) {
            return;
        }
        
        if (mouseX < x || mouseX >= x + width
                || mouseY < y || mouseY >= y + height) {
            focused = false;
            dragging = false;
            return;
        }
        
        focused = true;
        dragging = true;
        
        List<LineInfo> lines = getLines();
        
        if (lines.isEmpty()) {
            cursorPos = 0;
            selectionStart = cursorPos;
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
        selectionStart = cursorPos;
    }
    
    public void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (!dragging || clickedMouseButton != 0) {
            return;
        }
        
        List<LineInfo> lines = getLines();
        
        if (lines.isEmpty()) {
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
        
        int bestPos = line.start;
        int bestDistance = Integer.MAX_VALUE;
        
        if (relativeX > 0) {
            for (int i = 0; i <= line.text.length(); i++) {
                String before = line.text.substring(0, i);
                int stringWidth = font.getStringWidth(before);
                
                int distance = Math.abs(stringWidth - relativeX);
                
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestPos = line.start + i;
                }
            }
        }
        
        cursorPos = bestPos;
    }
    
    public void mouseReleased(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0) {
            dragging = false;
        }
    }
    
    private boolean hasSelection() {
        return selectionStart != -1 && selectionStart != cursorPos;
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
    
    private void drawSelection(LineInfo line, int lineY) {
        if (!hasSelection()) {
            return;
        }
        
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
        
        int x1 = x + 4 + font.getStringWidth(before);
        int x2 = x1 + font.getStringWidth(selected);
        
        Gui.drawRect(
                x1,
                lineY,
                x2,
                lineY + font.FONT_HEIGHT,
                0xFF5555AA
        );
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