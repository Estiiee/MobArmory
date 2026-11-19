package com.estie.mobarmory.client.gui.screen;

import com.estie.mobarmory.util.MobEquipmentSpawnUtil;
import com.estie.mobarmory.data.MobEquipmentReloadListener;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import org.lwjgl.opengl.GL11;

import java.util.*;

public final class EditScreenShared {
    // -- Preview entity & equipment cycling --
    private static final long CYCLE_INTERVAL_MS = 2000;
    private static final Random previewRandom = new Random();
    
    private static EntityLivingBase previewEntity;
    private static ResourceLocation previewMobId; //mob previewEntity was built for
    private static MobEquipmentReloadListener.EquipmentSet shownSet;
    private static String shownLabel = "";
    
    //what the preview cycles through. null/empty = defaults (every set of the entry, the entry's own mob).
    //the tree editor narrows this down to whatever is selected
    private static List<MobEquipmentReloadListener.EquipmentSet> poolSets = null;
    private static List<ResourceLocation> poolMobs = Collections.emptyList();
    private static List<MobEquipmentReloadListener.EquipmentSet> lastSets = Collections.emptyList();
    private static List<ResourceLocation> lastMobs = Collections.emptyList();
    private static int step = -1;
    private static int total = 1;
    private static boolean paused = false;
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
    private static List<Crumb> currentTrail = Collections.emptyList();
    private static final List<int[]> crumbBounds = new ArrayList<>();
    
    public static void renderHeader(Minecraft mc, MobEquipmentReloadListener.MobEquipmentEntry entry,
                                    int screenWidth, int previewSizeArg, List<Crumb> trail) {
        tickPreviewCycle(entry);
        
        FontRenderer font = mc.fontRenderer;
        
        String fileLabel = entry.fileName != null ? entry.fileName : "(unnamed file)";
        drawCentered(font, fileLabel, screenWidth / 2, 15, 0xFFFFFF);
        
        renderBreadcrumbs(font, trail, screenWidth);
        
        previewX = screenWidth - previewSizeArg - 20;
        previewY = 60;
        previewSize = previewSizeArg;
        
        String mobLabel = entry.mob != null ? entry.mob.toString() : "(no mob chosen)";
        drawCentered(font, mobLabel, previewX + previewSize / 2, previewY - 12, 0xAAAAAA);
        
        Gui.drawRect(previewX, previewY, previewX + previewSize, previewY + previewSize, 0xFF333333);
        
        if (previewEntity != null) {
            int centerX = previewX + previewSize / 2;
            int centerY = previewY + previewSize - previewSize / 6;
            
            enableScissor(mc, previewX, previewY, previewSize, previewSize);
            renderPreviewEntity(centerX, centerY, previewSize, previewEntity);
            disableScissor();
        } else {
            drawCentered(font, entry.mob == null ? "(no mob chosen)" : "(preview unavailable)",
                    previewX + previewSize / 2, previewY + previewSize / 2 - 4, 0xFFFFFF);
        }
        
        renderPreviewOverlay(font);
    }
    
    private static void drawCentered(FontRenderer font, String text, int centerX, int y, int color) {
        font.drawString(text, centerX - font.getStringWidth(text) / 2, y, color);
    }
    
    private static void enableScissor(Minecraft mc, int x, int y, int width, int height) {
        ScaledResolution sr = new ScaledResolution(mc);
        double scale = sr.getScaleFactor();
        
        int glX = (int) (x * scale);
        int glY = (int) (mc.displayHeight - (y + height) * scale);
        int glWidth = (int) (width * scale);
        int glHeight = (int) (height * scale);
        
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(glX, glY, glWidth, glHeight);
    }
    
