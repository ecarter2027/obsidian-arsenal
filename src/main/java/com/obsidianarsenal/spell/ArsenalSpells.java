package com.obsidianarsenal.spell;

import com.obsidianarsenal.ObsidianArsenal;
import com.obsidianarsenal.effect.AmaterasuEffect;
import com.obsidianarsenal.entity.ConstructEntity;
import com.obsidianarsenal.entity.ConstructEntity.Kind;
import com.obsidianarsenal.entity.BlackHoleEntity;
import com.obsidianarsenal.entity.HellfireStreamEntity;
import com.obsidianarsenal.entity.PlasmaBeamEntity;
import com.obsidianarsenal.entity.IndraArrowEntity;
import com.obsidianarsenal.entity.ObsidianOrbProjectile;
import com.obsidianarsenal.registry.ModEffects;
import com.obsidianarsenal.registry.ModSchools;
import com.obsidianarsenal.util.Scheduler;
import com.obsidianarsenal.util.SpellUtil;
import com.obsidianarsenal.util.Vfx;
import com.obsidianarsenal.util.Vfx.Effek;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.*;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The ten spells, built on Iron's Spells 'n Spellbooks {@link AbstractSpell}:
 *  - schools are this mod's Obsidian / Hellfire SchoolTypes (Iron's school power + resist attributes apply)
 *  - damage goes through Iron's SpellDamageSource / DamageSources.applyDamage
 *  - casting animations are Iron's AnimationHolders pointing at our PlayerAnimator files
 *  - mana, cooldowns, levels, rarities, scrolls, config (@AutoSpellConfig) are all Iron's
 */
public final class ArsenalSpells {
    private ArsenalSpells() {}

    // Player animations (assets/obsidian_arsenal/player_animation/obsidian_arsenal_casts.json), played by Iron's cast system.
    public static final AnimationHolder ANIM_SLAM = new AnimationHolder(ObsidianArsenal.id("obs_cast_slam"), true, false);
    public static final AnimationHolder ANIM_THRUST = new AnimationHolder(ObsidianArsenal.id("obs_cast_thrust"), true, false);
    public static final AnimationHolder ANIM_CHANNEL = new AnimationHolder(ObsidianArsenal.id("obs_cast_channel"), false, false);
    public static final AnimationHolder ANIM_BOW_CHARGE = new AnimationHolder(ObsidianArsenal.id("obs_cast_bow_charge"), false, true);
    public static final AnimationHolder ANIM_PUSH = new AnimationHolder(ObsidianArsenal.id("obs_cast_push"), true, false);
    public static final AnimationHolder ANIM_BOW_RELEASE = new AnimationHolder(ObsidianArsenal.id("obs_cast_bow_release"), true, true);

    // ======================================================================================================
    public abstract static class Base extends AbstractSpell {
        private final ResourceLocation id;
        private final DefaultConfig config;
        private final CastType castType;

        protected Base(String name, ResourceLocation school, SpellRarity rarity, int maxLevel, int cooldownSeconds,
                       CastType castType, int castTimeTicks, int baseMana, int manaPerLevel, int basePower, int powerPerLevel) {
            this.id = ObsidianArsenal.id(name);
            this.castType = castType;
            this.config = new DefaultConfig()
                    .setMinRarity(rarity)
                    .setSchoolResource(school)
                    .setMaxLevel(maxLevel)
                    .setCooldownSeconds(cooldownSeconds)
                    .build();
            this.manaCostPerLevel = manaPerLevel;
            this.baseSpellPower = basePower;
            this.spellPowerPerLevel = powerPerLevel;
            this.castTime = castTimeTicks;
            this.baseManaCost = baseMana;
        }

        @Override public ResourceLocation getSpellResource() { return id; }
        @Override public DefaultConfig getDefaultConfig() { return config; }
        @Override public CastType getCastType() { return castType; }
        @Override public AnimationHolder getCastStartAnimation() { return ANIM_SLAM; }
        @Override public AnimationHolder getCastFinishAnimation() { return AnimationHolder.pass(); }

