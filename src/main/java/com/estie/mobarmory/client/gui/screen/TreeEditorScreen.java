package com.estie.mobarmory.client.gui.screen;

import com.estie.mobarmory.data.EntryToBuilder;
import com.estie.mobarmory.data.MobEquipmentBuilder;
import com.estie.mobarmory.data.MobEquipmentReloadListener;
import com.estie.mobarmory.data.MobEquipmentReloadListener.MobEquipmentEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
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
 * click a green "+ Add" row to add, wheel scrolls (over the preview it zooms).
 * Keys:  Up/Down move, Left collapses (or goes to the parent), Right expands (or goes to the first child),
 *        Enter/Space toggles, Delete deletes the selected node, Esc leaves the text box / the editor.
 */
public class TreeEditorScreen extends GuiScreen {
    
    private static final int INDENT = 12;
    private static final int RIGHT_W = 190;
    private static final int LABEL_W = 64;
    private static final long STATUS_MS = 3000L;
    
    private static final class Row {
        final EditorNode node;
        final int depth;
        
        Row(EditorNode node, int depth) {
            this.node = node;
            this.depth = depth;
        }
    }
    
    private static final class Label {
        final String text;
        final int x;
        final int y;
        
        Label(String text, int x, int y) {
            this.text = text;
            this.x = x;
            this.y = y;
        }
    }
    
    //an inspector text box together with the field it edits
    private static final class FieldBox {
        final GuiTextField box;
        final EditorNode.Field field;
        
        FieldBox(GuiTextField box, EditorNode.Field field) {
            this.box = box;
            this.field = field;
        }
    }
    
    //a button that carries its own click action
    private static final class TreeButton extends GuiButton {
        final Runnable action;
        
        TreeButton(int id, int x, int y, int width, int height, String text, Runnable action) {
            super(id, x, y, width, height, text);
            this.action = action;
        }
    }
    
    private final MobEquipmentEntry entry;
    private EditorNode root;
    private EditorNode selected;
    private Object selectedKey;
    private final Set<Object> expanded = new HashSet<Object>();
    private final List<Row> rows = new ArrayList<Row>();
    
    private int scroll;
    private int lastClickRow = -1;
    private long lastClickTime;
    
    //layout (computed in initGui)
    private int rowH = 12;
    private boolean icons;
    private int treeX, treeY, treeW, treeH, rightX, previewSize, inspectorTitleY, messageY;
    private final List<FieldBox> inspectorBoxes = new ArrayList<FieldBox>();
    private final List<Label> fieldLabels = new ArrayList<Label>();
    
    private boolean previewBuilt;
    private boolean dirty;
    private int tickCounter;
    private String snapshot;
    
    private String message = "";
    private int messageColor = 0xFFAA00;
    private String status = "";
    private int statusColor = 0xFFFFFF;
    private long statusUntil;
    
    private final EditorTree.Hooks hooks = new EditorTree.Hooks() {
        @Override
        public void prompt(String title, String initialText, Predicate<String> validator, String warning,
                           Consumer<String> onConfirm) {
            mc.displayGuiScreen(new TextInputScreen(TreeEditorScreen.this, title, initialText, onConfirm,
                    validator, warning, false));
        }
        
        @Override
        public void changed(Object selectKey) {
            rebuildTree(selectKey);
            initGui();
            EditScreenShared.refreshPreview();
        }
        
        @Override
        public void mobChanged() {
            EditScreenShared.rebuildPreviewEntity(entry, mc.world);
            updatePreviewTarget();
        }
    };
    
