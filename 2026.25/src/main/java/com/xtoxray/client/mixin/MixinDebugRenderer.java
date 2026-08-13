/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.renderer.culling.Frustum
 *  net.minecraft.client.renderer.debug.DebugRenderer
 *  net.minecraft.core.registries.BuiltInRegistries
 *  net.minecraft.gizmos.GizmoStyle
 *  net.minecraft.gizmos.Gizmos
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.phys.AABB
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.xtoxray.client.mixin;

import com.xtoxray.XrayState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={DebugRenderer.class})
public class MixinDebugRenderer {
    @Inject(method={"emitGizmos(Lnet/minecraft/client/renderer/culling/Frustum;DDDF)V"}, at={@At(value="RETURN")}, require=0)
    private void onEmitGizmos(Frustum frustum, double camX, double camY, double camZ, float tickDelta, CallbackInfo ci) {
        if (!XrayState.getInstance().isShowHitboxes() || !XrayState.getInstance().isActive()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        XrayState xray = XrayState.getInstance();
        for (Entity entity : mc.level.entitiesForRendering()) {
            String typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
            if (!xray.isHitboxEntityWhitelisted(typeId)) continue;
            Gizmos.cuboid((AABB)entity.getBoundingBox(), (GizmoStyle)GizmoStyle.stroke((int)-65536));
        }
    }
}