        protected static MutableComponent dmg(float v) { return Component.translatable("ui.irons_spellbooks.damage", Utils.stringTruncation(v, 1)); }
        protected static MutableComponent aoe(float v) { return Component.translatable("ui.irons_spellbooks.aoe_damage", Utils.stringTruncation(v, 1)); }
        protected static MutableComponent radius(float v) { return Component.translatable("ui.irons_spellbooks.radius", Utils.stringTruncation(v, 1)); }
        protected static MutableComponent range(float v) { return Component.translatable("ui.irons_spellbooks.distance", Utils.stringTruncation(v, 1)); }
        protected static MutableComponent length(int ticks) { return Component.translatable("ui.irons_spellbooks.effect_length", Utils.timeFromTicks(ticks, 1)); }
    }

    private static final ResourceLocation OBSIDIAN = ModSchools.OBSIDIAN_RESOURCE;
    private static final ResourceLocation HELLFIRE = ModSchools.HELLFIRE_RESOURCE;
    private static final ResourceLocation PLASMA = ModSchools.PLASMA_RESOURCE;

    // ====================================================================================== OBSIDIAN SCHOOL
    /** 1. Obsidian Spike Explosion - orb flies where you look; on impact animated spikes erupt from the ground. */
    @AutoSpellConfig
    public static class ObsidianSpikeExplosion extends Base {
        public ObsidianSpikeExplosion() { super("obsidian_spike_explosion", OBSIDIAN, SpellRarity.RARE, 8, 8, CastType.INSTANT, 0, 35, 6, 6, 1); }

        float damage(int lvl, LivingEntity c) { return 4f + getSpellPower(lvl, c) * 0.9f; }

        @Override public List<MutableComponent> getUniqueInfo(int lvl, LivingEntity c) { return List.of(aoe(damage(lvl, c)), dmg(damage(lvl, c) * 0.5f)); }
        @Override public AnimationHolder getCastStartAnimation() { return ANIM_THRUST; }

        @Override
        public void onCast(Level level, int lvl, LivingEntity caster, CastSource src, MagicData data) {
            ObsidianOrbProjectile orb = new ObsidianOrbProjectile(level, caster);
            orb.damage = damage(lvl, caster);
            Vec3 look = caster.getLookAngle();
            Vec3 pos = caster.getEyePosition().add(look.scale(0.8)).add(0, -0.25, 0);
            orb.setPos(pos.x, pos.y, pos.z);
            orb.shoot(look.x, look.y, look.z, 1.7f, 0.0f);
            level.addFreshEntity(orb);
            level.playSound(null, caster.blockPosition(), SoundEvents.ENDER_DRAGON_SHOOT, SoundSource.PLAYERS, 0.8f, 0.7f);
            super.onCast(level, lvl, caster, src, data);
        }
    }

    /** 2. Obsidian Storm - swirling obsidian spikes circle the caster for a few seconds. */
    @AutoSpellConfig
    public static class ObsidianStorm extends Base {
        public ObsidianStorm() { super("obsidian_storm", OBSIDIAN, SpellRarity.EPIC, 6, 18, CastType.INSTANT, 0, 55, 8, 5, 1); }

        float damage(int lvl, LivingEntity c) { return 2.5f + getSpellPower(lvl, c) * 0.45f; }

        @Override public List<MutableComponent> getUniqueInfo(int lvl, LivingEntity c) { return List.of(dmg(damage(lvl, c)), length(Kind.STORM_SPIKE.lifetime)); }

        @Override
        public void onCast(Level level, int lvl, LivingEntity caster, CastSource src, MagicData data) {
            if (level instanceof ServerLevel sl) {
                for (int i = 0; i < 8; i++) ConstructEntity.spawn(sl, Kind.STORM_SPIKE, caster, caster.position(), 0f, damage(lvl, caster), i * 45f);
                Vfx.effekOn(sl, Effek.OBSIDIAN_STORM_VORTEX, caster, Vec3.ZERO, 1.6f);
                Vfx.ring(sl, Vfx.purpleSpark(), caster.position(), 3.2, 40, 0.2, 0.05);
                sl.playSound(null, caster.blockPosition(), SoundEvents.TRIDENT_RIPTIDE_3, SoundSource.PLAYERS, 1.0f, 0.6f);
            }
            super.onCast(level, lvl, caster, src, data);
        }
    }

