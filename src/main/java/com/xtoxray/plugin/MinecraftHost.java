package com.xtoxray.plugin;

import com.xtoxray.ConfigManager;
import com.xtoxray.XrayState;
import com.xtoxray.client.XrayToggleHandler;
import com.xtoxray.network.HandshakeState;
import java.lang.reflect.Method;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.LoggerFactory;

/** The game side of the Lua API. World access only works in singleplayer or on servers with the X To Xray server side. */
public final class MinecraftHost implements PluginHost {
    private static final int MAX_BOXES_PER_FRAME = 4096;
    private static final int MAX_MINE_DISTANCE = 64;
    private static final int MAX_HUD_CALLS_PER_FRAME = 4096;
    private int boxesThisFrame;
    private int hudCalls;
    private GuiGraphicsExtractor hudGraphics;

    /** Runs the plugins' HUD handlers; drawing only works during this call. */
    public void drawHud(GuiGraphicsExtractor graphics) {
        hudGraphics = graphics;
        hudCalls = 0;
        try {
            PluginManager.getInstance().fire("hud");
        } finally {
            hudGraphics = null;
        }
    }

    private boolean hudReady() {
        return hudGraphics != null && ++hudCalls <= MAX_HUD_CALLS_PER_FRAME;
    }

    @Override
    public int[] hudSize() {
        return new int[]{mc().getWindow().getGuiScaledWidth(), mc().getWindow().getGuiScaledHeight()};
    }

    @Override
    public int textWidth(String text) {
        return mc().font.width(text);
    }

    @Override
    public void hudText(String text, int x, int y, int color, boolean shadow) {
        if (hudReady()) hudGraphics.text(mc().font, text, x, y, color, shadow);
    }

    @Override
    public void hudRect(int x1, int y1, int x2, int y2, int color) {
        if (hudReady()) hudGraphics.fill(x1, y1, x2, y2, color);
    }

    @Override
    public boolean hudItem(String itemId, int x, int y) {
        if (!hudReady()) return false;
        Identifier id = Identifier.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return false;
        hudGraphics.item(new ItemStack(BuiltInRegistries.ITEM.getValue(id)), x, y);
        return true;
    }

    @Override
    public void drawLine(double x1, double y1, double z1, double x2, double y2, double z2, int color, double width) {
        if (!worldAllowed() || ++boxesThisFrame > MAX_BOXES_PER_FRAME) return;
        Gizmos.line(new Vec3(x1, y1, z1), new Vec3(x2, y2, z2), color, (float) width);
    }

    @Override
    public void drawText(String text, double x, double y, double z, int color, double scale) {
        if (!worldAllowed() || ++boxesThisFrame > MAX_BOXES_PER_FRAME) return;
        Gizmos.billboardTextOverBlock(text, BlockPos.containing(x, y, z), 0, color, (float) scale);
    }

    @Override
    public Map<String, Object> playerInfo() {
        LocalPlayer p = mc().player;
        if (p == null) return null;
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", p.getGameProfile().name());
        info.put("health", p.getHealth());
        info.put("max_health", p.getMaxHealth());
        info.put("armor", p.getArmorValue());
        info.put("food", p.getFoodData().getFoodLevel());
        info.put("saturation", p.getFoodData().getSaturationLevel());
        info.put("air", p.getAirSupply());
        info.put("level", p.experienceLevel);
        info.put("xp_progress", p.experienceProgress);
        info.put("selected_slot", p.getInventory().getSelectedSlot());
        info.put("held_item", BuiltInRegistries.ITEM.getKey(p.getMainHandItem().getItem()).toString());
        info.put("on_ground", p.onGround());
        info.put("sneaking", p.isShiftKeyDown());
        info.put("sprinting", p.isSprinting());
        info.put("creative", p.isCreative());
        info.put("spectator", p.isSpectator());
        if (worldAllowed()) {
            info.put("x", p.getX());
            info.put("y", p.getY());
            info.put("z", p.getZ());
            info.put("yaw", p.getYRot());
            info.put("pitch", p.getXRot());
        }
        return info;
    }

