package com.estie.mobarmory.client.gui.screen;

import com.estie.mobarmory.data.EntryToBuilder;
import com.estie.mobarmory.data.MobEquipmentBuilder;
import com.estie.mobarmory.data.MobEquipmentReloadListener;
import com.estie.mobarmory.data.MobEquipmentReloadListener.MobEquipmentEntry;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The whole editor in one screen: a collapsible tree on the left, an inspector for the selected
 * node on the right, the mob preview above it.
 * <p>
 * Mouse: click a row to select, click the arrow or double-click to expand/collapse,
 * click a green "+ Add ..." row to add, wheel scrolls (over the preview it zooms).
 * Keys:  Up/Down move, Left collapses (or goes to the parent), Right expands (or goes to the first child),
 *        Enter/Space toggles, Delete deletes the selected node, Esc leaves the text box / the editor.
 */
public class TreeEditorScreen extends Screen {
    
    private static final int ROW_H = 12;
    private static final int INDENT = 12;
    private static final int RIGHT_W = 190;
    private static final int LABEL_W = 64;
    private static final long STATUS_MS = 3000L;
    
    private record Row(EditorNode node, int depth) {}
    private record Label(String text, int x, int y) {}
    
    private final MobEquipmentEntry entry;
    private EditorNode root;
    private EditorNode selected;
    private Object selectedKey;
    private final Set<Object> expanded = new HashSet<>();
    private final List<Row> rows = new ArrayList<>();
    
    private int scroll;
    private int lastClickRow = -1;
    private long lastClickTime;
    
    //layout (computed in init)
    private int treeX, treeY, treeW, treeH, rightX, previewSize, inspectorTitleY, messageY;
    private final List<EditBox> inspectorBoxes = new ArrayList<>();
    private final List<Label> fieldLabels = new ArrayList<>();
    
    private boolean previewBuilt;
    private String snapshot;
    
    private String message = "";
    private int messageColor = 0xFFAA00;
    private String status = "";
    private int statusColor = 0xFFFFFF;
    private long statusUntil;
    
    private final EditorTree.Hooks hooks = new EditorTree.Hooks() {
        @Override
        public void prompt(String title, String initial, Predicate<String> validator, String warning, Consumer<String> onConfirm) {
            minecraft.setScreen(new TextInputScreen(TreeEditorScreen.this, title, initial, onConfirm,
                    validator, warning, false));
        }
        
        @Override
        public void changed(Object selectKey) {
            rebuildTree(selectKey);
            rebuildWidgets();
        }
        
        @Override
        public void mobChanged() {
            EditScreenShared.rebuildPreviewEntity(entry, minecraft.level);
        }
    };
    
    public TreeEditorScreen(MobEquipmentEntry entry) {
        super(Component.literal(entry.fileName != null ? "Edit: " + entry.fileName : "New Mob Equipment Entry"));
        this.entry = entry;
        EditorTree.normalize(entry);
        this.root = EditorTree.build(entry, hooks);
        this.selected = root;
        this.selectedKey = root.key;
        this.expanded.add(root.key);
        rebuildRows();
        this.snapshot = currentJson();
    }
    
    //tree state
    
    private void rebuildTree(Object selectKey) {
        Object want = selectKey != null ? selectKey : selectedKey;
        root = EditorTree.build(entry, hooks);
        
        EditorNode found = find(root, want);
        if (found == null) found = root; //what we had selected was deleted
        for (EditorNode p = found.parent; p != null; p = p.parent) expanded.add(p.key);
        
        selected = found;
        selectedKey = found.key;
        message = "";
        rebuildRows();
        scrollToSelected();
    }
    
    private static EditorNode find(EditorNode node, Object key) {
        if (node.key.equals(key)) return node;
        for (EditorNode c : node.children) {
            EditorNode hit = find(c, key);
            if (hit != null) return hit;
        }
        return null;
    }
    
    private void rebuildRows() {
        rows.clear();
        addRows(root, 0);
        clampScroll();
    }
    