    /** 3. Obsidian Tentacles - animated tentacles burst from the ground angled outward. */
    @AutoSpellConfig
    public static class ObsidianTentacles extends Base {
        public ObsidianTentacles() { super("obsidian_tentacles", OBSIDIAN, SpellRarity.EPIC, 6, 22, CastType.INSTANT, 0, 60, 8, 5, 1); }

        float damage(int lvl, LivingEntity c) { return 2f + getSpellPower(lvl, c) * 0.35f; }

        @Override public List<MutableComponent> getUniqueInfo(int lvl, LivingEntity c) { return List.of(dmg(damage(lvl, c)), length(Kind.TENTACLE.lifetime)); }

        @Override
        public void onCast(Level level, int lvl, LivingEntity caster, CastSource src, MagicData data) {
            if (level instanceof ServerLevel sl) {
                for (int i = 0; i < 6; i++) {
                    double a = Math.toRadians(i * 60.0 + 20);
                    double dx = Math.cos(a), dz = Math.sin(a);
                    double x = caster.getX() + dx * 3.0, z = caster.getZ() + dz * 3.0;
                    double y = SpellUtil.groundY(sl, x, caster.getY() + 1, z);
                    ConstructEntity.spawn(sl, Kind.TENTACLE, caster, new Vec3(x, y, z), SpellUtil.randomCardinal(sl.random), damage(lvl, caster), 0);
                }
                sl.playSound(null, caster.blockPosition(), SoundEvents.WARDEN_EMERGE, SoundSource.PLAYERS, 0.8f, 1.2f);
            }
            super.onCast(level, lvl, caster, src, data);
        }
    }

    /** 4. Purple Lightning Cloak - "Purple Lightning" status: 2x speed + purple afterimage trail. */
    @AutoSpellConfig
    public static class PurpleLightningCloak extends Base {
        public PurpleLightningCloak() { super("purple_lightning_cloak", OBSIDIAN, SpellRarity.RARE, 5, 25, CastType.INSTANT, 0, 40, 6, 1, 1); }

        int duration(int lvl) { return 20 * (8 + lvl * 2); }

        @Override public List<MutableComponent> getUniqueInfo(int lvl, LivingEntity c) {
            return List.of(length(duration(lvl)), Component.translatable("ui.obsidian_arsenal.speed_bonus", "100%"));
        }

        @Override
        public void onCast(Level level, int lvl, LivingEntity caster, CastSource src, MagicData data) {
            caster.addEffect(new MobEffectInstance(ModEffects.PURPLE_LIGHTNING.get(), duration(lvl), 0, false, true, true));
            Vfx.effekOn(level, Effek.PURPLE_LIGHTNING_BURST, caster, Vec3.ZERO, 1.0f);
            Vfx.burst(level, Vfx.purpleSpark(), caster.position().add(0, 1, 0), 40, 2.0, 0.35);
            Vfx.bolt(level, caster.position().add(0, 3.5, 0), caster.position().add(0, 0.1, 0));
            level.playSound(null, caster.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.6f, 1.6f);
            super.onCast(level, lvl, caster, src, data);
        }
    }

    /** 5. Obsidian Shatter - shards erupt in a ~6 block line toward where you look. */
    @AutoSpellConfig
    public static class ObsidianShatter extends Base {
        public ObsidianShatter() { super("obsidian_shatter", OBSIDIAN, SpellRarity.UNCOMMON, 8, 10, CastType.INSTANT, 0, 30, 5, 5, 1); }

        float damage(int lvl, LivingEntity c) { return 3f + getSpellPower(lvl, c) * 0.55f; }