    @Override
    public List<Map<String, Object>> inventory() {
        List<Map<String, Object>> result = new ArrayList<>();
        LocalPlayer p = mc().player;
        if (p == null) return result;
        for (int slot = 0; slot < p.getInventory().getContainerSize(); slot++) {
            ItemStack stack = p.getInventory().getItem(slot);
            if (stack.isEmpty()) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("slot", slot);
            item.put("id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            item.put("count", stack.getCount());
            item.put("damage", stack.getDamageValue());
            item.put("max_damage", stack.getMaxDamage());
            result.add(item);
        }
        return result;
    }

    @Override
    public Map<String, Object> lookingAt(double reach) {
        if (!worldAllowed()) return null;
        HitResult hit = mc().player.pick(reach, 1.0F, false);
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) return null;
        Map<String, Object> info = new LinkedHashMap<>();
        BlockPos pos = block.getBlockPos();
        info.put("block", BuiltInRegistries.BLOCK.getKey(mc().level.getBlockState(pos).getBlock()).toString());
        info.put("x", pos.getX());
        info.put("y", pos.getY());
        info.put("z", pos.getZ());
        info.put("face", block.getDirection().getName());
        return info;
    }

    @Override
    public Map<String, Object> worldInfo() {
        if (!worldAllowed()) return null;
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("dimension", mc().level.dimension().identifier().toString());
        info.put("game_time", mc().level.getGameTime());
        info.put("raining", mc().level.isRaining());
        info.put("thundering", mc().level.isThundering());
        return info;
    }

    @Override
    public Integer lightAt(int x, int y, int z) {
        return worldAllowed() ? mc().level.getMaxLocalRawBrightness(new BlockPos(x, y, z)) : null;
    }

    @Override
    public String biomeAt(int x, int y, int z) {
        if (!worldAllowed()) return null;
        return mc().level.getBiome(new BlockPos(x, y, z)).unwrapKey().map(key -> key.identifier().toString()).orElse(null);
    }

    @Override
    public List<Map<String, Object>> entityList(double radius) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (!worldAllowed()) return result;
        LocalPlayer self = mc().player;
        double limit = Math.min(256, radius);
        for (Entity entity : mc().level.entitiesForRendering()) {
            if (entity == self || entity.isRemoved() || entity.distanceTo(self) > limit) continue;
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("id", entity.getId());
            info.put("type", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
            info.put("kind", classify(entity));
            info.put("name", PluginDefinition.clean(entity.getName().getString()));
            info.put("x", entity.getX());
            info.put("y", entity.getY());
            info.put("z", entity.getZ());
            info.put("distance", (double) entity.distanceTo(self));
            if (entity instanceof LivingEntity living) {
                info.put("health", living.getHealth());
                info.put("max_health", living.getMaxHealth());
            }
            result.add(info);
        }
        return result;
    }

    @Override
    public boolean setLook(double yaw, double pitch) {
        if (!worldAllowed()) return false;
        mc().player.setYRot((float) yaw);
        mc().player.setXRot((float) Math.max(-90, Math.min(90, pitch)));
        return true;
    }

    @Override
    public boolean selectSlot(int slot) {
        if (!worldAllowed() || slot < 0 || slot > 8) return false;
        mc().player.getInventory().setSelectedSlot(slot);
        return true;
    }

    private KeyMapping control(String name) {
        var o = mc().options;
        return switch (name) {
            case "forward" -> o.keyUp;
            case "back" -> o.keyDown;
            case "left" -> o.keyLeft;
            case "right" -> o.keyRight;
            case "jump" -> o.keyJump;
            case "sneak" -> o.keyShift;
            case "sprint" -> o.keySprint;
            case "attack" -> o.keyAttack;
            case "use" -> o.keyUse;
            default -> throw new IllegalArgumentException(name);
        };
    }

    @Override
    public boolean setControl(String name, boolean down) {
        if (!worldAllowed() || GameCompat.screenOpen()) return false;
        control(name).setDown(down);
        return true;
    }

    @Override
    public void releaseControls() {
        for (String name : CONTROLS) control(name).setDown(false);
    }

    /** Keys are never reported while a menu or the chat is open, so plugins cannot read what you type. */
    @Override
    public boolean keyDown(String key) {
        if (GameCompat.screenOpen()) return false;
        if (!key.startsWith("key.keyboard.")) throw new org.luaj.vm2.LuaError("Only keyboard keys are supported");
        try {
            return GameCompat.keyDown(InputConstants.getKey(key).getValue());
        } catch (IllegalArgumentException e) {
            throw new org.luaj.vm2.LuaError("Unknown key: " + key);
        } catch (ReflectiveOperationException e) {
            throw new org.luaj.vm2.LuaError("Key input is unavailable");
        }
    }