    private void addRows(EditorNode node, int depth) {
        rows.add(new Row(node, depth));
        if (expanded.contains(node.key)) {
            for (EditorNode c : node.children) addRows(c, depth + 1);
        }
    }
    
    private int indexOf(EditorNode node) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).node() == node) return i;
        }
        return -1;
    }
    
    private void select(EditorNode node) {
        selected = node;
        selectedKey = node.key;
        message = "";
        scrollToSelected();
        rebuildWidgets();
    }
    
    private void toggle(EditorNode node) {
        if (node.children.isEmpty()) return;
        if (!expanded.remove(node.key)) expanded.add(node.key);
        rebuildRows();
        if (indexOf(selected) < 0) select(node); //selection was inside the node we just collapsed
    }
    
    private void expandAll(EditorNode node) {
        if (!node.children.isEmpty()) expanded.add(node.key);
        for (EditorNode c : node.children) expandAll(c);
    }
    
    private void collapseAll() {
        expanded.clear();
        expanded.add(root.key);
        rebuildRows();
        if (indexOf(selected) < 0) select(root);
    }
    
    private int maxScroll() {
        return Math.max(0, rows.size() * ROW_H - treeH);
    }
    
    private void clampScroll() {
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }
    
    private void scrollToSelected() {
        int idx = indexOf(selected);
        if (idx < 0) return;
        int top = idx * ROW_H;
        if (top < scroll) scroll = top;
        else if (top + ROW_H > scroll + treeH) scroll = top + ROW_H - treeH;
        clampScroll();
    }
    
    //widgets
    
    @Override
    protected void init() {
        if (!previewBuilt) {
            EditScreenShared.rebuildPreviewEntity(entry, this.minecraft.level);
            previewBuilt = true;
        }
        
        rightX = this.width - RIGHT_W - 10;
        treeX = 10;
        treeY = 40;
        treeW = rightX - 10 - treeX;
        treeH = this.height - 48 - treeY;
        clampScroll();
        
        //the preview shrinks to make room for the inspector
        int needed = 0;
        if (selected != null) {
            needed = 14 + selected.actions.size() * 22 + 16;
            for (EditorNode.Field f : selected.fields) needed += fieldHeight(f);
        }
        previewSize = Mth.clamp(this.height - 10 - 74 - needed, 36, 100);
        
        inspectorBoxes.clear();
        fieldLabels.clear();
        
        int y = 60 + previewSize + 16;
        inspectorTitleY = y;
        y += 14;
        
        if (selected != null) {
            for (EditorNode.Field f : selected.fields) {
                if (f.kind == EditorNode.Kind.TOGGLES) {
                    fieldLabels.add(new Label(f.label, rightX, y + 2));
                    addToggleButtons(f, y + 12);
                } else {
                    fieldLabels.add(new Label(f.label, rightX, y + 4));
                    addFieldWidget(f, rightX + LABEL_W, y, RIGHT_W - LABEL_W);
                }
                y += fieldHeight(f);
            }
            for (EditorNode.Action a : selected.actions) {
                this.addRenderableWidget(Button.builder(Component.literal(a.label()), btn -> runAction(a))
                        .bounds(rightX, y, RIGHT_W, 18).build());
                y += 22;
            }
        }
        messageY = y + 2;
        
        //bottom left: save + tree helpers
        int by = this.height - 26;
        this.addRenderableWidget(Button.builder(Component.literal("Save"), btn -> save())
                .bounds(treeX, by, 60, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Save As..."), btn -> saveAs())
                .bounds(treeX + 64, by, 70, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Expand"), btn -> {
            expandAll(root);
            rebuildRows();
        }).bounds(treeX + 138, by, 50, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Collapse"), btn -> collapseAll())
                .bounds(treeX + 192, by, 56, 20).build());
    }
    
    private static int fieldHeight(EditorNode.Field f) {
        if (f.kind != EditorNode.Kind.TOGGLES) return 22;
        return 12 + ((f.options.size() + 2) / 3) * 20 + 4; //label line + rows of three + gap
    }
    
    //green = on, red = off
    private void addToggleButtons(EditorNode.Field f, int top) {
        int perRow = 3;
        int gap = 2;
        int w = (RIGHT_W - gap * (perRow - 1)) / perRow;
        for (int i = 0; i < f.options.size(); i++) {
            final int index = i;
            int bx = rightX + (i % perRow) * (w + gap);
            int by = top + (i / perRow) * 20;
            Button button = Button.builder(Component.literal(f.options.get(i)), btn -> f.toggle.accept(index))
                    .bounds(bx, by, w, 18).build();
            button.setFGColor(f.isOn.test(i) ? 0x55FF55 : 0xFF5555);
            this.addRenderableWidget(button);
        }
    }
    
    private void addFieldWidget(EditorNode.Field f, int x, int y, int w) {
        switch (f.kind) {
            case TEXT -> {
                EditBox box = new EditBox(this.font, x, y, w, 16, Component.literal(f.label));
                box.setMaxLength(512);
                box.setValue(f.get.get());
                if (!f.hint.isEmpty()) box.setHint(Component.literal(f.hint));
                box.setResponder(v -> onFieldEdited(box, f, v));
                this.addRenderableWidget(box);
                inspectorBoxes.add(box);
            }
            case MULTILINE -> {
                String value = f.get.get();
                String text = f.buttonText != null
                        ? f.buttonText.apply(value)
                        : (value.isBlank() ? "(none) - edit..." : "edit... (" + value.length() + " chars)");
                this.addRenderableWidget(Button.builder(Component.literal(text), btn ->
                        this.minecraft.setScreen(new TextInputScreen(this, f.modalTitle, f.get.get(),
                                v -> {
                                    String error = f.set.apply(v);
                                    message = error != null ? error : "";
                                    messageColor = 0xFF5555;
                                }, f.modalValidator, f.modalWarning, true, true))
                ).bounds(x, y, w, 16).build());
            }
            case CYCLE -> this.addRenderableWidget(Button.builder(Component.literal(f.get.get()), btn -> f.cycle.run())
                    .bounds(x, y, w, 16).build());
        }
    }
    
    private void onFieldEdited(EditBox box, EditorNode.Field f, String value) {
        String error = f.set.apply(value);
        if (error != null) {
            box.setTextColor(0xFF5555);
            message = error;
            messageColor = 0xFF5555;
            return;
        }
        box.setTextColor(0xE0E0E0);
        String warning = f.warner != null ? f.warner.apply(value) : null;
        message = warning != null ? warning : "";
        messageColor = 0xFFAA00;
    }
    
    private void runAction(EditorNode.Action action) {
        if (action.confirm() == null) {
            action.run().run();
            return;
        }
        this.minecraft.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) action.run().run(); //changed() rebuilds the widgets
            this.minecraft.setScreen(this);
        }, Component.literal(action.label()), Component.literal(action.confirm())));
    }
    
    //saving
    
    private String currentJson() {
        try {
            return MobEquipmentReloadListener.toJson(entry).toString();
        } catch (Exception e) {
            return "unserialisable-" + System.nanoTime();
        }
    }
    
    private void save() {
        if (entry.fileName == null || entry.fileName.isBlank()) {
            saveAs();
            return;
        }
        doSave();
    }
    
    private void saveAs() {
        String initial = entry.fileName != null ? entry.fileName : "";
        this.minecraft.setScreen(new TextInputScreen(this, "Save As...", initial, name -> {
            entry.fileName = name;
            doSave();
        }));
    }
    
    private void doSave() {
        MobEquipmentBuilder.SaveResult result = EntryToBuilder.toBuilder(entry).createFile(entry.fileName);
        if (result.success) {
            MobEquipmentReloadListener.addOrReplaceLookupFile(entry);
            snapshot = currentJson();
            setStatus("Saved " + result.path.getFileName(), 0x55FF55);
        } else {
            setStatus("Save failed: " + result.error.getMessage(), 0xFF5555);
        }
    }
    
    private void setStatus(String text, int color) {
        status = text;
        statusColor = color;
        statusUntil = Util.getMillis() + STATUS_MS;
    }
    
    //rendering
    
    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx);
        renderTree(gfx, mouseX, mouseY);
        super.render(gfx, mouseX, mouseY, partialTick);
        
        EditScreenShared.renderHeader(gfx, font, entry, width, previewSize, pathTrail());
        renderInspectorText(gfx);
        
        if (Util.getMillis() < statusUntil) {
            gfx.drawCenteredString(font, status, treeX + treeW / 2, this.height - 40, statusColor);
        }
    }
    
    private List<EditScreenShared.Crumb> pathTrail() {
        List<String> labels = new ArrayList<>();
        for (EditorNode p = selected; p != null; p = p.parent) labels.add(0, shorten(p.label.get(), 26));
        
        List<EditScreenShared.Crumb> trail = new ArrayList<>();
        int from = Math.max(0, labels.size() - 3);
        if (from > 0) trail.add(EditScreenShared.current("..."));
        for (int i = from; i < labels.size(); i++) trail.add(EditScreenShared.current(labels.get(i)));
        return trail;
    }
    
    private static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "...";
    }
    
    private void renderTree(GuiGraphics gfx, int mouseX, int mouseY) {
        gfx.fill(treeX, treeY, treeX + treeW, treeY + treeH, 0x80000000);
        gfx.enableScissor(treeX, treeY, treeX + treeW, treeY + treeH);
        
        for (int i = 0; i < rows.size(); i++) {
            int y = treeY + i * ROW_H - scroll;
            if (y + ROW_H < treeY || y > treeY + treeH) continue;
            
            Row row = rows.get(i);
            EditorNode node = row.node();
            boolean hovered = mouseX >= treeX && mouseX < treeX + treeW && mouseY >= y && mouseY < y + ROW_H
                    && mouseY >= treeY && mouseY < treeY + treeH;
            
            if (node == selected) gfx.fill(treeX, y, treeX + treeW - 5, y + ROW_H, 0x80336699);
            else if (hovered) gfx.fill(treeX, y, treeX + treeW - 5, y + ROW_H, 0x30FFFFFF);
            
            int x = treeX + 4 + row.depth() * INDENT;
            if (node.isActionRow()) {
                gfx.drawString(font, node.label.get(), x + 10, y + 2, 0x77DD77);
                continue;
            }
            
            if (!node.children.isEmpty()) {
                gfx.drawString(font, expanded.contains(node.key) ? "v" : ">", x, y + 2, 0xAAAAAA);
            }
            
            String label = node.label.get();
            int labelX = x + 10;
            int maxW = treeX + treeW - 8 - labelX;
            String shownLabel = font.plainSubstrByWidth(label, maxW);
            gfx.drawString(font, shownLabel, labelX, y + 2, 0xFFFFFF);
            
            String summary = node.summary.get();
            if (!summary.isEmpty() && font.width(label) < maxW - 12) {
                int sx = labelX + font.width(label) + 8;
                gfx.drawString(font, font.plainSubstrByWidth(summary, treeX + treeW - 8 - sx), sx, y + 2, 0x888888);
            }
        }
        gfx.disableScissor();
        
        int max = maxScroll();
        if (max > 0) {
            int barH = Math.max(12, treeH * treeH / (rows.size() * ROW_H));
            int barY = treeY + (int) ((long) scroll * (treeH - barH) / max);
            gfx.fill(treeX + treeW - 4, barY, treeX + treeW - 1, barY + barH, 0xFF888888);
        }
    }
    
    private void renderInspectorText(GuiGraphics gfx) {
        if (selected == null) return;
        gfx.drawString(font, font.plainSubstrByWidth(selected.label.get(), RIGHT_W), rightX, inspectorTitleY, 0xFFFF55);
        for (Label l : fieldLabels) {
            gfx.drawString(font, l.text(), l.x(), l.y(), 0xAAAAAA);
        }
        if (!message.isEmpty()) {
            gfx.drawString(font, font.plainSubstrByWidth(message, RIGHT_W), rightX, messageY, messageColor);
        }
    }
    
    //input
    
    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
    
    private boolean typing() {
        for (EditBox box : inspectorBoxes) {
            if (box.isFocused()) return true;
        }
        return false;
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (typing()) {
                for (EditBox box : inspectorBoxes) box.setFocused(false);
                this.setFocused(null);
            } else {
                exit();
            }
            return true;
        }
        
        if (!typing()) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_UP -> { move(-1); return true; }
                case GLFW.GLFW_KEY_DOWN -> { move(1); return true; }
                case GLFW.GLFW_KEY_LEFT -> { left(); return true; }
                case GLFW.GLFW_KEY_RIGHT -> { right(); return true; }
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> {
                    toggle(selected);
                    return true;
                }
                case GLFW.GLFW_KEY_DELETE -> {
                    for (EditorNode.Action a : selected.actions) {
                        if (a.label().startsWith("Delete")) {
                            runAction(a);
                            break;
                        }
                    }
                    return true;
                }
                default -> { }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    //moves the selection to the next/previous selectable row (add rows are skipped)
    private void move(int dir) {
        int idx = indexOf(selected);
        for (int i = idx + dir; i >= 0 && i < rows.size(); i += dir) {
            if (!rows.get(i).node().isActionRow()) {
                select(rows.get(i).node());
                return;
            }
        }
    }
    
    private void left() {
        if (!selected.children.isEmpty() && expanded.contains(selected.key)) toggle(selected);
        else if (selected.parent != null) select(selected.parent);
    }
    
    private void right() {
        if (selected.children.isEmpty()) return;
        if (!expanded.contains(selected.key)) {
            toggle(selected);
            return;
        }
        for (EditorNode c : selected.children) {
            if (!c.isActionRow()) {
                select(c);
                return;
            }
        }
    }
    
    private void exit() {
        if (currentJson().equals(snapshot)) {
            this.minecraft.setScreen(null);
            return;
        }
        this.minecraft.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) this.minecraft.setScreen(null);
            else this.minecraft.setScreen(this);
        }, Component.literal("Exit Editor"), Component.literal("You have unsaved changes. Exit anyway?")));
    }
    
    private boolean inTree(double mx, double my) {
        return mx >= treeX && mx < treeX + treeW && my >= treeY && my < treeY + treeH;
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (EditScreenShared.mouseClicked(mouseX, mouseY, button)) return true;
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (inTree(mouseX, mouseY)) {
            clickTree(mouseX, mouseY);
            return true;
        }
        return false;
    }
    
    private void clickTree(double mouseX, double mouseY) {
        int idx = (int) ((mouseY - treeY + scroll) / ROW_H);
        if (idx < 0 || idx >= rows.size()) return;
        
        Row row = rows.get(idx);
        EditorNode node = row.node();
        if (node.isActionRow()) {
            node.activate.run();
            return;
        }
        
        int arrowX = treeX + 4 + row.depth() * INDENT;
        boolean onArrow = !node.children.isEmpty() && mouseX >= arrowX && mouseX < arrowX + 10;
        long now = Util.getMillis();
        boolean doubleClick = idx == lastClickRow && now - lastClickTime < 300L;
        lastClickRow = idx;
        lastClickTime = now;
        
        if (node != selected) select(node);
        if (onArrow || doubleClick) toggle(node);
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (EditScreenShared.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }
    
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (EditScreenShared.mouseReleased(mouseX, mouseY, button)) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (EditScreenShared.mouseScrolled(mouseX, mouseY, delta)) return true;
        if (inTree(mouseX, mouseY)) {
            scroll -= (int) (delta * ROW_H * 3);
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }
}