        @Override public List<MutableComponent> getUniqueInfo(int lvl, LivingEntity c) { return List.of(dmg(damage(lvl, c)), range(6)); }
        @Override public AnimationHolder getCastStartAnimation() { return ANIM_THRUST; }

        @Override
        public void onCast(Level level, int lvl, LivingEntity caster, CastSource src, MagicData data) {
            if (level instanceof ServerLevel sl) {
                Vec3 look = caster.getLookAngle();
                Vec3 dir = new Vec3(look.x, 0, look.z);
                if (dir.lengthSqr() < 1e-4) dir = Vec3.directionFromRotation(0, caster.getYRot());
                final Vec3 d = dir.normalize();
                int shards = (int) Math.round(Mth.clamp(SpellUtil.blockDistance(caster, 6.0), 3.0, 6.0));
                float dmg = damage(lvl, caster);
                for (int i = 1; i <= shards; i++) {
                    final int idx = i;
                    Scheduler.later(sl, i * 2, () -> {
                        double x = caster.getX() + d.x * idx, z = caster.getZ() + d.z * idx;
                        double y = SpellUtil.groundY(sl, x, caster.getY() + 1, z);
                        ConstructEntity.spawn(sl, Kind.SHARD, caster, new Vec3(x, y, z), SpellUtil.randomCardinal(sl.random), dmg, 0);
                        Vfx.burst(sl, Vfx.obsidianChunk(), new Vec3(x, y + 0.3, z), 10, 0.8, 0.3);
                    });
                }
                sl.playSound(null, caster.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0f, 0.5f);
            }
            super.onCast(level, lvl, caster, src, data);
        }
    }

    // ====================================================================================== HELLFIRE SCHOOL
    /** 6. Columns of Hell - 4 swirling flame columns orbit the caster. */
    @AutoSpellConfig
    public static class ColumnsOfHell extends Base {
        public ColumnsOfHell() { super("columns_of_hell", HELLFIRE, SpellRarity.EPIC, 6, 20, CastType.INSTANT, 0, 60, 8, 6, 1); }

        float damage(int lvl, LivingEntity c) { return 4f + getSpellPower(lvl, c) * 0.7f; }

        @Override public List<MutableComponent> getUniqueInfo(int lvl, LivingEntity c) { return List.of(dmg(damage(lvl, c)), length(Kind.COLUMN.lifetime)); }

        @Override
        public void onCast(Level level, int lvl, LivingEntity caster, CastSource src, MagicData data) {
            if (level instanceof ServerLevel sl) {
                for (int i = 0; i < 4; i++) ConstructEntity.spawn(sl, Kind.COLUMN, caster, caster.position(), 0f, damage(lvl, caster), i * 90f);
                Vfx.ring(sl, Vfx.hellfire(), caster.position(), 3.0, 36, 0.15, 0.1);
                sl.playSound(null, caster.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.2f, 0.5f);
            }
            super.onCast(level, lvl, caster, src, data);
        }
    }

    /** 7. Flaming Judgement - continuous stream of red hellfire, up to 10 blocks (Iron's CONTINUOUS cast). */
    @AutoSpellConfig
    public static class FlamingJudgement extends Base {
        public static final double RANGE = HellfireStreamEntity.RANGE;

        public FlamingJudgement() { super("flaming_judgement", HELLFIRE, SpellRarity.RARE, 8, 10, CastType.CONTINUOUS, 100, 8, 2, 4, 1); }

        float damage(int lvl, LivingEntity c) { return 1.8f + getSpellPower(lvl, c) * 0.25f; }

        @Override public List<MutableComponent> getUniqueInfo(int lvl, LivingEntity c) { return List.of(dmg(damage(lvl, c) * 10f), range((float) RANGE)); }
        @Override public AnimationHolder getCastStartAnimation() { return ANIM_CHANNEL; }
        @Override public AnimationHolder getCastFinishAnimation() { return AnimationHolder.none(); }