    @Override
    public boolean playSound(String soundId, double volume, double pitch) {
        Identifier id = Identifier.tryParse(soundId);
        if (id == null || !BuiltInRegistries.SOUND_EVENT.containsKey(id)) return false;
        mc().getSoundManager().play(SimpleSoundInstance.forUI(BuiltInRegistries.SOUND_EVENT.getValue(id),
            (float) Math.max(0.5, Math.min(2, pitch)), (float) Math.max(0, Math.min(1, volume))));
        return true;
    }

    @Override
    public void actionBar(String text) {
        if (mc().player != null) mc().player.sendOverlayMessage(Component.literal(text));
    }

    @Override
    public void title(String title, String subtitle) {
        try {
            GameCompat.title(Component.literal(title), Component.literal(subtitle));
        } catch (ReflectiveOperationException e) {
            throw new org.luaj.vm2.LuaError("Titles are unavailable");
        }
    }

    @Override
    public Object option(String name) {
        var o = mc().options;
        return switch (name) {
            case "fov" -> o.fov().get();
            case "gamma" -> o.gamma().get();
            case "render_distance" -> o.renderDistance().get();
            case "view_bobbing" -> o.bobView().get();
            default -> throw new org.luaj.vm2.LuaError("Unknown option: " + name);
        };
    }

    /** Changes last until the game closes unless the player saves their options. */
    @Override
    public boolean setOption(String name, Object value) {
        var o = mc().options;
        switch (name) {
            case "fov" -> o.fov().set(((Double) value).intValue());
            case "gamma" -> o.gamma().set((Double) value);
            case "render_distance" -> o.renderDistance().set(((Double) value).intValue());
            case "view_bobbing" -> o.bobView().set((Boolean) value);
            default -> throw new org.luaj.vm2.LuaError("Unknown option: " + name);
        }
        return true;
    }

    @Override
    public boolean xraySetActive(boolean active) {
        if (!worldAllowed()) return false;
        if (XrayState.getInstance().isActive() != active) XrayToggleHandler.toggleXray(mc());
        return true;
    }

    @Override
    public boolean xrayNightVision() {
        return XrayState.getInstance().isNightVision();
    }

    @Override
    public void xraySetNightVision(boolean on) {
        XrayState.getInstance().setNightVision(on);
        ConfigManager.save();
    }

    @Override
    public int xrayRenderDistance() {
        return XrayState.getInstance().getRenderDistance();
    }

    @Override
    public void xraySetRenderDistance(int distance) {
        XrayState.getInstance().setRenderDistance(distance);
        ConfigManager.save();
        if (XrayState.getInstance().isActive()) XrayToggleHandler.rebuildAllSections(mc());
    }

