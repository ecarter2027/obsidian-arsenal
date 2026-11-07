package com.obsidianarsenal.efcompat;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import yesman.epicfight.api.animation.AnimationPlayer;
import yesman.epicfight.api.animation.types.EntityState;
import yesman.epicfight.api.client.camera.EpicFightCameraAPI;
import yesman.epicfight.network.client.CPSkillRequest;
import yesman.epicfight.skill.SkillContainer;
import yesman.epicfight.skill.dodge.DodgeSkill;
import yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;

/**
 * Obsidian Stretch: the body stretches from where the player stands to where they are going, then snaps them there.
 * The direction is the movement keys taken against the view (pitch included, so looking up or down dashes that way);
 * with no key held it is straight along the view. Up to {@link #RANGE} blocks, stopping short of walls. The dodge
 * animation gives the usual dodge invulnerability; the move and the stretch itself are done by client/StretchFx.
 * Unlike the vanilla Epic Fight dodges it also works in the air, costs nothing and has no cooldown.
 */
public class StretchDodgeSkill extends DodgeSkill {
    public static final float RANGE = 7.0F;

    public StretchDodgeSkill(DodgeSkill.Builder builder) {
        super(builder);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public Object getExecutionPacket(SkillContainer container, FriendlyByteBuf args) {
        LocalPlayer player = container.getClientExecutor().getOriginal();
        EpicFightCameraAPI camera = EpicFightCameraAPI.getInstance();
        float yaw = camera.getForwardYRot(), pitch = camera.getForwardXRot();
        // the keys as they are this instant, plus any tapped just before the dodge key (client/DodgeKeys)
        float forward = com.obsidianarsenal.efcompat.client.DodgeKeys.forward(), left = com.obsidianarsenal.efcompat.client.DodgeKeys.left();
        Vec3 look = Vec3.directionFromRotation(pitch, yaw);
        Vec3 leftward = Vec3.directionFromRotation(0.0F, yaw - 90.0F);
        Vec3 dir = forward == 0 && left == 0 ? look : look.scale(forward).add(leftward.scale(left));
        dir = dir.lengthSqr() < 1.0E-4 ? look : dir.normalize();

        Vec3 from = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
        HitResult hit = player.level().clip(new ClipContext(from, from.add(dir.scale(RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        float dist = hit.getType() == HitResult.Type.MISS ? RANGE : (float) Math.max(0.0, hit.getLocation().distanceTo(from) - 0.6);

        double flat = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        float faceYaw = flat > 0.2 ? (float) Math.toDegrees(Math.atan2(-dir.x, dir.z)) : yaw;
        CPSkillRequest packet = new CPSkillRequest(container.getSlot());
        packet.getBuffer().writeInt(0);                         // DodgeSkill's own fields: animation, model facing
        packet.getBuffer().writeFloat(Mth.wrapDegrees(faceYaw));
        packet.getBuffer().writeFloat((float) dir.x);
        packet.getBuffer().writeFloat((float) dir.y);
        packet.getBuffer().writeFloat((float) dir.z);
        packet.getBuffer().writeFloat(dist);
        return packet;
    }

    @Override
    public void executeOnServer(SkillContainer container, FriendlyByteBuf args) {
        super.executeOnServer(container, args);                 // stamina, dodge animation, facing
        ServerPlayerPatch executor = container.getServerExecutor();
        Vec3 dir = new Vec3(args.readFloat(), args.readFloat(), args.readFloat());
        float dist = Mth.clamp(args.readFloat(), 0.0F, RANGE);
        if (dir.lengthSqr() < 1.0E-4 || !Double.isFinite(dir.lengthSqr())) return;
        StretchNet.send(executor.getOriginal(), executor.getOriginal().position(), dir.normalize(), dist);
    }

    @Override
    public boolean isExecutableState(PlayerPatch<?> executor) {
        EntityState state = executor.getEntityState();
        // no cooldown: a new Stretch can start straight out of the last one
        AnimationPlayer player = executor.getAnimator().getPlayerFor(null);
        boolean stretching = player != null && player.getRealAnimation() != null
                && player.getRealAnimation().get() == GauntletAnimations.STRETCH_DODGE.get();
        return (state.canUseSkill() || stretching) && !executor.getOriginal().isInWater() && !executor.getOriginal().onClimbable()
                && executor.getOriginal().getVehicle() == null;
    }
}