        /**
         * Iron's calls this every 10 ticks while channeling (mana is drained here). The first pulse spawns the
         * stream entity, later pulses keep it alive; when the channel stops the stream dies out on its own.
         */
        @Override
        public void onCast(Level level, int lvl, LivingEntity caster, CastSource src, MagicData data) {
            if (level instanceof ServerLevel sl) {
                HellfireStreamEntity stream = stream(sl, caster);
                if (stream == null) {
                    stream = HellfireStreamEntity.spawn(sl, caster, damage(lvl, caster));
                    caster.getPersistentData().putInt(STREAM_TAG, stream.getId());
                }
                stream.keepAlive(damage(lvl, caster));
                Vfx.effekFromHead(sl, Effek.HELLFIRE_STREAM, caster, new Vec3(0, -0.25, 0.6), 1.0f);
            }
            super.onCast(level, lvl, caster, src, data);
        }

        @Override
        public void onServerCastComplete(Level level, int lvl, LivingEntity caster, MagicData data, boolean cancelled) {
            if (level instanceof ServerLevel sl) {
                HellfireStreamEntity stream = stream(sl, caster);
                if (stream != null) stream.discard();
                caster.getPersistentData().remove(STREAM_TAG);
            }
            super.onServerCastComplete(level, lvl, caster, data, cancelled);
        }

        private static final String STREAM_TAG = "OA_HellStream";

        @Nullable
        private static HellfireStreamEntity stream(ServerLevel level, LivingEntity caster) {
            if (!caster.getPersistentData().contains(STREAM_TAG)) return null;
            return level.getEntity(caster.getPersistentData().getInt(STREAM_TAG)) instanceof HellfireStreamEntity s && s.isAlive() ? s : null;
        }
    }

    /** 8. Gyroscope - rotating fire rings that follow the caster and negate all damage and arrows. */
    @AutoSpellConfig
    public static class Gyroscope extends Base {
        public Gyroscope() { super("gyroscope", HELLFIRE, SpellRarity.LEGENDARY, 5, 30, CastType.INSTANT, 0, 80, 10, 1, 1); }

        @Override public List<MutableComponent> getUniqueInfo(int lvl, LivingEntity c) {
            return List.of(length(Kind.RING.lifetime), Component.translatable("ui.obsidian_arsenal.immune_all"));
        }

        @Override
        public void onCast(Level level, int lvl, LivingEntity caster, CastSource src, MagicData data) {
            if (level instanceof ServerLevel sl) {
                for (ConstructEntity e : sl.getEntitiesOfClass(ConstructEntity.class, caster.getBoundingBox().inflate(6), c -> c.kind == Kind.RING && c.isOwnedBy(caster)))
                    e.discard();
                ConstructEntity.spawn(sl, Kind.RING, caster, caster.position(), 0f, 0f, 0f);
                Vfx.ring(sl, Vfx.hellfire(), caster.position(), 2.4, 36, 0.1, 0.1);
                sl.playSound(null, caster.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.2f, 0.5f);
            }
            super.onCast(level, lvl, caster, src, data);
        }
    }

    /** 9. Amaterasu - targeted black flame that burns until the target dies. No target = no cast (no mana, no cooldown). */
    @AutoSpellConfig
    public static class Amaterasu extends Base {
        public static final double RANGE = 40.0;

        public Amaterasu() { super("amaterasu", HELLFIRE, SpellRarity.LEGENDARY, 5, 20, CastType.INSTANT, 0, 70, 10, 1, 1); }

        float damage(int lvl, LivingEntity c) { return 1.5f + getSpellPower(lvl, c) * 0.5f; }

        @Override public List<MutableComponent> getUniqueInfo(int lvl, LivingEntity c) {
            return List.of(Component.translatable("ui.obsidian_arsenal.damage_per_second", Utils.stringTruncation(damage(lvl, c) * 2, 1)),
                    Component.translatable("ui.irons_spellbooks.cast_range", Utils.stringTruncation(RANGE, 0)),
                    Component.translatable("ui.obsidian_arsenal.until_death"));
        }

        @Override public AnimationHolder getCastStartAnimation() { return ANIM_THRUST; }