    void beginFrame() {
        boxesThisFrame = 0;
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    private static boolean worldAllowed() {
        Minecraft mc = mc();
        return mc.level != null && mc.player != null
            && (mc.getCurrentServer() == null || HandshakeState.getInstance().isServerHasMod());
    }

    @Override
    public String blockAt(int x, int y, int z) {
        if (!worldAllowed()) return null;
        return BuiltInRegistries.BLOCK.getKey(mc().level.getBlockState(new BlockPos(x, y, z)).getBlock()).toString();
    }

    @Override
    public double[] playerPosition() {
        if (!worldAllowed()) return null;
        Vec3 p = mc().player.position();
        return new double[]{p.x, p.y, p.z};
    }

    @Override
    public List<EntityBox> entities(double radius) {
        List<EntityBox> result = new ArrayList<>();
        if (!worldAllowed()) return result;
        Minecraft mc = mc();
        double limit = Math.min(256, radius);
        double limitSqr = limit * limit;
        Vec3 cam = cameraPosition(mc);
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity.isRemoved() || entity.isSpectator()) continue;
            Vec3 pos = entity.getPosition(partialTick);
            if (pos.distanceToSqr(cam) > limitSqr) continue;
            Vec3 delta = pos.subtract(entity.position());
            AABB box = entity.getBoundingBox().move(delta.x, delta.y, delta.z);
            result.add(new EntityBox(classify(entity), box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ));
        }
        return result;
    }

    private static String classify(Entity entity) {
        if (entity instanceof Player) return "player";
        MobCategory category = entity.getType().getCategory();
        if (category == MobCategory.MONSTER) return "hostile";
        if (category == MobCategory.MISC) return "other";
        return "friendly";
    }

    private static Vec3 cameraPosition(Minecraft mc) {
        try {
            Method mainCamera = mc.gameRenderer.getClass().getMethod("mainCamera");
            Object camera = mainCamera.invoke(mc.gameRenderer);
            return (Vec3) camera.getClass().getMethod("position").invoke(camera);
        } catch (Exception e) {
            try {
                Camera camera = (Camera) mc.gameRenderer.getClass().getMethod("getCamera").invoke(mc.gameRenderer);
                return camera.position();
            } catch (Exception ex) {
                return Vec3.ZERO;
            }
        }
    }

    @Override
    public void mine(List<Pos> blocks, String silkMode, int durabilityCost) {
        if (!worldAllowed() || mc().gameMode == null || blocks.isEmpty()) return;
        Minecraft mc = mc();
        ItemStack tool = mc.player.getMainHandItem();
        var silkTouch = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH);
        boolean had = EnchantmentHelper.getItemEnchantmentLevel(silkTouch, tool) > 0;
        boolean forceOn = silkMode.equals("always") && !had;
        boolean forceOff = silkMode.equals("never") && had;
        if (forceOn) EnchantmentHelper.updateEnchantments(tool, m -> m.set(silkTouch, 1));
        if (forceOff) EnchantmentHelper.updateEnchantments(tool, m -> m.set(silkTouch, 0));
        int mined = 0;
        try {
            BlockPos player = mc.player.blockPosition();
            for (Pos p : blocks) {
                BlockPos pos = new BlockPos(p.x(), p.y(), p.z());
                BlockState state = mc.level.getBlockState(pos);
                if (state.isAir() || state.getDestroySpeed(mc.level, pos) < 0 || !pos.closerThan(player, MAX_MINE_DISTANCE)) continue;
                mined++;
                if (!mineSingleplayer(mc, pos)) {
                    mc.player.connection.send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, Direction.DOWN));
                }
            }
        } finally {
            if (forceOn) EnchantmentHelper.updateEnchantments(tool, m -> m.set(silkTouch, 0));
            if (forceOff) EnchantmentHelper.updateEnchantments(tool, m -> m.set(silkTouch, 1));
        }
        int extra = durabilityCost * Math.max(0, mined - 1);
        if (extra > 0 && !tool.isEmpty()) {
            tool.setDamageValue(Math.min(tool.getDamageValue() + extra, Math.max(0, tool.getMaxDamage() - 1)));
        }
    }

    private static boolean mineSingleplayer(Minecraft mc, BlockPos pos) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) return false;
        ServerLevel level = server.getLevel(mc.level.dimension());
        if (level == null) return false;
        ServerPlayer player = server.getPlayerList().getPlayer(mc.player.getUUID());
        mc.level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_NEIGHBORS | Block.UPDATE_CLIENTS);
        server.execute(() -> level.destroyBlock(pos, true, player, 3));
        return true;
    }

    @Override
    public void drawBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int color, boolean onTop) {
        if (!worldAllowed() || ++boxesThisFrame > MAX_BOXES_PER_FRAME) return;
        var props = Gizmos.cuboid(new AABB(minX, minY, minZ, maxX, maxY, maxZ), GizmoStyle.stroke(color));
        if (onTop) props.setAlwaysOnTop();
    }

    @Override
    public boolean xrayActive() {
        return XrayState.getInstance().isActive();
    }

    @Override
    public Set<String> xrayBlocks() {
        return XrayState.getInstance().getWhitelist().stream().map(b -> BuiltInRegistries.BLOCK.getKey(b).toString()).collect(Collectors.toSet());
    }

    @Override
    public boolean xrayChange(String blockId, boolean add) {
        Identifier id = Identifier.tryParse(blockId);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) return false;
        Block block = BuiltInRegistries.BLOCK.get(id).map(ref -> ref.value()).orElse(Blocks.AIR);
        if (add) XrayState.getInstance().addToWhitelist(block);
        else XrayState.getInstance().removeFromWhitelist(block);
        ConfigManager.save();
        if (XrayState.getInstance().isActive()) XrayToggleHandler.rebuildAllSections(mc());
        return true;
    }

    @Override
    public void chat(String message) {
        if (mc().player != null) mc().player.sendSystemMessage(Component.literal("[Plugin] " + message));
    }

    @Override
    public void log(String pluginId, String message) {
        LoggerFactory.getLogger("XtoXray-" + pluginId).info("{}", message);
    }
}
