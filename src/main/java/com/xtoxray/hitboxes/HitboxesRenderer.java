package com.xtoxray.hitboxes;

import com.xtoxray.plugin.PluginState;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class HitboxesRenderer {
    private static final Minecraft mc = Minecraft.getInstance();

    public static void register() {
        LevelRenderEvents.BEFORE_GIZMOS.register(HitboxesRenderer::render);
    }

    public static void render(LevelRenderContext context) {
        if (!PluginState.getInstance().isInstalled("Hitboxes")) {
            return;
        }
        HitboxesState state = HitboxesState.getInstance();
        if (!state.isEnabled() || mc.level == null || mc.player == null) {
            return;
        }
        double reach = state.getMaxDistance();
        double reachSqr = reach * reach;
        Vec3 camPos = getCameraPosition(mc);
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity.isRemoved() || entity.isSpectator()) {
                continue;
            }
            Vec3 pos = entity.getPosition(partialTick);
            double dx = pos.x - camPos.x;
            double dy = pos.y - camPos.y;
            double dz = pos.z - camPos.z;
            if (dx * dx + dy * dy + dz * dz > reachSqr) {
                continue;
            }
            int category = classify(entity);
            if (!state.isShown(category)) {
                continue;
            }
            AABB box = entity.getBoundingBox();
            Vec3 delta = pos.subtract(entity.position());
            AABB moved = box.move(delta.x, delta.y, delta.z);
            var props = Gizmos.cuboid(moved, GizmoStyle.stroke(state.getColor(category)));
            if (state.isSeeThroughWalls()) {
                props.setAlwaysOnTop();
            }
        }
    }

    private static Vec3 getCameraPosition(Minecraft mc) {
        try {
            Method mainCamera = mc.gameRenderer.getClass().getMethod("mainCamera");
            Object camera = mainCamera.invoke(mc.gameRenderer);
            Method position = camera.getClass().getMethod("position");
            return (Vec3) position.invoke(camera);
        } catch (Exception e) {
            try {
                Method getCamera = mc.gameRenderer.getClass().getMethod("getCamera");
                Camera camera = (Camera) getCamera.invoke(mc.gameRenderer);
                return camera.position();
            } catch (Exception ex) {
                return Vec3.ZERO;
            }
        }
    }

    private static int classify(Entity entity) {
        if (entity instanceof Player) {
            return HitboxesState.CATEGORY_PLAYERS;
        }
        MobCategory category = entity.getType().getCategory();
        if (category == MobCategory.MONSTER) {
            return HitboxesState.CATEGORY_HOSTILE;
        }
        if (category == MobCategory.MISC) {
            return HitboxesState.CATEGORY_OTHER;
        }
        return HitboxesState.CATEGORY_FRIENDLY;
    }
}