    private static void disableScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }
    
    public static boolean hasOverride(Float value) {
        return value != null && value >= 0.0F;
    }
    
    public static void rebuildPreviewEntity(
            MobEquipmentReloadListener.MobEquipmentEntry entry,
            World world) {
        
        showMob(entry.mob, world);
        step = -1; //restart the cycle so the current set is applied to the new entity
    }
    
    private static void showMob(ResourceLocation id, World world) {
        previewMobId = id;
        previewEntity = null;
        if (id == null || world == null) return;
        
        if (id.equals(new ResourceLocation("minecraft", "player"))) {
            if (Minecraft.getMinecraft().player != null) {
                GameProfile profile = Minecraft.getMinecraft().player.getGameProfile();
                previewEntity = new EntityOtherPlayerMP(world, profile);
            }
            return;
        }
        
        Class<? extends Entity> entityClass = EntityList.getClass(id);
        if (entityClass == null || !EntityLivingBase.class.isAssignableFrom(entityClass)) return;
        
        Entity created = EntityList.newEntity(entityClass, world);
        previewEntity = created instanceof EntityLivingBase ? (EntityLivingBase) created : null;
    }
    
    public static boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button == 0 && overlayVisible()) {
            int barY = previewY + previewSize - BAR_H;
            if (mouseX >= previewX && mouseX < previewX + previewSize && mouseY >= barY && mouseY < previewY + previewSize) {
                if (mouseX < previewX + BAR_BUTTON_W) stepPreview(-1);
                else if (mouseX >= previewX + previewSize - BAR_BUTTON_W) stepPreview(1);
                else paused = !paused;
                return true;
            }
        }
        
        if (button == 0 && mouseX >= previewX && mouseX < previewX + previewSize
                && mouseY >= previewY && mouseY < previewY + previewSize) {
            dragging = true;
            lastDragMouseX = mouseX;
            lastDragMouseY = mouseY;
            return true;
        }
        return false;
    }
    
    //1.12.2 GuiScreen apparently has no mouseDragged hook by default. this is called every drawScreen tick
    //while the mouse button is held, using Mouse.isButtonDown(0) and Mouse.getX/Y()
    //converted to GUI-space coordinates.
    public static boolean mouseDragged(double mouseX, double mouseY) {
        if (dragging) {
            previewYaw -= (float) (mouseX - lastDragMouseX) * 2f;
            previewPitch = MathHelper.clamp(previewPitch + (float) (mouseY - lastDragMouseY) * 2f, MIN_PITCH, MAX_PITCH);
            lastDragMouseX = mouseX;
            lastDragMouseY = mouseY;
            return true;
        }
        return false;
    }
    
    public static boolean mouseReleased() {
        boolean was = dragging;
        dragging = false;
        return was;
    }
    
    public static boolean mouseScrolled(int rawMouseX, int rawMouseY, double delta) {
        Minecraft mc = Minecraft.getMinecraft();
        if (delta == 0) {
            return false;
        }
        ScaledResolution sr = new ScaledResolution(mc);
        
        int mouseX = rawMouseX * sr.getScaledWidth() / mc.displayWidth;
        
        int mouseY = sr.getScaledHeight()
                - rawMouseY * sr.getScaledHeight() / mc.displayHeight
                - 1;
        
        if (mouseX >= previewX && mouseX < previewX + previewSize
                && mouseY >= previewY && mouseY < previewY + previewSize) {
            
            previewZoom = MathHelper.clamp(
                    previewZoom + (float)Math.signum(delta) * 0.1F,
                    MIN_ZOOM,
                    MAX_ZOOM
            );
            
            return true;
        }
        
        return false;
    }
    
    public static boolean itemExists(String rawId) {
        ResourceLocation rl = safeParse(rawId);
        return rl != null && ForgeRegistries.ITEMS.containsKey(rl);
    }
    
    public static boolean mobExists(String rawId) {
        ResourceLocation rl = safeParse(rawId);
        if (rl == null) return false;
        
        if (rl.equals(new ResourceLocation("minecraft", "player"))) {
            return true;
        }
        
        return EntityList.getClass(rl) != null;
    }
    
    public static boolean enchantExists(String rawId) {
        ResourceLocation rl = safeParse(rawId);
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
        if (raw == null || raw.trim().isEmpty()) return true; //empty = no nbt, always valid
        try {
            String wrapped = raw.trim().startsWith("{") ? raw.trim() : "{" + raw.trim() + "}";
            JsonToNBT.getTagFromJson(wrapped);
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
        ResourceLocation rl = safeParse(rawId);
        return rl != null && ForgeRegistries.POTIONS.containsKey(rl);
    }
    
    // no biome tags/dynamic registries in 1.12.2. "#name" is checked against BiomeDictionary.Type,
    // which auto-creates unknown types rather than rejecting them, same permissive behavior as before
    public static boolean biomeMatchValid(String raw) {
        if (raw.equalsIgnoreCase("global")) return true;
        if (raw.indexOf(':') < 0) return true; //treated as a BiomeDictionary tag, always accepted
        ResourceLocation rl = safeParse(raw);
        return rl != null && ForgeRegistries.BIOMES.containsKey(rl);
    }
    
    private static ResourceLocation safeParse(String raw) {
        try {
            return new ResourceLocation(raw);
        } catch (Exception e) {
            return null;
        }
    }
    
    private static void applySetToPreview(MobEquipmentReloadListener.EquipmentSet set) {
        if (previewEntity == null) return;
        
        for (EntityEquipmentSlot slot : EntityEquipmentSlot.values()) {
            previewEntity.setItemStackToSlot(slot, ItemStack.EMPTY);
        }
        if (set == null) return;
        
        for (Map.Entry<EntityEquipmentSlot, List<MobEquipmentReloadListener.WeightedItem>> slotEntry : set.slots.entrySet()) {
            
            List<MobEquipmentReloadListener.WeightedItem> items = slotEntry.getValue();
            if (items.isEmpty()) continue;
            
            MobEquipmentReloadListener.WeightedItem chosen = pickWeightedItem(items);
            
            ResourceLocation rl = safeParse(chosen.itemId);
            Item item = rl != null ? ForgeRegistries.ITEMS.getValue(rl) : null;
            
            if (item == null) item = Item.getItemFromBlock(Blocks.BEDROCK);
            
            previewEntity.setItemStackToSlot(slotEntry.getKey(), new ItemStack(item));
        }
    }
    
    private static MobEquipmentReloadListener.WeightedItem pickWeightedItem(List<MobEquipmentReloadListener.WeightedItem> items) {
        int totalWeight = 0;
        for (MobEquipmentReloadListener.WeightedItem item : items) totalWeight += item.weight;
        if (totalWeight <= 0) return items.get(0);
        
        int roll = previewRandom.nextInt(totalWeight);
        int cumulative = 0;
        for (MobEquipmentReloadListener.WeightedItem item : items) {
            cumulative += item.weight;
            if (roll < cumulative) return item;
        }
        return items.get(items.size() - 1);
    }
    
    //what the preview cycles through: the sets and replacement mobs under the tree editor's selection
    public static void setPreviewTarget(List<MobEquipmentReloadListener.EquipmentSet> sets, List<ResourceLocation> mobs) {
        poolSets = sets;
        poolMobs = mobs != null ? mobs : Collections.<ResourceLocation>emptyList();
    }
    
    //re-applies the current step (e.g. after an item was added to the shown set)
    public static void refreshPreview() {
        if (step >= 0) showStep(step);
    }
    
    //manual stepping from the overlay buttons
    public static void stepPreview(int direction) {
        if (total <= 1) return;
        int next = (Math.max(step, 0) + direction) % total;
        if (next < 0) next += total;
        showStep(next);
        lastCycleTime = System.currentTimeMillis();
    }
    
    private static void tickPreviewCycle(MobEquipmentReloadListener.MobEquipmentEntry entry) {
        List<MobEquipmentReloadListener.EquipmentSet> sets = poolSets != null ? poolSets : MobEquipmentSpawnUtil.collectAllSets(entry);
        List<ResourceLocation> mobs = !poolMobs.isEmpty() ? poolMobs
                : (entry.mob != null ? Collections.singletonList(entry.mob) : Collections.<ResourceLocation>emptyList());
        
        if (!sets.equals(lastSets) || !mobs.equals(lastMobs)) {
            lastSets = sets;
            lastMobs = mobs;
            step = -1; //new selection: start over
        }
        
        total = Math.max(1, sets.size()) * Math.max(1, mobs.size());
        
        long now = System.currentTimeMillis();
        if (step == -1 || (!paused && now - lastCycleTime >= CYCLE_INTERVAL_MS)) {
            showStep(step == -1 ? 0 : (step + 1) % total);
            lastCycleTime = now;
        }
    }
    
    //one step = one (set, mob) pair: every set for the first mob, then every set for the next, ...
    private static void showStep(int s) {
        step = s;
        int setCount = Math.max(1, lastSets.size());
        int setIndex = s % setCount;
        int mobIndex = (s / setCount) % Math.max(1, lastMobs.size());
        
        ResourceLocation mob = lastMobs.isEmpty() ? null : lastMobs.get(mobIndex);
        if (!Objects.equals(mob, previewMobId)) showMob(mob, Minecraft.getMinecraft().world);
        
        shownSet = lastSets.isEmpty() ? null : lastSets.get(setIndex);
        applySetToPreview(shownSet);
        
        String setName = shownSet == null ? "(no set)"
                : (shownSet.name != null && !shownSet.name.trim().isEmpty() ? shownSet.name : "Set " + (setIndex + 1));
        shownLabel = lastMobs.size() > 1 && mob != null ? setName + " - " + mob.getPath() : setName;
    }
    
    //overlay: shown label at the top of the preview, < n/N > bar at the bottom. hidden when the preview is tiny
    private static final int BAR_H = 12;
    private static final int BAR_BUTTON_W = 14;
    
    private static boolean overlayVisible() {
        return previewSize >= 60 && total > 1;
    }
    
    private static void renderPreviewOverlay(FontRenderer font) {
        if (previewSize < 60) return;
        
        if (!shownLabel.isEmpty()) {
            font.drawStringWithShadow(font.trimStringToWidth(shownLabel, previewSize - 6), previewX + 3, previewY + 3, 0xCCCCCC);
        }
        if (!overlayVisible()) return;
        
        int barY = previewY + previewSize - BAR_H;
        Gui.drawRect(previewX, barY, previewX + previewSize, previewY + previewSize, 0x99000000);
        font.drawStringWithShadow("<", previewX + 5, barY + 2, 0xFFFFFF);
        font.drawStringWithShadow(">", previewX + previewSize - 10, barY + 2, 0xFFFFFF);
        drawCentered(font, (paused ? "|| " : "") + (step + 1) + "/" + total,
                previewX + previewSize / 2, barY + 2, paused ? 0xFFAA00 : 0xAAAAAA);
    }
    
    private static final float FILL_FACTOR = 0.6f;
    private static final int MIN_SCALE = 8;
    private static final int MAX_SCALE = 150;
    
    public static void renderPreviewEntity(int centerX, int centerY, int boxSize, EntityLivingBase entity) {
        int scale = MathHelper.clamp(
                Math.round(computeFitScale(entity, boxSize) * previewZoom),
                MIN_SCALE,
                MAX_SCALE
        );
        
        float prevRenderYawOffset = entity.renderYawOffset;
        float prevRotationYaw = entity.rotationYaw;
        float prevRotationPitch = entity.rotationPitch;
        float prevRotationYawHead = entity.rotationYawHead;
        float prevPrevRotationYawHead = entity.prevRotationYawHead;
        
        RenderManager renderManager = Minecraft.getMinecraft().getRenderManager();
        
        boolean prevShadow = renderManager.isRenderShadow();
        float prevPlayerViewY = renderManager.playerViewY;
        
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        
        GlStateManager.enableColorMaterial();
        GlStateManager.pushMatrix();
        
        GlStateManager.translate((float) centerX, (float) centerY, 50.0F);
        GlStateManager.scale((float) (-scale), (float) scale, (float) scale);
        GlStateManager.rotate(180.0F, 0.0F, 0.0F, 1.0F);
        
        GlStateManager.rotate(135.0F, 0.0F, 1.0F, 0.0F);
        RenderHelper.enableStandardItemLighting();
        GlStateManager.rotate(-135.0F, 0.0F, 1.0F, 0.0F);
        
        GlStateManager.rotate(previewPitch, 1.0F, 0.0F, 0.0F);
        
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
        
        entity.renderYawOffset = previewYaw;
        entity.rotationYaw = previewYaw;
        entity.rotationPitch = 0.0F;
        entity.rotationYawHead = entity.rotationYaw;
        entity.prevRotationYawHead = entity.rotationYaw;
        
        renderManager.setRenderShadow(false);
        renderManager.setPlayerViewY(180.0F);
        
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        
        GlStateManager.enableCull();
        GlStateManager.cullFace(GlStateManager.CullFace.BACK);
        
        GlStateManager.enableDepth();
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GlStateManager.depthMask(true);
        
        GlStateManager.disableBlend();
        GlStateManager.disableLighting();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        
        renderManager.renderEntity(
                entity,
                0.0D,
                0.0D,
                0.0D,
                0.0F,
                1.0F,
                false
        );
        
        renderManager.setRenderShadow(prevShadow);
        renderManager.setPlayerViewY(prevPlayerViewY);
        
        entity.renderYawOffset = prevRenderYawOffset;
        entity.rotationYaw = prevRotationYaw;
        entity.rotationPitch = prevRotationPitch;
        entity.rotationYawHead = prevRotationYawHead;
        entity.prevRotationYawHead = prevPrevRotationYawHead;
        
        GlStateManager.popMatrix();
        GL11.glPopAttrib();
        
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableRescaleNormal();
        
        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.enableTexture2D();
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }
    
    private static int computeFitScale(EntityLivingBase entity, int boxSize) {
        AxisAlignedBB box = entity.getEntityBoundingBox();
        float width = (float) (box.maxX - box.minX);
        float height = (float) (box.maxY - box.minY);
        float maxDim = Math.max(width, height);
        if (maxDim <= 0.01f) maxDim = 1.0f;
        
        int scale = Math.round((boxSize / maxDim) * FILL_FACTOR);
        return MathHelper.clamp(scale, MIN_SCALE, MAX_SCALE);
    }
    
    private static void renderBreadcrumbs(FontRenderer font, List<Crumb> trail, int screenWidth) {
        currentTrail = trail;
        crumbBounds.clear();
        
        String sep = " > ";
        int totalWidth = 0;
        for (int i = 0; i < trail.size(); i++) {
            totalWidth += font.getStringWidth(trail.get(i).label());
            if (i < trail.size() - 1) totalWidth += font.getStringWidth(sep);
        }
        
        int x = screenWidth / 2 - totalWidth / 2;
        int y = 30;
        
        for (int i = 0; i < trail.size(); i++) {
            Crumb crumb = trail.get(i);
            boolean clickable = crumb.onClick() != null;
            int color = clickable ? 0x55FF55 : 0xFFFFFF;
            
            int labelWidth = font.getStringWidth(crumb.label());
            font.drawString(crumb.label(), x, y, color);
            crumbBounds.add(new int[]{x, y, x + labelWidth, y + font.FONT_HEIGHT});
            x += labelWidth;
            
            if (i < trail.size() - 1) {
                font.drawString(sep, x, y, 0xAAAAAA);
                x += font.getStringWidth(sep);
            }
        }
    }
    
    public static boolean breadcrumbClicked(int mouseX, int mouseY) {
        for (int i = 0; i < crumbBounds.size(); i++) {
            int[] b = crumbBounds.get(i);
            if (mouseX >= b[0] && mouseX < b[2] && mouseY >= b[1] && mouseY < b[3]) {
                Runnable action = currentTrail.get(i).onClick();
                if (action != null) { action.run(); return true; }
            }
        }
        return false;
    }
    
    public static final class Crumb {
        private final String label;
        private final Runnable onClick;
        
        public Crumb(String label, Runnable onClick) {
            this.label = label;
            this.onClick = onClick;
        }
        
        public String label() { return label; }
        public Runnable onClick() { return onClick; }
    }
    
    public static Crumb current(String label) {
        return new Crumb(label, null);
    }
}