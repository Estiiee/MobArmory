package com.estie.mobarmory.client.gui.screen;

import com.estie.mobarmory.data.MobEquipmentReloadListener;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class EditScreenShared {
    // -- Preview entity & equipment cycling --
    private static final long CYCLE_INTERVAL_MS = 2000;
    private static final Random previewRandom = new Random();
    
    private static LivingEntity previewEntity;
    private static List<MobEquipmentReloadListener.EquipmentSet> previewSets = List.of();
    private static int previewSetIndex = -1;
    private static long lastCycleTime = 0;
    
    // -- Preview camera (drag-to-rotate, scroll-to-zoom) --
    private static final float MIN_PITCH = -80f;
    private static final float MAX_PITCH = 80f;
    private static final float MIN_ZOOM = 0.3f;
    private static final float MAX_ZOOM = 3.0f;
    
    private static float previewYaw = 0f;
    private static float previewPitch = 10f;
    private static float previewZoom = 1.0f;
    private static boolean dragging = false;
    private static double lastDragMouseX;
    private static double lastDragMouseY;
    
    // -- Preview box bounds - written each renderHeader call, read by input handlers --
    private static int previewX, previewY, previewSize;
    
    // -- Breadcrumb trail - written each renderHeader call, read by click handler --
    private static List<Crumb> currentTrail = List.of();
    private static final List<int[]> crumbBounds = new ArrayList<>();
    
    public static void renderHeader(GuiGraphics gfx, Font font, MobEquipmentReloadListener.MobEquipmentEntry entry,
                                    int screenWidth, int previewSizeArg, List<Crumb> trail) {
        tickPreviewCycle(entry);
        
        String fileLabel = entry.fileName != null ? entry.fileName : "(unnamed file)";
        gfx.drawCenteredString(font, fileLabel, screenWidth / 2, 15, 0xFFFFFF);
        
        renderBreadcrumbs(gfx, font, trail, screenWidth);
        
        previewX = screenWidth - previewSizeArg - 20;
        previewY = 60;
        previewSize = previewSizeArg;
        
        String mobLabel = entry.mob != null ? entry.mob.toString() : "(no mob chosen)";
        gfx.drawCenteredString(font, mobLabel, previewX + previewSize / 2, previewY - 12, 0xAAAAAA);
        
        gfx.fill(previewX, previewY, previewX + previewSize, previewY + previewSize, 0xFF333333);
        
        if (previewEntity != null) {
            int centerX = previewX + previewSize / 2;
            int centerY = previewY + previewSize - previewSize / 6;
            
            gfx.enableScissor(previewX, previewY, previewX + previewSize, previewY + previewSize);
            renderPreviewEntity(gfx, centerX, centerY, previewSize, previewEntity);
            gfx.disableScissor();
        } else {
            gfx.drawCenteredString(font, entry.mob == null ? "(no mob chosen)" : "(preview unavailable)",
                    previewX + previewSize / 2, previewY + previewSize / 2 - 4, 0xFFFFFF);
        }
    }
    
    public static boolean hasOverride(Float value) {
        return value != null && value != 0.0F;
    }
    
    public static void rebuildPreviewEntity(MobEquipmentReloadListener.MobEquipmentEntry entry, ClientLevel level) {
        if (entry.mob == null || level == null) {
            previewEntity = null;
            return;
        }
        
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(entry.mob);
        if (type == null) {
            previewEntity = null;
            return;
        }
        
        Entity created = type.create(level);
        previewEntity = created instanceof LivingEntity living ? living : null;

        if (previewEntity != null && previewSetIndex >= 0 && previewSetIndex < previewSets.size()) {
            applySetToPreview(previewSets.get(previewSetIndex));
        }
    }
    
    public static boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= previewX && mouseX < previewX + previewSize
                && mouseY >= previewY && mouseY < previewY + previewSize) {
            dragging = true;
            lastDragMouseX = mouseX;
            lastDragMouseY = mouseY;
            return true;
        }
        return false;
    }
    
    public static boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging) {
            previewYaw -= (float) (mouseX - lastDragMouseX) * 2f; // flipped: was +=
            previewPitch = Mth.clamp(previewPitch - (float) (mouseY - lastDragMouseY) * 2f, MIN_PITCH, MAX_PITCH);
            lastDragMouseX = mouseX;
            lastDragMouseY = mouseY;
            return true;
        }
        return false;
    }
    
    public static boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean was = dragging;
        dragging = false;
        return was;
    }
    
    public static boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= previewX && mouseX < previewX + previewSize
                && mouseY >= previewY && mouseY < previewY + previewSize) {
            previewZoom = Mth.clamp(previewZoom + (float) delta * 0.1f, MIN_ZOOM, MAX_ZOOM);
            return true;
        }
        return false;
    }
    
    public static boolean itemExists(String rawId) {
        ResourceLocation rl = ResourceLocation.tryParse(rawId);
        return rl != null && ForgeRegistries.ITEMS.containsKey(rl);
    }
    
    public static boolean mobExists(String rawId) {
        ResourceLocation rl = ResourceLocation.tryParse(rawId);
        return rl != null && ForgeRegistries.ENTITY_TYPES.containsKey(rl);
    }
    
    public static boolean enchantExists(String rawId) {
        ResourceLocation rl = ResourceLocation.tryParse(rawId);
        return rl != null && ForgeRegistries.ENCHANTMENTS.containsKey(rl);
    }
    
    public static boolean timeValid(String raw) {
        try {
            MobEquipmentReloadListener.timeStringToTicks(raw);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    public static boolean nbtValid(String raw) {
        if (raw == null || raw.isBlank()) return true; //empty = no nbt, always valid
        try {
            String wrapped = raw.trim().startsWith("{") ? raw.trim() : "{" + raw.trim() + "}";
            TagParser.parseTag(wrapped);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    public static boolean timeRangeValid(String raw) {
        try { MobEquipmentReloadListener.parseTimeRange(raw); return true; }
        catch (Exception e) { return false; }
    }
    
    public static boolean yLevelValid(String raw) {
        try { MobEquipmentReloadListener.parseYLevel(raw); return true; }
        catch (Exception e) { return false; }
    }
    
    public static boolean effectExists(String rawId) {
        ResourceLocation rl = ResourceLocation.tryParse(rawId);
        return rl != null && ForgeRegistries.MOB_EFFECTS.containsKey(rl);
    }
    
    public static boolean biomeMatchValid(String raw) {
        if (raw.equals("global")) return true;
        
        String idPart = raw.startsWith("#") ? raw.substring(1) : raw;
        ResourceLocation rl = ResourceLocation.tryParse(idPart);
        if (rl == null) return false;

        if (raw.startsWith("#")) return true;
        
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return true;
        
        return level.registryAccess().registryOrThrow(Registries.BIOME).containsKey(rl);
    }
    
    private static void applySetToPreview(MobEquipmentReloadListener.EquipmentSet set) {
        if (previewEntity == null) return;
        for (EquipmentSlot slot : EquipmentSlot.values()) previewEntity.setItemSlot(slot, ItemStack.EMPTY);
        if (set == null) return;
        
        for (var slotEntry : set.slots.entrySet()) {
            List<MobEquipmentReloadListener.WeightedItem> items = slotEntry.getValue();
            if (items.isEmpty()) continue;
            
            MobEquipmentReloadListener.WeightedItem chosen = pickWeightedItem(items);
            ResourceLocation rl = ResourceLocation.tryParse(chosen.itemId);
            Item item = rl != null ? ForgeRegistries.ITEMS.getValue(rl) : null;
            if (item == null) item = Items.BARRIER; //stands out as "this id didn't resolve"
            previewEntity.setItemSlot(slotEntry.getKey(), new ItemStack(item));
        }
    }
    
    private static MobEquipmentReloadListener.WeightedItem pickWeightedItem(List<MobEquipmentReloadListener.WeightedItem> items) {
        int totalWeight = items.stream().mapToInt(i -> i.weight).sum();
        if (totalWeight <= 0) return items.get(0);
        
        int roll = previewRandom.nextInt(totalWeight);
        int cumulative = 0;
        for (var item : items) {
            cumulative += item.weight;
            if (roll < cumulative) return item;
        }
        return items.get(items.size() - 1);
    }
    
    private static void tickPreviewCycle(MobEquipmentReloadListener.MobEquipmentEntry entry) {
        List<MobEquipmentReloadListener.EquipmentSet> sets = collectAllSets(entry);
    
        if (!sets.equals(previewSets)) {
            previewSets = sets;
            previewSetIndex = -1;
        }
        
        if (previewSets.isEmpty()) {
            applySetToPreview(null);
            return;
        }
        
        long now = Util.getMillis();
        if (previewSetIndex == -1 || now - lastCycleTime >= CYCLE_INTERVAL_MS) {
            previewSetIndex = (previewSetIndex + 1) % previewSets.size();
            lastCycleTime = now;
            applySetToPreview(previewSets.get(previewSetIndex));
        }
    }
    
    private static List<MobEquipmentReloadListener.EquipmentSet> collectAllSets(MobEquipmentReloadListener.MobEquipmentEntry entry) {
        List<MobEquipmentReloadListener.EquipmentSet> all = new ArrayList<>();
        for (var dg : entry.difficultyGroups) {
            for (var bg : dg.biomeGroups) all.addAll(bg.sets);
            all.addAll(dg.globalSets);
        }
        return all;
    }
    
    public static void renderPreviewEntity(GuiGraphics gfx, int centerX, int centerY, int boxSize, LivingEntity entity) {
        Quaternionf pose = new Quaternionf().rotateZ(3.1415927F);
        Quaternionf cameraOrientation = new Quaternionf().rotateX(previewPitch * ((float) Math.PI / 180F));
        pose.mul(cameraOrientation);
        
        float prevBodyRot = entity.yBodyRot;
        float prevYRot = entity.getYRot();
        float prevXRot = entity.getXRot();
        float prevHeadRotO = entity.yHeadRotO;
        float prevHeadRot = entity.yHeadRot;
        
        entity.yBodyRot = 180.0F + previewYaw;
        entity.setYRot(180.0F + previewYaw);
        entity.setXRot(0f);
        entity.yHeadRot = entity.getYRot();
        entity.yHeadRotO = entity.getYRot();
        
        int scale = Mth.clamp(Math.round(computeFitScale(entity, boxSize) * previewZoom), MIN_SCALE, MAX_SCALE);
        InventoryScreen.renderEntityInInventory(gfx, centerX, centerY, scale, pose, cameraOrientation, entity);
        
        entity.yBodyRot = prevBodyRot;
        entity.setYRot(prevYRot);
        entity.setXRot(prevXRot);
        entity.yHeadRotO = prevHeadRotO;
        entity.yHeadRot = prevHeadRot;
    }
    
    private static final float FILL_FACTOR = 0.6f;
    private static final int MIN_SCALE = 8;
    private static final int MAX_SCALE = 150;
    
    private static int computeFitScale(LivingEntity entity, int boxSize) {
        AABB box = entity.getBoundingBox();
        float width = (float) (box.maxX - box.minX);
        float height = (float) (box.maxY - box.minY);
        float maxDim = Math.max(width, height);
        if (maxDim <= 0.01f) maxDim = 1.0f;
        
        int scale = Math.round((boxSize / maxDim) * FILL_FACTOR);
        return Mth.clamp(scale, MIN_SCALE, MAX_SCALE);
    }
    
    private static void renderBreadcrumbs(GuiGraphics gfx, Font font, List<Crumb> trail, int screenWidth) {
        currentTrail = trail;
        crumbBounds.clear();
        
        String sep = " > ";
        int totalWidth = 0;
        for (int i = 0; i < trail.size(); i++) {
            totalWidth += font.width(trail.get(i).label());
            if (i < trail.size() - 1) totalWidth += font.width(sep);
        }
        
        int x = screenWidth / 2 - totalWidth / 2;
        int y = 30;
        
        for (int i = 0; i < trail.size(); i++) {
            Crumb crumb = trail.get(i);
            boolean clickable = crumb.onClick() != null;
            int color = clickable ? 0x55FF55 : 0xFFFFFF;
            
            int labelWidth = font.width(crumb.label());
            gfx.drawString(font, crumb.label(), x, y, color);
            crumbBounds.add(new int[]{x, y, x + labelWidth, y + font.lineHeight});
            x += labelWidth;
            
            if (i < trail.size() - 1) {
                gfx.drawString(font, sep, x, y, 0xAAAAAA);
                x += font.width(sep);
            }
        }
    }
    
    public static boolean breadcrumbClicked(double mouseX, double mouseY) {
        for (int i = 0; i < crumbBounds.size(); i++) {
            int[] b = crumbBounds.get(i);
            if (mouseX >= b[0] && mouseX < b[2] && mouseY >= b[1] && mouseY < b[3]) {
                Runnable action = currentTrail.get(i).onClick();
                if (action != null) { action.run(); return true; }
            }
        }
        return false;
    }
    
    public record Crumb(String label, Runnable onClick) {}
    
    public static Crumb crumbMain(MobEquipmentReloadListener.MobEquipmentEntry entry) {
        return new Crumb("Main", () -> Minecraft.getInstance().setScreen(new EditScreenMain(entry)));
    }
    
    public static Crumb crumbDifficultyGroup(EditScreenMain main, MobEquipmentReloadListener.DifficultyGroup dg) {
        return new Crumb("Difficulty Group", () -> Minecraft.getInstance().setScreen(new EditScreenDifficultyGroupEntry(main, dg)));
    }
    
    public static Crumb crumbBiomeGroup(EditScreenMain main, MobEquipmentReloadListener.DifficultyGroup dg, MobEquipmentReloadListener.BiomeGroup bg) {
        return new Crumb("Biome Group", () -> Minecraft.getInstance().setScreen(new EditScreenBiomeGroupEntry(main, dg, bg)));
    }
    
    public static Crumb crumbSet(EditScreenMain main, MobEquipmentReloadListener.DifficultyGroup dg,
                                 MobEquipmentReloadListener.BiomeGroup bg, MobEquipmentReloadListener.EquipmentSet set) {
        String label = set.name != null ? set.name : "Equipment Set";
        return new Crumb(label, () -> Minecraft.getInstance().setScreen(new EditScreenEquipmentSetEntry(main, dg, bg, set)));
    }
    
    public static Crumb crumbSlotsList(EditScreenMain main, MobEquipmentReloadListener.DifficultyGroup dg,
                                       MobEquipmentReloadListener.BiomeGroup bg, MobEquipmentReloadListener.EquipmentSet set) {
        return new Crumb("Slots", () -> Minecraft.getInstance().setScreen(new EditScreenSlots(main, dg, bg, set)));
    }
    
    public static Crumb crumbSlot(EditScreenMain main, MobEquipmentReloadListener.DifficultyGroup dg,
                                  MobEquipmentReloadListener.BiomeGroup bg, MobEquipmentReloadListener.EquipmentSet set,
                                  EquipmentSlot slot) {
        return new Crumb(EditScreenSlots.slotLabel(slot),
                () -> Minecraft.getInstance().setScreen(new EditScreenSlotItems(main, dg, bg, set, slot)));
    }
    
    public static Crumb crumbItem(EditScreenMain main, MobEquipmentReloadListener.DifficultyGroup dg,
                                  MobEquipmentReloadListener.BiomeGroup bg, MobEquipmentReloadListener.EquipmentSet set,
                                  EquipmentSlot slot, MobEquipmentReloadListener.WeightedItem item) {
        return new Crumb("Item", () -> Minecraft.getInstance().setScreen(new EditScreenWeightedItemEntry(main, dg, bg, set, slot, item)));
    }
    
    public static Crumb current(String label) {
        return new Crumb(label, null);
    }
}