        @Override
        public boolean checkPreCastConditions(Level level, int lvl, LivingEntity entity, MagicData data) {
            boolean has = SpellUtil.raycastLiving(entity, RANGE) != null;
            if (!has && entity instanceof ServerPlayer sp)
                sp.displayClientMessage(Component.translatable("ui.irons_spellbooks.cast_error_target"), true);
            return has;
        }

        @Override
        public void onCast(Level level, int lvl, LivingEntity caster, CastSource src, MagicData data) {
            LivingEntity target = SpellUtil.raycastLiving(caster, RANGE);
            if (target != null && level instanceof ServerLevel sl) {
                MobEffectInstance inst = new MobEffectInstance(ModEffects.AMATERASU.get(), MobEffectInstance.INFINITE_DURATION, 0, false, true, true);
                inst.setCurativeItems(new ArrayList<>());   // milk / totems can't clear it
                AmaterasuEffect.mark(target, caster.getUUID(), damage(lvl, caster));
                target.addEffect(inst, caster);
                Vec3 mid = target.position().add(0, target.getBbHeight() / 2, 0);
                Vfx.effekOn(sl, Effek.AMATERASU_IGNITE, target, Vec3.ZERO, Math.max(1f, target.getBbHeight() * 0.6f));
                Vfx.burst(sl, Vfx.amaterasu(), mid, 60, 1.2, 0.3);
                Vfx.line(sl, Vfx.amaterasu(), caster.getEyePosition().add(0, -0.3, 0), mid, 0.4, 0.1);
                sl.playSound(null, target.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.6f, 1.6f);
            }
            super.onCast(level, lvl, caster, src, data);
        }
    }

    /**
     * 10. Indra's Arrow - Iron's LONG cast: the arrow materialises and charges while you channel (2s), then flies
     * where you look and annihilates a 25 block radius sphere. Cancelling the cast fizzles the arrow.
     */
    @AutoSpellConfig
    public static class IndrasArrow extends Base {
        private static final String ARROW_TAG = "OA_IndraArrow";

        public IndrasArrow() { super("indras_arrow", OBSIDIAN, SpellRarity.LEGENDARY, 3, 60, CastType.LONG, 40, 150, 40, 10, 2); }

        float damage(int lvl, LivingEntity c) { return 40f + getSpellPower(lvl, c) * 3f; }

        @Override public List<MutableComponent> getUniqueInfo(int lvl, LivingEntity c) { return List.of(aoe(damage(lvl, c)), radius(IndraArrowEntity.RADIUS)); }
        @Override public AnimationHolder getCastStartAnimation() { return ANIM_BOW_CHARGE; }
        @Override public AnimationHolder getCastFinishAnimation() { return ANIM_BOW_RELEASE; }

        @Override
        public void onServerPreCast(Level level, int lvl, LivingEntity caster, @Nullable MagicData data) {
            IndraArrowEntity arrow = new IndraArrowEntity(level, caster);
            arrow.damage = damage(lvl, caster);
            Vec3 pos = caster.getEyePosition().add(caster.getLookAngle().scale(2.4));
            arrow.setPos(pos.x, pos.y, pos.z);
            level.addFreshEntity(arrow);
            caster.getPersistentData().putInt(ARROW_TAG, arrow.getId());
            Vfx.effekOn(level, Effek.INDRA_CHARGE, arrow, Vec3.ZERO, 1.5f);
            level.playSound(null, caster.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2.0f, 0.5f);
            super.onServerPreCast(level, lvl, caster, data);
        }

        @Override
        public void onServerCastTick(Level level, int lvl, LivingEntity caster, @Nullable MagicData data) {
            if (caster.tickCount % 10 == 0)
                level.playSound(null, caster.blockPosition(), SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 1.5f, 0.6f);
        }