    public TreeEditorScreen(MobEquipmentEntry entry) {
        this.entry = entry;
        EditorTree.normalize(entry);
        this.root = EditorTree.build(entry, hooks);
        this.selected = root;
        this.selectedKey = root.key;
        this.expanded.add(root.key);
        rebuildRows();
        this.snapshot = currentJson();
        updatePreviewTarget();
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
        updatePreviewTarget();
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
            if (rows.get(i).node == node) return i;
        }
        return -1;
    }
    
    //the preview cycles through the sets (and replacement mobs) under the current selection
    private void updatePreviewTarget() {
        EditScreenShared.setPreviewTarget(EditorTree.previewSets(selected), EditorTree.previewMobs(selected));
    }
    
    private void select(EditorNode node) {
        selected = node;
        selectedKey = node.key;
        message = "";
        scrollToSelected();
        updatePreviewTarget();
        initGui();
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
        return Math.max(0, rows.size() * rowH - treeH);
    }
    
    private void clampScroll() {
        scroll = MathHelper.clamp(scroll, 0, maxScroll());
    }
    
    private void scrollToSelected() {
        int idx = indexOf(selected);
        if (idx < 0) return;
        int top = idx * rowH;
        if (top < scroll) scroll = top;
        else if (top + rowH > scroll + treeH) scroll = top + rowH - treeH;
        clampScroll();
    }
    
    //widgets
    
    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        inspectorBoxes.clear();
        fieldLabels.clear();
        
        if (!previewBuilt) {
            EditScreenShared.rebuildPreviewEntity(entry, mc.world);
            previewBuilt = true;
        }
        
        rightX = this.width - RIGHT_W - 10;
        treeX = 10;
        treeY = 40;
        treeW = rightX - 10 - treeX;
        treeH = this.height - 48 - treeY;
        icons = this.height >= 300; //icons need 16px rows; the compact layout keeps 12px rows and no icons
        rowH = icons ? 16 : 12;
        clampScroll();
        
        //the preview shrinks to make room for the inspector (the header draws it at y = 60)
        int needed = 0;
        if (selected != null) {
            needed = 14 + selected.actions.size() * 20 + 16;
            for (EditorNode.Field f : selected.fields) needed += fieldHeight(f);
        }
        previewSize = MathHelper.clamp(this.height - 10 - 74 - needed, 36, 100);
        
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
            for (final EditorNode.Action a : selected.actions) {
                TreeButton actionButton = new TreeButton(nextId(), rightX, y, RIGHT_W, 18, a.label(), new Runnable() {
                    @Override
                    public void run() {
                        runAction(a);
                    }
                });
                if (a.label().startsWith("Delete")) actionButton.packedFGColour = 0xFF7777;
                buttonList.add(actionButton);
                y += 20;
            }
        }
        messageY = y + 2;
        
        //bottom-left: save + tree helpers
        int by = this.height - 26;
        buttonList.add(new TreeButton(nextId(), treeX, by, 60, 20, "Save", new Runnable() {
            @Override
            public void run() {
                save();
            }
        }));
        buttonList.add(new TreeButton(nextId(), treeX + 64, by, 70, 20, "Save As...", new Runnable() {
            @Override
            public void run() {
                saveAs();
            }
        }));
        buttonList.add(new TreeButton(nextId(), treeX + 138, by, 50, 20, "Expand", new Runnable() {
            @Override
            public void run() {
                expandAll(root);
                rebuildRows();
            }
        }));
        buttonList.add(new TreeButton(nextId(), treeX + 192, by, 56, 20, "Collapse", new Runnable() {
            @Override
            public void run() {
                collapseAll();
            }
        }));
    }
    
    private int nextId() {
        return buttonList.size() + inspectorBoxes.size();
    }
    
    private static int fieldHeight(EditorNode.Field f) {
        if (f.kind != EditorNode.Kind.TOGGLES) return 20;
        return 12 + ((f.options.size() + 2) / 3) * 20 + 4; //label line + rows of three + gap
    }
    
    //green = on, red = off, three per row across the whole inspector width
    private void addToggleButtons(final EditorNode.Field f, int top) {
        int perRow = 3;
        int gap = 2;
        int w = (RIGHT_W - gap * (perRow - 1)) / perRow;
        for (int i = 0; i < f.options.size(); i++) {
            final int index = i;
            int bx = rightX + (i % perRow) * (w + gap);
            int by = top + (i / perRow) * 20;
            TreeButton button = new TreeButton(nextId(), bx, by, w, 18, f.options.get(i), new Runnable() {
                @Override
                public void run() {
                    f.toggle.accept(index);
                }
            });
            button.packedFGColour = f.isOn.test(i) ? 0x55FF55 : 0xFF5555;
            buttonList.add(button);
        }
    }
    
    private void addFieldWidget(final EditorNode.Field f, int x, int y, int w) {
        switch (f.kind) {
            case TEXT: {
                GuiTextField box = new GuiTextField(nextId(), fontRenderer, x, y, w, 16);
                box.setMaxStringLength(512);
                box.setText(f.get.get());
                inspectorBoxes.add(new FieldBox(box, f));
                break;
            }
            case MULTILINE: {
                String value = f.get.get();
                String text = f.buttonText != null
                        ? f.buttonText.apply(value)
                        : (isBlank(value) ? "(none) - edit..." : "edit... (" + value.length() + " chars)");
                buttonList.add(new TreeButton(nextId(), x, y, w, 16, fit(text, w - 8), new Runnable() {
                    @Override
                    public void run() {
                        mc.displayGuiScreen(new TextInputScreen(TreeEditorScreen.this, f.modalTitle, f.get.get(),
                                new Consumer<String>() {
                                    @Override
                                    public void accept(String v) {
                                        String error = f.set.apply(v);
                                        message = error != null ? error : "";
                                        messageColor = 0xFF5555;
                                    }
                                }, f.modalValidator, f.modalWarning, true, true));
                    }
                }));
                break;
            }
            case CYCLE: {
                buttonList.add(new TreeButton(nextId(), x, y, w, 16, fit(f.get.get(), w - 8), new Runnable() {
                    @Override
                    public void run() {
                        f.cycle.run();
                    }
                }));
                break;
            }
            default:
                break;
        }
    }
    
    private String fit(String label, int maxWidth) {
        if (fontRenderer.getStringWidth(label) <= maxWidth) return label;
        return fontRenderer.trimStringToWidth(label, maxWidth - fontRenderer.getStringWidth("...")) + "...";
    }
    
    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
    
    private void onFieldEdited(FieldBox fb, String value) {
        String error = fb.field.set.apply(value);
        if (error != null) {
            fb.box.setTextColor(0xFF5555);
            message = error;
            messageColor = 0xFF5555;
            return;
        }
        fb.box.setTextColor(0xE0E0E0);
        String warning = fb.field.warner != null ? fb.field.warner.apply(value) : null;
        message = warning != null ? warning : "";
        messageColor = 0xFFAA00;
    }
    
    private void runAction(final EditorNode.Action action) {
        if (action.confirm() == null) {
            action.run().run();
            return;
        }
        mc.displayGuiScreen(new GuiYesNo((result, id) -> {
            if (result) action.run().run(); //changed() rebuilds the widgets
            mc.displayGuiScreen(this);
        }, action.label(), action.confirm(), 0));
    }
    
    //saving
    
    private String currentJson() {
        try {
            return MobEquipmentReloadListener.toJson(entry).toString();
        } catch (Exception e) {
            return "unserialisable-" + System.nanoTime(); //always counts as changed
        }
    }
    
    private void save() {
        if (isBlank(entry.fileName)) {
            saveAs();
            return;
        }
        doSave();
    }
    
    private void saveAs() {
        String initial = entry.fileName != null ? entry.fileName : "";
        mc.displayGuiScreen(new TextInputScreen(this, "Save As...", initial, new Consumer<String>() {
            @Override
            public void accept(String name) {
                entry.fileName = name;
                doSave();
            }
        }));
    }
    
    private void doSave() {
        MobEquipmentBuilder.SaveResult result = EntryToBuilder.toBuilder(entry).createFile(entry.fileName);
        if (result.success) {
            MobEquipmentReloadListener.addOrReplaceLookupFile(entry);
            snapshot = currentJson();
            dirty = false;
            setStatus("Saved " + result.path.getFileName(), 0x55FF55);
        } else {
            setStatus("Save failed: " + result.error.getMessage(), 0xFF5555);
        }
    }
    
    private void setStatus(String text, int color) {
        status = text;
        statusColor = color;
        statusUntil = Minecraft.getSystemTime() + STATUS_MS;
    }
    
    //rendering
    
    @Override
    public void updateScreen() {
        super.updateScreen();
        for (FieldBox fb : inspectorBoxes) fb.box.updateCursorCounter();
        if (++tickCounter % 10 == 0) dirty = !currentJson().equals(snapshot);
    }
    
    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        renderTree(mouseX, mouseY);
        renderInspectorPanel();
        super.drawScreen(mouseX, mouseY, partialTicks); //buttons
        
        for (FieldBox fb : inspectorBoxes) {
            fb.box.drawTextBox();
            if (fb.box.getText().isEmpty() && !fb.box.isFocused() && !fb.field.hint.isEmpty()) {
                fontRenderer.drawString(fit(fb.field.hint, fb.box.width - 8), fb.box.x + 4, fb.box.y + 4, 0x707070);
            }
        }
        
        EditScreenShared.renderHeader(mc, entry, width, previewSize, pathTrail());
        renderInspectorText();
        
        if (dirty) fontRenderer.drawString("* unsaved changes", treeX, 15, 0xFFAA00);
        
        if (Minecraft.getSystemTime() < statusUntil) {
            fontRenderer.drawString(status, treeX + treeW / 2 - fontRenderer.getStringWidth(status) / 2,
                    this.height - 40, statusColor);
        }
    }
    
    private List<EditScreenShared.Crumb> pathTrail() {
        List<String> labels = new ArrayList<String>();
        for (EditorNode p = selected; p != null; p = p.parent) labels.add(0, shorten(p.label.get(), 26));
        
        List<EditScreenShared.Crumb> trail = new ArrayList<EditScreenShared.Crumb>();
        int from = Math.max(0, labels.size() - 3);
        if (from > 0) trail.add(EditScreenShared.current("..."));
        for (int i = from; i < labels.size(); i++) trail.add(EditScreenShared.current(labels.get(i)));
        return trail;
    }
    
    private static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "...";
    }
    
    private void renderTree(int mouseX, int mouseY) {
        Gui.drawRect(treeX - 1, treeY - 1, treeX + treeW + 1, treeY + treeH + 1, 0xFF101010);
        Gui.drawRect(treeX, treeY, treeX + treeW, treeY + treeH, 0xA0000000);
        enableScissor(treeX, treeY, treeW, treeH);
        
        int textY = (rowH - fontRenderer.FONT_HEIGHT) / 2 + 1;
        for (int i = 0; i < rows.size(); i++) {
            int y = treeY + i * rowH - scroll;
            if (y + rowH < treeY || y > treeY + treeH) continue;
            
            Row row = rows.get(i);
            EditorNode node = row.node;
            boolean hovered = mouseX >= treeX && mouseX < treeX + treeW && mouseY >= y && mouseY < y + rowH
                    && mouseY >= treeY && mouseY < treeY + treeH;
            
            if (node == selected) Gui.drawRect(treeX, y, treeX + treeW - 5, y + rowH, 0x80336699);
            else if (hovered) Gui.drawRect(treeX, y, treeX + treeW - 5, y + rowH, 0x30FFFFFF);
            
            //indent guides: a faint vertical line under each ancestor's arrow
            for (int d = 1; d <= row.depth; d++) {
                int gx = treeX + 4 + (d - 1) * INDENT + 4;
                Gui.drawRect(gx, y, gx + 1, y + rowH, 0x28FFFFFF);
            }
            
            int x = treeX + 4 + row.depth * INDENT;
            if (node.isActionRow()) {
                fontRenderer.drawString(node.label.get(), x + 10, y + textY, 0x77DD77);
                continue;
            }
            
            if (!node.children.isEmpty()) {
                fontRenderer.drawString(expanded.contains(node.key) ? "v" : ">", x, y + textY, 0xAAAAAA);
            }
            
            int labelX = x + 10;
            if (icons && node.icon != null) {
                ItemStack stack = node.icon.get();
                if (!stack.isEmpty()) {
                    RenderHelper.enableGUIStandardItemLighting();
                    GlStateManager.enableDepth();
                    itemRender.renderItemAndEffectIntoGUI(stack, labelX, y);
                    RenderHelper.disableStandardItemLighting();
                }
                labelX += 18;
            }
            
            String label = node.label.get();
            int maxW = treeX + treeW - 8 - labelX;
            fontRenderer.drawString(fontRenderer.trimStringToWidth(label, maxW), labelX, y + textY, node.color);
            
            String summary = node.summary.get();
            if (!summary.isEmpty() && fontRenderer.getStringWidth(label) < maxW - 12) {
                int sx = labelX + fontRenderer.getStringWidth(label) + 8;
                fontRenderer.drawString(fontRenderer.trimStringToWidth(summary, treeX + treeW - 8 - sx), sx, y + textY, 0x888888);
            }
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GlStateManager.disableDepth();
        
        int max = maxScroll();
        if (max > 0) {
            int barH = Math.max(12, treeH * treeH / (rows.size() * rowH));
            int barY = treeY + (int) ((long) scroll * (treeH - barH) / max);
            Gui.drawRect(treeX + treeW - 4, barY, treeX + treeW - 1, barY + barH, 0xFF888888);
        }
    }
    
    //GL scissor in gui coordinates (same maths as the preview box in EditScreenShared)
    private void enableScissor(int x, int y, int w, int h) {
        ScaledResolution sr = new ScaledResolution(mc);
        double scale = sr.getScaleFactor();
        
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor((int) (x * scale), (int) (mc.displayHeight - (y + h) * scale), (int) (w * scale), (int) (h * scale));
    }
    
    //panel behind the inspector (drawn before the widgets) and a separator under its title
    private void renderInspectorPanel() {
        int left = rightX - 6;
        int top = inspectorTitleY - 6;
        int right = this.width - 4;
        int bottom = this.height - 6;
        Gui.drawRect(left - 1, top - 1, right + 1, bottom + 1, 0xFF101010);
        Gui.drawRect(left, top, right, bottom, 0xA0000000);
        Gui.drawRect(rightX, inspectorTitleY + 11, rightX + RIGHT_W, inspectorTitleY + 12, 0x40FFFFFF);
    }
    
    private void renderInspectorText() {
        if (selected == null) return;
        fontRenderer.drawString(fontRenderer.trimStringToWidth(selected.label.get(), RIGHT_W), rightX, inspectorTitleY, selected.color);
        for (Label l : fieldLabels) {
            fontRenderer.drawString(l.text, l.x, l.y, 0xAAAAAA);
        }
        if (!message.isEmpty()) {
            fontRenderer.drawString(fontRenderer.trimStringToWidth(message, RIGHT_W), rightX, messageY, messageColor);
        }
    }
    
    //input
    
    private FieldBox focusedBox() {
        for (FieldBox fb : inspectorBoxes) {
            if (fb.box.isFocused()) return fb;
        }
        return null;
    }
    
    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            FieldBox focused = focusedBox();
            if (focused != null) focused.box.setFocused(false);
            else exit();
            return;
        }
        
        FieldBox focused = focusedBox();
        if (focused != null) {
            String before = focused.box.getText();
            focused.box.textboxKeyTyped(typedChar, keyCode);
            String after = focused.box.getText();
            if (!after.equals(before)) onFieldEdited(focused, after);
            return;
        }
        
        switch (keyCode) {
            case Keyboard.KEY_UP:
                move(-1);
                break;
            case Keyboard.KEY_DOWN:
                move(1);
                break;
            case Keyboard.KEY_LEFT:
                left();
                break;
            case Keyboard.KEY_RIGHT:
                right();
                break;
            case Keyboard.KEY_RETURN:
            case Keyboard.KEY_NUMPADENTER:
            case Keyboard.KEY_SPACE:
                toggle(selected);
                break;
            case Keyboard.KEY_DELETE:
                for (EditorNode.Action a : selected.actions) {
                    if (a.label().startsWith("Delete")) {
                        runAction(a);
                        break;
                    }
                }
                break;
            default:
                break;
        }
    }
    
    //moves the selection to the next/previous selectable row (add rows are skipped)
    private void move(int dir) {
        int idx = indexOf(selected);
        for (int i = idx + dir; i >= 0 && i < rows.size(); i += dir) {
            if (!rows.get(i).node.isActionRow()) {
                select(rows.get(i).node);
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
            mc.displayGuiScreen(null);
            return;
        }
        mc.displayGuiScreen(new GuiYesNo((result, id) -> {
            if (result) mc.displayGuiScreen(null);
            else mc.displayGuiScreen(this);
        }, "Exit Editor", "You have unsaved changes. Exit anyway?", 0));
    }
    
    private boolean inTree(int mx, int my) {
        return mx >= treeX && mx < treeX + treeW && my >= treeY && my < treeY + treeH;
    }
    
    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (EditScreenShared.mouseClicked(mouseX, mouseY, mouseButton)) return;
        
        //buttons (a click can rebuild the widgets, so look at the result after)
        super.mouseClicked(mouseX, mouseY, mouseButton);
        
        for (FieldBox fb : new ArrayList<FieldBox>(inspectorBoxes)) {
            fb.box.mouseClicked(mouseX, mouseY, mouseButton);
        }
        
        if (inTree(mouseX, mouseY)) clickTree(mouseX, mouseY);
    }
    
    private void clickTree(int mouseX, int mouseY) {
        int idx = (mouseY - treeY + scroll) / rowH;
        if (idx < 0 || idx >= rows.size()) return;
        
        Row row = rows.get(idx);
        EditorNode node = row.node;
        if (node.isActionRow()) {
            node.activate.run();
            return;
        }
        
        int arrowX = treeX + 4 + row.depth * INDENT;
        boolean onArrow = !node.children.isEmpty() && mouseX >= arrowX && mouseX < arrowX + 10;
        long now = Minecraft.getSystemTime();
        boolean doubleClick = idx == lastClickRow && now - lastClickTime < 300L;
        lastClickRow = idx;
        lastClickTime = now;
        
        if (node != selected) select(node);
        if (onArrow || doubleClick) toggle(node);
    }
    
    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (EditScreenShared.mouseDragged(mouseX, mouseY)) return;
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }
    
    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (EditScreenShared.mouseReleased()) return;
        super.mouseReleased(mouseX, mouseY, state);
    }
    
    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        
        if (EditScreenShared.mouseScrolled(Mouse.getEventX(), Mouse.getEventY(), wheel)) return;
        
        int mx = Mouse.getEventX() * this.width / mc.displayWidth;
        int my = this.height - Mouse.getEventY() * this.height / mc.displayHeight - 1;
        if (inTree(mx, my)) {
            scroll -= Integer.signum(wheel) * rowH * 3;
            clampScroll();
        }
    }
    
    @Override
    protected void actionPerformed(GuiButton button) {
        if (button instanceof TreeButton) ((TreeButton) button).action.run();
    }
    
    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }
    
    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}