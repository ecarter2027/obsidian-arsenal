package com.obsidianarsenal.entity;

import com.obsidianarsenal.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

/**
 * A spell's 3D set piece (no hitbox, never saved): drawn by client/render/SpellVisualRenderer from its kind and age,
 * while the spell itself runs the gameplay on the server on the same clock. Sits on the ground at the spell's target.
 *  ORBITAL - Railgun: targeting reticle, the orbital cannon unfolding and charging overhead, the beam, the blast
 *  KIRIN   - Kirin: the storm cloud gathering and the lightning dragon diving out of it onto the target
 *  CIRCLE  - a school's magic circle on the ground (or, raised, facing down from above) for a spell's area
 */
public class SpellVisualEntity extends Entity {
    public static final int ORBITAL = 0, KIRIN = 1, CIRCLE = 2, FLASH = 3;
    /** Ticks of each kind's sequence (the spells time their hits to these). */
    public static final int ORBITAL_LIFE = 130, ORBITAL_FIRE = 72, KIRIN_LIFE = 50, KIRIN_STRIKE = 22;

    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(SpellVisualEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> YAW = SynchedEntityData.defineId(SpellVisualEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> SCHOOL = SynchedEntityData.defineId(SpellVisualEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(SpellVisualEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(SpellVisualEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> RAISE = SynchedEntityData.defineId(SpellVisualEntity.class, EntityDataSerializers.FLOAT);

    public SpellVisualEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    public static SpellVisualEntity spawn(ServerLevel level, int kind, Vec3 at, float yaw) {
        SpellVisualEntity e = new SpellVisualEntity(ModEntities.SPELL_VISUAL.get(), level);
        e.setPos(at.x, at.y, at.z);
        e.entityData.set(KIND, kind);
        e.entityData.set(YAW, yaw);
        level.addFreshEntity(e);
        return e;
    }

    /**
     * A magic circle for {@code life} ticks: school 0 obsidian / 1 hellfire / 2 plasma, lying on the ground at
     * {@code at}, or raised {@code raise} blocks and facing down (raining spells).
     */
    public static SpellVisualEntity circle(ServerLevel level, int school, Vec3 at, double radius, int life, double raise) {
        SpellVisualEntity e = new SpellVisualEntity(ModEntities.SPELL_VISUAL.get(), level);
        e.setPos(at.x, at.y, at.z);
        e.entityData.set(KIND, CIRCLE);
        e.entityData.set(SCHOOL, school);
        e.entityData.set(RADIUS, (float) radius);
        e.entityData.set(LIFE, life);
        e.entityData.set(RAISE, (float) raise);
        e.entityData.set(YAW, level.random.nextFloat() * 6.28f);
        level.addFreshEntity(e);
        return e;
    }

    /** A glowing flash at {@code at} (school 0 obsidian / 1 hellfire / 2 plasma / 3 black), radius r, lasting life ticks. */
    public static SpellVisualEntity flash(ServerLevel level, int school, Vec3 at, double r, int life) {
        SpellVisualEntity e = circle(level, school, at, r, life, 0);
        e.entityData.set(KIND, FLASH);
        return e;
    }

    public int school() { return entityData.get(SCHOOL); }
    public float radius() { return entityData.get(RADIUS); }
    public float raise() { return entityData.get(RAISE); }

    public int kind() { return entityData.get(KIND); }
    public float facing() { return entityData.get(YAW); }
    public int life() { return switch (kind()) { case ORBITAL -> ORBITAL_LIFE; case KIRIN -> KIRIN_LIFE; default -> entityData.get(LIFE); }; }

    @Override
    protected void defineSynchedData() {
        entityData.define(KIND, ORBITAL);
        entityData.define(YAW, 0f);
        entityData.define(SCHOOL, 0);
        entityData.define(RADIUS, 3f);
        entityData.define(LIFE, 40);
        entityData.define(RAISE, 0f);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount > life()) discard();
    }

    /** Tall enough that the cannon high overhead is never culled with the reticle out of view. */
    @Override public AABB getBoundingBoxForCulling() { return getBoundingBox().inflate(14, 70, 14).move(0, 50, 0); }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 300 * 300; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean shouldBeSaved() { return false; }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
}