        @Override
        public void onCast(Level level, int lvl, LivingEntity caster, CastSource src, MagicData data) {
            IndraArrowEntity arrow = arrow(level, caster);
            if (arrow == null) {   // e.g. cast via command/mob without pre-cast: create and launch immediately
                arrow = new IndraArrowEntity(level, caster);
                arrow.damage = damage(lvl, caster);
                Vec3 pos = caster.getEyePosition().add(caster.getLookAngle().scale(2.4));
                arrow.setPos(pos.x, pos.y, pos.z);
                level.addFreshEntity(arrow);
            }
            arrow.launch();
            caster.getPersistentData().remove(ARROW_TAG);
            super.onCast(level, lvl, caster, src, data);
        }

        @Override
        public void onServerCastComplete(Level level, int lvl, LivingEntity caster, MagicData data, boolean cancelled) {
            if (cancelled) {
                IndraArrowEntity arrow = arrow(level, caster);
                if (arrow != null) {
                    Vfx.burst(level, Vfx.hellfire(), arrow.position(), 30, 0.8, 0.2);
                    level.playSound(null, arrow.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0f, 0.6f);
                    arrow.discard();
                }
                caster.getPersistentData().remove(ARROW_TAG);
            }
            super.onServerCastComplete(level, lvl, caster, data, cancelled);
        }

        @Nullable
        private static IndraArrowEntity arrow(Level level, LivingEntity caster) {
            if (!caster.getPersistentData().contains(ARROW_TAG)) return null;
            Entity e = level.getEntity(caster.getPersistentData().getInt(ARROW_TAG));
            return e instanceof IndraArrowEntity a && a.isAlive() ? a : null;
        }
    }

    // ====================================================================================== PLASMA SCHOOL
    /** Plasma Beam - channelled red 3D beam from the extended hand to wherever the caster looks. */
    @AutoSpellConfig
    public static class PlasmaBeam extends Base {
        private static final String BEAM_TAG = "OA_PlasmaBeam";

        public PlasmaBeam() { super("plasma_beam", PLASMA, SpellRarity.RARE, 8, 12, CastType.CONTINUOUS, 80, 10, 2, 5, 1); }

        float damage(int lvl, LivingEntity c) { return 2.2f + getSpellPower(lvl, c) * 0.3f; }

        @Override public List<MutableComponent> getUniqueInfo(int lvl, LivingEntity c) {
            return List.of(Component.translatable("ui.obsidian_arsenal.damage_per_second", Utils.stringTruncation(damage(lvl, c) * 10, 1)),
                    range((float) PlasmaBeamEntity.RANGE));
        }
        @Override public AnimationHolder getCastStartAnimation() { return ANIM_CHANNEL; }
        @Override public AnimationHolder getCastFinishAnimation() { return AnimationHolder.none(); }

        /** Iron's continuous pulse (every 10 ticks): spawn the beam on the first one, keep it alive afterwards. */
        @Override
        public void onCast(Level level, int lvl, LivingEntity caster, CastSource src, MagicData data) {
            if (level instanceof ServerLevel sl) {
                PlasmaBeamEntity beam = beam(sl, caster);
                if (beam == null) {
                    beam = PlasmaBeamEntity.spawn(sl, caster, damage(lvl, caster));
                    caster.getPersistentData().putInt(BEAM_TAG, beam.getId());
                    sl.playSound(null, caster.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2f, 2.0f);
                    Vfx.effekOn(sl, Effek.PLASMA_BEAM_MUZZLE, caster, new Vec3(-0.33, 1.2, 0.75), 0.6f);
                }
                beam.keepAlive(damage(lvl, caster));
            }
            super.onCast(level, lvl, caster, src, data);
        }

        @Override
        public void onServerCastComplete(Level level, int lvl, LivingEntity caster, MagicData data, boolean cancelled) {
            if (level instanceof ServerLevel sl) {
                PlasmaBeamEntity beam = beam(sl, caster);
                if (beam != null) beam.discard();
                caster.getPersistentData().remove(BEAM_TAG);
                sl.playSound(null, caster.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0f, 2.0f);
            }
            super.onServerCastComplete(level, lvl, caster, data, cancelled);
        }

