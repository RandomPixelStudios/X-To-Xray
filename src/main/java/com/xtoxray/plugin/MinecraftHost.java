package com.xtoxray.plugin;

import com.xtoxray.ConfigManager;
import com.xtoxray.XrayState;
import com.xtoxray.client.XrayToggleHandler;
import com.xtoxray.network.HandshakeState;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.client.Camera;
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
final class MinecraftHost implements PluginHost {
    private static final int MAX_BOXES_PER_FRAME = 4096;
    private static final int MAX_MINE_DISTANCE = 64;
    private int boxesThisFrame;

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