        @Nullable
        private static PlasmaBeamEntity beam(ServerLevel level, LivingEntity caster) {
            if (!caster.getPersistentData().contains(BEAM_TAG)) return null;
            return level.getEntity(caster.getPersistentData().getInt(BEAM_TAG)) instanceof PlasmaBeamEntity b && b.isAlive() ? b : null;
        }
    }

    /** Black Hole - charge a singularity at your hand for a moment, then fire it; it anchors where it lands. */
    @AutoSpellConfig
    public static class BlackHole extends Base {
        private static final String HOLE_TAG = "OA_BlackHole";

        public BlackHole() { super("black_hole", PLASMA, SpellRarity.COMMON, BlackHoleEntity.MAX_TIER, 45, CastType.LONG, 40, 100, 25, 4, 1); }

        float damage(int lvl, LivingEntity c) { return 3f + getSpellPower(lvl, c) * 0.6f; }

        /** Charges like any Iron's long cast; bigger holes take longer. */
        @Override public int getCastTime(int lvl) { return BlackHoleEntity.chargeTicks(lvl); }

        @Override public List<MutableComponent> getUniqueInfo(int lvl, LivingEntity c) {
            return List.of(dmg(damage(lvl, c)),
                    Component.literal("Width: " + Utils.stringTruncation(BlackHoleEntity.width(lvl), 1) + " blocks"),
                    radius((float) BlackHoleEntity.pullRadius(lvl)),
                    Component.literal("Execute chance: " + Math.round(BlackHoleEntity.executeChance(lvl) * 100) + "%"),
                    length(BlackHoleEntity.duration(lvl)));
        }
        @Override public AnimationHolder getCastStartAnimation() { return ANIM_CHANNEL; }
        @Override public AnimationHolder getCastFinishAnimation() { return ANIM_PUSH; }

        @Nullable
        private static BlackHoleEntity hole(Level level, LivingEntity caster) {
            return caster.getPersistentData().contains(HOLE_TAG) && level.getEntity(caster.getPersistentData().getInt(HOLE_TAG)) instanceof BlackHoleEntity h && h.isAlive() ? h : null;
        }

        @Override
        public void onServerPreCast(Level level, int lvl, LivingEntity caster, @Nullable MagicData data) {
            if (level instanceof ServerLevel sl) {
                BlackHoleEntity old = hole(level, caster);
                if (old != null && old.getState() == BlackHoleEntity.CHARGING) old.discard();
                BlackHoleEntity h = BlackHoleEntity.spawn(sl, caster, lvl, damage(lvl, caster));
                caster.getPersistentData().putInt(HOLE_TAG, h.getId());
                Vfx.effekOn(sl, Effek.BLACK_HOLE_FORM, h, Vec3.ZERO, 0.8f);
            }
            super.onServerPreCast(level, lvl, caster, data);
        }

        @Override
        public void onCast(Level level, int lvl, LivingEntity caster, CastSource src, MagicData data) {
            if (level instanceof ServerLevel sl) {
                BlackHoleEntity h = hole(level, caster);
                if (h == null || h.getState() != BlackHoleEntity.CHARGING)   // e.g. mob / command cast without a pre-cast
                    h = BlackHoleEntity.spawn(sl, caster, lvl, damage(lvl, caster));
                h.launch();
                caster.getPersistentData().remove(HOLE_TAG);
            }
            super.onCast(level, lvl, caster, src, data);
        }

        @Override
        public void onServerCastComplete(Level level, int lvl, LivingEntity caster, MagicData data, boolean cancelled) {
            if (cancelled) {
                BlackHoleEntity h = hole(level, caster);
                if (h != null && h.getState() == BlackHoleEntity.CHARGING) {
                    Vfx.burst(level, Vfx.voidWisp(), h.position(), 30, 0.5, 0.2);
                    level.playSound(null, h.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0f, 1.2f);
                    h.discard();
                }
                caster.getPersistentData().remove(HOLE_TAG);
            }
            super.onServerCastComplete(level, lvl, caster, data, cancelled);
        }
    }

}
