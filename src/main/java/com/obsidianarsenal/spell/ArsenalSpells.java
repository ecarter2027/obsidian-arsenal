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

    // Casting animations: Iron's Spells 'n Spellbooks' own (SpellAnimations), the same ones its built-in spells use.
    public static final AnimationHolder ANIM_SLAM = SpellAnimations.TOUCH_GROUND_ANIMATION;
    public static final AnimationHolder ANIM_THRUST = SpellAnimations.ANIMATION_INSTANT_CAST;
    public static final AnimationHolder ANIM_CHANNEL = SpellAnimations.ANIMATION_CONTINUOUS_CAST;
    public static final AnimationHolder ANIM_BOW_CHARGE = SpellAnimations.BOW_CHARGE_ANIMATION;
    public static final AnimationHolder ANIM_PUSH = SpellAnimations.ONE_HANDED_HORIZONTAL_SWING_ANIMATION;
    // (Iron's finish semantics: none() ends the cast pose, pass() keeps it playing)
    public static final AnimationHolder ANIM_BOW_RELEASE = AnimationHolder.none();
    public static final AnimationHolder ANIM_RAISED = SpellAnimations.CHARGE_RAISED_HAND;
    public static final AnimationHolder ANIM_SELF = SpellAnimations.SELF_CAST_ANIMATION;
    public static final AnimationHolder ANIM_STOMP = SpellAnimations.STOMP;

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

        /** Each spell's own sharp signature particle (tools/gen_fx.py). */
        public static final java.util.Map<String, String> SIGNATURE = java.util.Map.ofEntries(
                java.util.Map.entry("obsidian_spike_explosion", "fx_spike_explosion"), java.util.Map.entry("obsidian_storm", "fx_storm"), java.util.Map.entry("obsidian_tentacles", "fx_tentacles"), java.util.Map.entry("purple_lightning_cloak", "fx_cloak"), java.util.Map.entry("obsidian_shatter", "fx_shatter"), java.util.Map.entry("indras_arrow", "fx_indra"), java.util.Map.entry("obsidian_wall", "fx_wall"), java.util.Map.entry("columns_of_hell", "fx_columns"), java.util.Map.entry("flaming_judgement", "fx_judgement"), java.util.Map.entry("gyroscope", "fx_gyroscope"), java.util.Map.entry("amaterasu", "fx_amaterasu"), java.util.Map.entry("infernal_nova", "fx_nova"), java.util.Map.entry("plasma_beam", "fx_beam"), java.util.Map.entry("black_hole", "fx_black_hole"), java.util.Map.entry("plasma_barrage", "fx_barrage"), java.util.Map.entry("kirin", "fx_kirin"), java.util.Map.entry("obsidian_lance_rain", "fx_lance_rain"), java.util.Map.entry("shard_step", "fx_shard_step"), java.util.Map.entry("glass_prison", "fx_prison"), java.util.Map.entry("kagutsuchi", "fx_kagutsuchi"), java.util.Map.entry("hellbrand", "fx_brand"), java.util.Map.entry("infernal_chains", "fx_chains"), java.util.Map.entry("railgun", "fx_railgun"), java.util.Map.entry("plasma_blink", "fx_blink"), java.util.Map.entry("ion_storm", "fx_ion_storm"));

        /** This spell's signature particle. */
        protected net.minecraft.core.particles.ParticleOptions sig() { return Vfx.fx(SIGNATURE.getOrDefault(id.getPath(), "purple_spark")); }

        @Override
        public void onCast(Level level, int lvl, LivingEntity caster, CastSource src, MagicData data) {
            if (level instanceof ServerLevel sl && !(this instanceof Kirin) && !(this instanceof Railgun))
                Vfx.burst(sl, sig(), caster.position().add(0, caster.getBbHeight() * 0.55, 0), 26, 1.0, 0.45);
            super.onCast(level, lvl, caster, src, data);
        }

        @Override public ResourceLocation getSpellResource() { return id; }
        @Override public DefaultConfig getDefaultConfig() { return config; }
        @Override public CastType getCastType() { return castType; }

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

        @Override public AnimationHolder getCastStartAnimation() { return ANIM_SELF; }
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

        @Override public AnimationHolder getCastStartAnimation() { return ANIM_SLAM; }
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

        @Override public AnimationHolder getCastStartAnimation() { return ANIM_SELF; }
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
            for (int i = 0; i < 4; i++) {                    // arcs crackling down around the caster
                double a = i * Math.PI / 2 + level.random.nextDouble();
                Vec3 g = caster.position().add(Math.cos(a) * 1.6, 0.1, Math.sin(a) * 1.6);
                Vfx.bolt(level, g.add(0, 3.0, 0), g);
            }
            if (level instanceof ServerLevel sl) Vfx.shockwave(sl, Vfx.lightning(), Vfx.purpleSpark(), caster.position(), 3.5, 6);
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

        @Override public AnimationHolder getCastStartAnimation() { return ANIM_SELF; }
        float damage(int lvl, LivingEntity c) { return 4f + getSpellPower(lvl, c) * 0.7f; }

        @Override public List<MutableComponent> getUniqueInfo(int lvl, LivingEntity c) { return List.of(dmg(damage(lvl, c)), length(Kind.COLUMN.lifetime)); }

        @Override
        public void onCast(Level level, int lvl, LivingEntity caster, CastSource src, MagicData data) {
            if (level instanceof ServerLevel sl) {
                for (int i = 0; i < 4; i++) ConstructEntity.spawn(sl, Kind.COLUMN, caster, caster.position(), 0f, damage(lvl, caster), i * 90f);
                Vfx.ring(sl, Vfx.fx("fx_columns"), caster.position(), 3.0, 36, 0.15, 0.1);
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
        @Override public AnimationHolder getCastFinishAnimation() { return AnimationHolder.none(); }   // ends the looping channel pose

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

        @Override public AnimationHolder getCastStartAnimation() { return ANIM_SELF; }
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
                com.obsidianarsenal.network.ModNetwork.cutscene(caster, "eyes", target.position());
                MobEffectInstance inst = new MobEffectInstance(ModEffects.AMATERASU.get(), MobEffectInstance.INFINITE_DURATION, 0, false, true, true);
                inst.setCurativeItems(new ArrayList<>());   // milk / totems can't clear it
                AmaterasuEffect.mark(target, caster.getUUID(), damage(lvl, caster));
                target.addEffect(inst, caster);
                Vec3 mid = target.position().add(0, target.getBbHeight() / 2, 0);
                Vfx.effekOn(sl, Effek.AMATERASU_IGNITE, target, Vec3.ZERO, Math.max(1f, target.getBbHeight() * 0.6f));
                Vfx.burst(sl, Vfx.fx("fx_amaterasu"), mid, 60, 1.0, 0.2);
                Vfx.line(sl, Vfx.amaterasu(), caster.getEyePosition().add(0, -0.3, 0), mid, 0.4, 0.1);
                Vfx.spiral(sl, Vfx.amaterasu(), target.position(), 1.1, target.getBbHeight() + 1.5, 70);   // a pillar of black flame
                com.obsidianarsenal.entity.SpellVisualEntity.flash(sl, 3, mid, 1.6, 16);
                Vfx.spiral(sl, Vfx.voidWisp(), target.position(), 0.8, target.getBbHeight() + 1.0, 30);
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
            if (!level.isClientSide) com.obsidianarsenal.network.ModNetwork.cutscene(caster, "line", caster.getEyePosition().add(caster.getLookAngle().scale(SpellUtil.blockDistance(caster, 48))));
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
        @Override public AnimationHolder getCastFinishAnimation() { return AnimationHolder.none(); }   // ends the looping channel pose

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
            if (!level.isClientSide) com.obsidianarsenal.network.ModNetwork.cutscene(caster, "pullback", caster.getEyePosition().add(caster.getLookAngle().scale(SpellUtil.blockDistance(caster, 48))));
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


    // ====================================================================================== ADDED IN 1.0.12
    /** 13. Obsidian Wall - a line of smooth obsidian spikes bursts up across your path, from the middle outward. */
    @AutoSpellConfig
    public static class ObsidianWall extends Base {
        public ObsidianWall() { super("obsidian_wall", OBSIDIAN, SpellRarity.RARE, 6, 14, CastType.INSTANT, 0, 45, 6, 5, 1); }

        float damage(int lvl, LivingEntity c) { return 3f + getSpellPower(lvl, c) * 0.6f; }

        @Override public List<MutableComponent> getUniqueInfo(int lvl, LivingEntity c) { return List.of(dmg(damage(lvl, c)), range(4)); }
        @Override public AnimationHolder getCastStartAnimation() { return ANIM_STOMP; }

        @Override
        public void onCast(Level level, int lvl, LivingEntity caster, CastSource src, MagicData data) {
            if (level instanceof ServerLevel sl) {
                Vec3 look = caster.getLookAngle();
                Vec3 fwd = new Vec3(look.x, 0, look.z);
                if (fwd.lengthSqr() < 1e-4) fwd = Vec3.directionFromRotation(0, caster.getYRot());
                fwd = fwd.normalize();
                Vec3 side = new Vec3(-fwd.z, 0, fwd.x);
                Vec3 mid = caster.position().add(fwd.scale(4.0));
                float dmg = damage(lvl, caster);
                int half = 3 + lvl / 3;
                for (int i = -half; i <= half; i++) {
                    final Vec3 at = mid.add(side.scale(i * 1.4));
                    Scheduler.later(sl, Math.abs(i) * 2, () -> {
                        double y = SpellUtil.groundY(sl, at.x, caster.getY() + 1, at.z);
                        Vec3 p = new Vec3(at.x, y, at.z);
                        ConstructEntity.spawn(sl, Kind.SPIKE, caster, p, SpellUtil.randomCardinal(sl.random), dmg, 0);
                        Vfx.burst(sl, Vfx.obsidianChunk(), p.add(0, 0.3, 0), 12, 0.7, 0.3);
                        Vfx.ring(sl, Vfx.fx("fx_wall"), p, 0.9, 8, 0.12, 0.05);
                    });
                }
                sl.playSound(null, caster.blockPosition(), SoundEvents.DEEPSLATE_BREAK, SoundSource.PLAYERS, 1.4f, 0.5f);
            }
            super.onCast(level, lvl, caster, src, data);
        }
    }

    /** 14. Infernal Nova - an expanding ring of hellfire bursts out from the caster, burning everything it passes. */
    @AutoSpellConfig
    public static class InfernalNova extends Base {
        public InfernalNova() { super("infernal_nova", HELLFIRE, SpellRarity.EPIC, 6, 16, CastType.INSTANT, 0, 55, 8, 6, 1); }

        float damage(int lvl, LivingEntity c) { return 4f + getSpellPower(lvl, c) * 0.8f; }
        double novaRadius(int lvl) { return 7 + lvl; }

        @Override public List<MutableComponent> getUniqueInfo(int lvl, LivingEntity c) { return List.of(aoe(damage(lvl, c)), radius((float) novaRadius(lvl))); }
        @Override public AnimationHolder getCastStartAnimation() { return ANIM_STOMP; }

        @Override
        public void onCast(Level level, int lvl, LivingEntity caster, CastSource src, MagicData data) {
            if (level instanceof ServerLevel sl) {
                Vec3 c = caster.position();
                double max = novaRadius(lvl);
                com.obsidianarsenal.entity.SpellVisualEntity.circle(sl, 1, c, max, 18, 0);
                float dmg = damage(lvl, caster);
                java.util.Set<java.util.UUID> hit = new java.util.HashSet<>();
                int steps = 10;
                for (int i = 1; i <= steps; i++) {
                    final double r = max * i / steps;
                    Scheduler.later(sl, i, () -> {
                        Vfx.ring(sl, Vfx.fx("fx_nova"), c, r, (int) (14 + r * 4), 0.05, 0.12);
                        Vfx.ring(sl, Vfx.redSpark(), c, r, (int) (10 + r * 4), 0.15, 0.25);
                        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(c, c).inflate(r, 2.5, r),
                                t -> SpellUtil.canHarm(caster, t))) {
                            double d = Math.hypot(e.getX() - c.x, e.getZ() - c.z);
                            if (d > r - max / steps - 0.5 && d <= r + 0.5 && hit.add(e.getUUID())) {
                                SpellUtil.spellDamage(this, caster, caster, e, dmg);
                                Vec3 push = new Vec3(e.getX() - c.x, 0, e.getZ() - c.z);
                                if (push.lengthSqr() > 1e-4) push = push.normalize().scale(0.8);
                                e.push(push.x, 0.35, push.z);
                                e.hurtMarked = true;
                            }
                        }
                    });
                }
                Vfx.burst(sl, Vfx.fx("fx_nova"), c.add(0, 1, 0), 40, 1.2, 0.5);
                com.obsidianarsenal.entity.SpellVisualEntity.flash(sl, 1, c.add(0, 1, 0), 2.2, 12);
                sl.playSound(null, caster.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.4f, 0.4f);
                sl.playSound(null, caster.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.8f, 0.7f);
            }
            super.onCast(level, lvl, caster, src, data);
        }
    }

    /** 15. Plasma Barrage - a rapid fan of plasma bolts that seek out enemies in front of you. */
    @AutoSpellConfig
    public static class PlasmaBarrage extends Base {
        public static final double RANGE = 20.0;

        public PlasmaBarrage() { super("plasma_barrage", PLASMA, SpellRarity.RARE, 8, 10, CastType.INSTANT, 0, 40, 6, 5, 1); }

        float damage(int lvl, LivingEntity c) { return 2f + getSpellPower(lvl, c) * 0.45f; }
        int bolts(int lvl) { return 5 + lvl / 2; }

        @Override public List<MutableComponent> getUniqueInfo(int lvl, LivingEntity c) { return List.of(dmg(damage(lvl, c)), range((float) RANGE)); }
        @Override public AnimationHolder getCastStartAnimation() { return ANIM_THRUST; }

        @Override
        public void onCast(Level level, int lvl, LivingEntity caster, CastSource src, MagicData data) {
            if (level instanceof ServerLevel sl) {
                Vec3 look = caster.getLookAngle();
                List<LivingEntity> targets = sl.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(RANGE),
                        t -> SpellUtil.canHarm(caster, t) && t.distanceTo(caster) <= RANGE && caster.hasLineOfSight(t)
                                && t.position().subtract(caster.position()).normalize().dot(look) > 0.35);
                targets.sort(java.util.Comparator.comparingDouble(t -> t.distanceToSqr(caster)));
                float dmg = damage(lvl, caster);
                int n = bolts(lvl);
                for (int i = 0; i < n; i++) {
                    final int k = i;
                    Scheduler.later(sl, i * 2, () -> {
                        Vec3 from = caster.getEyePosition().add(0, -0.3, 0);
                        Vec3 to;
                        LivingEntity t = targets.isEmpty() ? null : targets.get(k % targets.size());
                        if (t != null && t.isAlive()) {
                            to = t.position().add(0, t.getBbHeight() * 0.6, 0);
                            SpellUtil.spellDamage(this, caster, caster, t, dmg);
                        } else {                              // nothing to seek: a fan straight ahead
                            double yaw = Math.toRadians(caster.getYRot() + (k - (n - 1) / 2.0) * 9);
                            Vec3 dir = new Vec3(-Math.sin(yaw), look.y, Math.cos(yaw)).normalize();
                            to = from.add(dir.scale(SpellUtil.blockDistance(caster, RANGE)));
                        }
                        Vfx.line(sl, Vfx.fx("fx_barrage"), from, to, 0.3, 0.05);
                        Vfx.burst(sl, Vfx.fx("fx_barrage"), to, 14, 0.4, 0.35);
                        Vfx.burst(sl, Vfx.redSpark(), to, 6, 0.3, 0.15);
                        sl.playSound(null, to.x, to.y, to.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.7f, 1.6f);
                    });
                }
                sl.playSound(null, caster.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0f, 1.8f);
            }
            super.onCast(level, lvl, caster, src, data);
        }
    }


    // ====================================================================================== ADDED IN 1.0.13
    /** Where the caster is looking: the block hit within {@code range} (or that far ahead), dropped onto the ground. */
    static Vec3 aimPoint(LivingEntity caster, double range) {
        Vec3 p = caster.getEyePosition().add(caster.getLookAngle().scale(SpellUtil.blockDistance(caster, range)));
        return new Vec3(p.x, SpellUtil.groundY(caster.level(), p.x, p.y + 1, p.z), p.z);
    }

    static List<LivingEntity> foesNear(ServerLevel sl, LivingEntity caster, Vec3 c, double r) {
        return sl.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(c, c).inflate(r), t -> SpellUtil.canHarm(caster, t)
                && t.position().distanceTo(c) <= r + t.getBbWidth());
    }

    /**
     * 16. Kirin (lore: the lightning dragon called down from a storm cloud). A long cast gathers a black storm cloud
     * over the target point; then a dragon of purple lightning plunges from it, coiling as it falls, and the strike
     * blasts everything within 6 blocks.
     */
    @AutoSpellConfig
    public static class Kirin extends Base {
        public Kirin() { super("kirin", OBSIDIAN, SpellRarity.LEGENDARY, 5, 45, CastType.LONG, 40, 90, 15, 10, 2); }

        float damage(int lvl, LivingEntity c) { return 12f + getSpellPower(lvl, c) * 1.6f; }

        @Override public List<MutableComponent> getUniqueInfo(int lvl, LivingEntity c) { return List.of(aoe(damage(lvl, c)), radius(6), range(40)); }
        @Override public AnimationHolder getCastStartAnimation() { return ANIM_RAISED; }

        @Override
        public void onCast(Level level, int lvl, LivingEntity caster, CastSource src, MagicData data) {
            if (level instanceof ServerLevel sl) {
                Vec3 g = aimPoint(caster, 40);
                com.obsidianarsenal.network.ModNetwork.cutscene(caster, "sky", g);
                com.obsidianarsenal.entity.SpellVisualEntity.spawn(sl, com.obsidianarsenal.entity.SpellVisualEntity.KIRIN, g, (float) (sl.random.nextDouble() * Math.PI * 2));
                float dmg = damage(lvl, caster);
                sl.playSound(null, g.x, g.y + 18, g.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 4.0f, 0.5f);
                Scheduler.later(sl, 10, () -> sl.playSound(null, g.x, g.y + 10, g.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 2.5f, 1.3f));
                Scheduler.later(sl, com.obsidianarsenal.entity.SpellVisualEntity.KIRIN_STRIKE, () -> {
                    for (LivingEntity e : foesNear(sl, caster, g, 6)) SpellUtil.spellDamage(this, caster, caster, e, dmg);
                    com.obsidianarsenal.entity.SpellVisualEntity.flash(sl, 0, g.add(0, 1.5, 0), 4.0, 16);
                    sl.playSound(null, g.x, g.y, g.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 4.0f, 0.7f);
                    sl.playSound(null, g.x, g.y, g.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.0f, 0.6f);
                });
            }
            super.onCast(level, lvl, caster, src, data);
        }
    }

    /** 17. Obsidian Lance Rain - obsidian lances rain down on the area you look at, a few leaving crystal clusters. */
    @AutoSpellConfig
    public static class ObsidianLanceRain extends Base {
        public ObsidianLanceRain() { super("obsidian_lance_rain", OBSIDIAN, SpellRarity.EPIC, 6, 20, CastType.INSTANT, 0, 60, 8, 6, 1); }

        float damage(int lvl, LivingEntity c) { return 3f + getSpellPower(lvl, c) * 0.5f; }
        int lances(int lvl) { return 14 + lvl * 2; }

        @Override public List<MutableComponent> getUniqueInfo(int lvl, LivingEntity c) { return List.of(dmg(damage(lvl, c)), radius(4.5f), range(30)); }
        @Override public AnimationHolder getCastStartAnimation() { return ANIM_PUSH; }

        @Override
        public void onCast(Level level, int lvl, LivingEntity caster, CastSource src, MagicData data) {
            if (level instanceof ServerLevel sl) {
                Vec3 c = aimPoint(caster, 30);
                com.obsidianarsenal.entity.SpellVisualEntity.circle(sl, 0, c, 5, 2 + lances(lvl) * 2 + 10, 13);
                com.obsidianarsenal.entity.SpellVisualEntity.circle(sl, 0, c, 4.5, 2 + lances(lvl) * 2 + 10, 0);
                float dmg = damage(lvl, caster);
                int n = lances(lvl);
                for (int i = 0; i < n; i++) {
                    final int k = i;
                    Scheduler.later(sl, 2 + i * 2, () -> {
                        double a = sl.random.nextDouble() * Math.PI * 2, d = Math.sqrt(sl.random.nextDouble()) * 4.5;
                        double x = c.x + Math.cos(a) * d, z = c.z + Math.sin(a) * d;
                        Vec3 hit = new Vec3(x, SpellUtil.groundY(sl, x, c.y + 3, z), z);
                        Vfx.line(sl, Vfx.fx("fx_lance_rain"), hit.add(0.6, 12, 0.3), hit, 0.5, 0.05);
                        for (int j = 0; j < 6; j++) Vfx.spawn(sl, Vfx.fx("fx_lance_rain"), hit.x, hit.y + 2 + j, hit.z, 0, -1.6, 0);
                        Vfx.burst(sl, Vfx.obsidianChunk(), hit.add(0, 0.2, 0), 8, 0.5, 0.3);
                        Vfx.burst(sl, Vfx.fx("fx_spike_explosion"), hit.add(0, 0.2, 0), 6, 0.3, 0.4);
                        for (LivingEntity e : foesNear(sl, caster, hit, 1.6)) SpellUtil.spellDamage(this, caster, caster, e, dmg);
                        if (k % 5 == 0) ConstructEntity.spawn(sl, Kind.SHARD, caster, hit, SpellUtil.randomCardinal(sl.random), dmg * 0.5f, 0);
                        sl.playSound(null, hit.x, hit.y, hit.z, SoundEvents.TRIDENT_HIT_GROUND, SoundSource.PLAYERS, 0.8f, 0.6f + sl.random.nextFloat() * 0.3f);
                    });
                }
            }
            super.onCast(level, lvl, caster, src, data);
        }
    }

    /** 18. Shard Step - flash forward up to 9 blocks in a streak of obsidian needles, cutting everything you pass. */
    @AutoSpellConfig
    public static class ShardStep extends Base {
        public ShardStep() { super("shard_step", OBSIDIAN, SpellRarity.RARE, 6, 6, CastType.INSTANT, 0, 25, 4, 4, 1); }

        float damage(int lvl, LivingEntity c) { return 3f + getSpellPower(lvl, c) * 0.6f; }

        @Override public List<MutableComponent> getUniqueInfo(int lvl, LivingEntity c) { return List.of(dmg(damage(lvl, c)), range(9)); }
        @Override public AnimationHolder getCastStartAnimation() { return ANIM_THRUST; }

        @Override
        public void onCast(Level level, int lvl, LivingEntity caster, CastSource src, MagicData data) {
            if (level instanceof ServerLevel sl) {
                Vec3 look = caster.getLookAngle();
                Vec3 dir = new Vec3(look.x, 0, look.z);
                if (dir.lengthSqr() < 1e-4) dir = Vec3.directionFromRotation(0, caster.getYRot());
                dir = dir.normalize();
                Vec3 from = caster.position();
                double dist = 9;
                for (double t = 0.5; t <= 9; t += 0.5) {            // stop short of walls
                    Vec3 p = from.add(dir.scale(t));
                    if (!sl.noCollision(caster, caster.getBoundingBox().move(p.subtract(from)))) { dist = t - 0.5; break; }
                }
                Vec3 to = from.add(dir.scale(dist));
                float dmg = damage(lvl, caster);
                net.minecraft.world.phys.AABB sweep = caster.getBoundingBox().expandTowards(to.subtract(from)).inflate(0.8);
                for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, sweep, t -> SpellUtil.canHarm(caster, t)))
                    SpellUtil.spellDamage(this, caster, caster, e, dmg);
                Vec3 d = dir;
                for (int i = 0; i < 40; i++) {
                    double t = sl.random.nextDouble() * dist;
                    Vec3 p = from.add(d.scale(t)).add(0, 0.3 + sl.random.nextDouble() * 1.5, 0);
                    Vfx.spawn(sl, Vfx.fx("fx_shard_step"), p.x, p.y, p.z, d.x * 1.2, 0, d.z * 1.2);
                }
                Vfx.line(sl, Vfx.fx("fx_shatter"), from.add(0, 1, 0), to.add(0, 1, 0), 0.4, 0.4);
                caster.teleportTo(to.x, to.y, to.z);
                caster.resetFallDistance();
                Vfx.burst(sl, Vfx.fx("fx_shard_step"), to.add(0, 1, 0), 16, 0.6, 0.5);
                com.obsidianarsenal.entity.SpellVisualEntity.flash(sl, 0, to.add(0, 1, 0), 1.3, 10);
                sl.playSound(null, to.x, to.y, to.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.2f, 1.4f);
            }
            super.onCast(level, lvl, caster, src, data);
        }
    }

    /** 19. Glass Prison - obsidian spikes snap shut around the creature you look at, pinning it in place. */
    @AutoSpellConfig
    public static class GlassPrison extends Base {
        public static final double RANGE = 25;

        public GlassPrison() { super("glass_prison", OBSIDIAN, SpellRarity.RARE, 5, 20, CastType.INSTANT, 0, 45, 6, 3, 1); }

        int hold(int lvl) { return 40 + lvl * 12; }
        float damage(int lvl, LivingEntity c) { return 2f + getSpellPower(lvl, c) * 0.4f; }

        @Override public List<MutableComponent> getUniqueInfo(int lvl, LivingEntity c) { return List.of(dmg(damage(lvl, c)), length(hold(lvl)), range((float) RANGE)); }
        @Override public AnimationHolder getCastStartAnimation() { return ANIM_THRUST; }

        @Override
        public boolean checkPreCastConditions(Level level, int lvl, LivingEntity entity, MagicData data) {
            boolean has = SpellUtil.raycastLiving(entity, RANGE) != null;
            if (!has && entity instanceof ServerPlayer sp) sp.displayClientMessage(Component.translatable("ui.irons_spellbooks.cast_error_target"), true);
            return has;
        }

        @Override
        public void onCast(Level level, int lvl, LivingEntity caster, CastSource src, MagicData data) {
            LivingEntity target = SpellUtil.raycastLiving(caster, RANGE);
            if (target != null && level instanceof ServerLevel sl) {
                Vec3 c = target.position();
                float dmg = damage(lvl, caster);
                com.obsidianarsenal.entity.SpellVisualEntity.circle(sl, 0, c, 2.2, hold(lvl), 0);
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    Vec3 p = c.add(Math.cos(a) * 1.4, 0, Math.sin(a) * 1.4);
                    Scheduler.later(sl, i % 2, () -> ConstructEntity.spawn(sl, Kind.SPIKE, caster, new Vec3(p.x, SpellUtil.groundY(sl, p.x, c.y + 1, p.z), p.z),
                            SpellUtil.yawFacing(c.x - p.x, c.z - p.z), dmg, 0));
                }
                target.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, hold(lvl), 10, false, false, true));
                target.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS, hold(lvl), 2, false, false, true));
                for (int i = 0; i < hold(lvl); i += 8)
                    Scheduler.later(sl, i, () -> { if (target.isAlive()) Vfx.burst(sl, Vfx.fx("fx_prison"), target.position().add(0, target.getBbHeight() / 2, 0), 5, 1.0, 0.05); });
                SpellUtil.spellDamage(this, caster, caster, target, dmg);
                sl.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5f, 0.5f);
            }
            super.onCast(level, lvl, caster, src, data);
        }
    }

    /**
     * 20. Kagutsuchi (lore: Amaterasu's black flames shaped by will into a blade). A sweeping arc of black-flame blades
     * cuts everything in front of you; the flames cling to whatever they cut and wither it.
     */
    @AutoSpellConfig
    public static class Kagutsuchi extends Base {
        public Kagutsuchi() { super("kagutsuchi", HELLFIRE, SpellRarity.LEGENDARY, 5, 16, CastType.INSTANT, 0, 70, 10, 8, 1); }

        float damage(int lvl, LivingEntity c) { return 7f + getSpellPower(lvl, c) * 1.0f; }

        @Override public List<MutableComponent> getUniqueInfo(int lvl, LivingEntity c) { return List.of(dmg(damage(lvl, c)), range(6)); }
        @Override public AnimationHolder getCastStartAnimation() { return ANIM_PUSH; }

        @Override
        public void onCast(Level level, int lvl, LivingEntity caster, CastSource src, MagicData data) {
            if (level instanceof ServerLevel sl) {
                float yaw = caster.getYRot();
                Vec3 c = caster.position().add(0, 1.1, 0);
                com.obsidianarsenal.network.ModNetwork.cutscene(caster, "orbit", caster.position());
                float dmg = damage(lvl, caster);
                for (int k = 0; k < 7; k++) {                    // the blade sweeps from right to left over 7 ticks
                    final int step = k;
                    Scheduler.later(sl, k, () -> {
                        double a = Math.toRadians(yaw + 75 - step * 25);
                        Vec3 dir = new Vec3(-Math.sin(a), 0, Math.cos(a));
                        for (double r = 1.2; r <= 6; r += 0.45) {
                            Vec3 p = c.add(dir.scale(r)).add(0, (sl.random.nextDouble() - 0.5) * 0.5 - (r - 3) * 0.08, 0);
                            Vec3 tangent = new Vec3(dir.z, 0, -dir.x);
                            Vfx.spawn(sl, Vfx.fx("fx_kagutsuchi"), p.x, p.y, p.z, tangent.x * 0.6, 0.02, tangent.z * 0.6);
                            if (sl.random.nextInt(3) == 0) Vfx.spawn(sl, Vfx.fx("fx_amaterasu"), p.x, p.y, p.z, 0, 0.05, 0);
                        }
                    });
                }
                Vec3 look = Vec3.directionFromRotation(0, yaw);
                for (LivingEntity e : foesNear(sl, caster, caster.position(), 6.5)) {
                    Vec3 to = e.position().subtract(caster.position());
                    if (to.horizontalDistanceSqr() > 1e-4 && new Vec3(to.x, 0, to.z).normalize().dot(look) > -0.2) {
                        SpellUtil.spellDamage(this, caster, caster, e, dmg);
                        e.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.WITHER, 100, 1), caster);
                        Vfx.spiral(sl, Vfx.fx("fx_amaterasu"), e.position(), 0.8, e.getBbHeight() + 0.5, 24);
                    }
                }
                sl.playSound(null, caster.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.5f, 0.5f);
                sl.playSound(null, caster.blockPosition(), SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 0.7f, 0.6f);
            }
            super.onCast(level, lvl, caster, src, data);
        }
    }

    /** 21. Hellbrand - burn a crimson sigil onto the creature you look at; after a moment it detonates. */
    @AutoSpellConfig
    public static class Hellbrand extends Base {
        public static final double RANGE = 30;

        public Hellbrand() { super("hellbrand", HELLFIRE, SpellRarity.RARE, 8, 12, CastType.INSTANT, 0, 40, 6, 6, 1); }

        float damage(int lvl, LivingEntity c) { return 6f + getSpellPower(lvl, c) * 1.2f; }

        @Override public List<MutableComponent> getUniqueInfo(int lvl, LivingEntity c) { return List.of(aoe(damage(lvl, c)), radius(3), range((float) RANGE)); }
        @Override public AnimationHolder getCastStartAnimation() { return ANIM_THRUST; }

        @Override
        public boolean checkPreCastConditions(Level level, int lvl, LivingEntity entity, MagicData data) {
            boolean has = SpellUtil.raycastLiving(entity, RANGE) != null;
            if (!has && entity instanceof ServerPlayer sp) sp.displayClientMessage(Component.translatable("ui.irons_spellbooks.cast_error_target"), true);
            return has;
        }

        @Override
        public void onCast(Level level, int lvl, LivingEntity caster, CastSource src, MagicData data) {
            LivingEntity target = SpellUtil.raycastLiving(caster, RANGE);
            if (target != null && level instanceof ServerLevel sl) {
                float dmg = damage(lvl, caster);
                Vfx.line(sl, Vfx.fx("fx_brand"), caster.getEyePosition().add(0, -0.3, 0), target.position().add(0, target.getBbHeight() * 0.6, 0), 0.6, 0.05);
                com.obsidianarsenal.entity.SpellVisualEntity.circle(sl, 1, target.position(), 1.6, 52, 0);
                for (int i = 0; i < 50; i += 5)
                    Scheduler.later(sl, i, () -> { if (target.isAlive()) Vfx.burst(sl, Vfx.fx("fx_brand"), target.position().add(0, target.getBbHeight() * 0.6, 0), 4, 0.5, 0.05); });
                Scheduler.later(sl, 50, () -> {
                    Vec3 c = target.position().add(0, target.getBbHeight() * 0.5, 0);
                    com.obsidianarsenal.entity.SpellVisualEntity.flash(sl, 1, c, 2.6, 14);
                    for (LivingEntity e : foesNear(sl, caster, c, 3)) SpellUtil.spellDamage(this, caster, caster, e, dmg);
                    Vfx.burst(sl, Vfx.fx("fx_brand"), c, 30, 1.0, 0.6);
                    Vfx.burst(sl, Vfx.redSpark(), c, 50, 1.2, 0.9);
                    Vfx.cracks(sl, Vfx.fx("fx_columns"), target.position(), 3.5, 6);
                    sl.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.2f, 1.3f);
                });
                sl.playSound(null, target.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.2f, 0.5f);
            }
            super.onCast(level, lvl, caster, src, data);
        }
    }

    /** 22. Infernal Chains - crimson chains lash out to up to five enemies in front of you and drag them in. */
    @AutoSpellConfig
    public static class InfernalChains extends Base {
        public InfernalChains() { super("infernal_chains", HELLFIRE, SpellRarity.EPIC, 6, 16, CastType.INSTANT, 0, 50, 8, 4, 1); }

        float damage(int lvl, LivingEntity c) { return 3f + getSpellPower(lvl, c) * 0.5f; }

        @Override public List<MutableComponent> getUniqueInfo(int lvl, LivingEntity c) { return List.of(dmg(damage(lvl, c)), range(16)); }
        @Override public AnimationHolder getCastStartAnimation() { return ANIM_PUSH; }

        @Override
        public void onCast(Level level, int lvl, LivingEntity caster, CastSource src, MagicData data) {
            if (level instanceof ServerLevel sl) {
                Vec3 look = caster.getLookAngle();
                List<LivingEntity> foes = new ArrayList<>(foesNear(sl, caster, caster.position(), 16));
                foes.removeIf(t -> t.position().subtract(caster.position()).normalize().dot(look) < 0.2 || !caster.hasLineOfSight(t));
                foes.sort(java.util.Comparator.comparingDouble(t -> t.distanceToSqr(caster)));
                float dmg = damage(lvl, caster);
                for (LivingEntity t : foes.subList(0, Math.min(5, foes.size()))) {
                    Vec3 hand = caster.getEyePosition().add(0, -0.4, 0);
                    Vfx.line(sl, Vfx.fx("fx_chains"), hand, t.position().add(0, t.getBbHeight() * 0.6, 0), 0.35, 0.0);
                    SpellUtil.spellDamage(this, caster, caster, t, dmg);
                    Vec3 pull = caster.position().subtract(t.position());
                    double d = pull.length();
                    if (d > 1.5) {
                        pull = pull.normalize().scale(Math.min(2.2, 0.35 + d * 0.16));
                        t.setDeltaMovement(pull.x, 0.35, pull.z);
                        t.hurtMarked = true;
                    }
                    t.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
                    Scheduler.later(sl, 3, () -> Vfx.line(sl, Vfx.fx("fx_chains"), caster.getEyePosition().add(0, -0.4, 0), t.position().add(0, t.getBbHeight() * 0.6, 0), 0.35, 0.0));
                }
                sl.playSound(null, caster.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.5f, 0.5f);
                sl.playSound(null, caster.blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 1.2f, 0.7f);
            }
            super.onCast(level, lvl, caster, src, data);
        }
    }

    /**
     * 23. Railgun - an orbital strike. Mark a spot up to 64 blocks away: a targeting reticle locks on, an orbital
     * cannon slides into place far overhead, unfolds and charges, then fires a beam straight down that blasts
     * everything within 7 blocks, keeps burning for a second and a half, and craters the ground (with mobGriefing).
     * All of it is a 3D set piece (SpellVisualEntity ORBITAL), with its own cutscene.
     */
    @AutoSpellConfig
    public static class Railgun extends Base {
        public static final double RANGE = 64;

        public Railgun() { super("railgun", PLASMA, SpellRarity.LEGENDARY, 5, 40, CastType.LONG, 30, 90, 15, 12, 2); }

        float damage(int lvl, LivingEntity c) { return 16f + getSpellPower(lvl, c) * 2.0f; }

        @Override public List<MutableComponent> getUniqueInfo(int lvl, LivingEntity c) { return List.of(aoe(damage(lvl, c)), radius(12), range((float) RANGE)); }
        @Override public AnimationHolder getCastStartAnimation() { return ANIM_RAISED; }

        @Override
        public void onCast(Level level, int lvl, LivingEntity caster, CastSource src, MagicData data) {
            if (level instanceof ServerLevel sl) {
                Vec3 g = aimPoint(caster, RANGE);
                com.obsidianarsenal.network.ModNetwork.cutscene(caster, "orbital", g);
                com.obsidianarsenal.entity.SpellVisualEntity.spawn(sl, com.obsidianarsenal.entity.SpellVisualEntity.ORBITAL, g, (float) Math.toRadians(caster.getYRot()));
                float dmg = damage(lvl, caster);
                int fire = com.obsidianarsenal.entity.SpellVisualEntity.ORBITAL_FIRE;
                sl.playSound(null, g.x, g.y, g.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2.0f, 0.6f);
                Scheduler.later(sl, 18, () -> sl.playSound(null, g.x, g.y + 20, g.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 3.0f, 0.5f));
                Scheduler.later(sl, 30, () -> sl.playSound(null, g.x, g.y + 30, g.z, SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 3.0f, 0.5f));
                Scheduler.later(sl, 42, () -> sl.playSound(null, g.x, g.y + 20, g.z, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 3.0f, 0.5f));
                Scheduler.later(sl, 58, () -> sl.playSound(null, g.x, g.y + 20, g.z, SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 4.0f, 2.0f));
                Scheduler.later(sl, fire, () -> {
                    sl.playSound(null, g.x, g.y, g.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 6.0f, 0.5f);
                    sl.playSound(null, g.x, g.y, g.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 6.0f, 0.7f);
                    sl.playSound(null, g.x, g.y, g.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 4.0f, 0.5f);
                    for (LivingEntity e : foesNear(sl, caster, g, 12)) {
                        double d = Math.hypot(e.getX() - g.x, e.getZ() - g.z);
                        SpellUtil.spellDamage(this, caster, caster, e, dmg * (float) (1 - 0.55 * Math.min(1, d / 12)));
                        Vec3 push = new Vec3(e.getX() - g.x, 0, e.getZ() - g.z);
                        push = push.lengthSqr() < 1e-4 ? Vec3.ZERO : push.normalize().scale(2.2 * (1 - 0.5 * Math.min(1, d / 12)));
                        e.setDeltaMovement(push.x, 1.4, push.z);
                        e.hurtMarked = true;
                    }
                    SpellUtil.launchDebris(sl, g, 12, 70);     // real blocks thrown out of the blast
                    SpellUtil.crater(sl, g, 10);
                    Scheduler.later(sl, 8, () -> SpellUtil.launchDebris(sl, g, 15, 40));
                });
                for (int k = fire + 5; k < fire + 32; k += 5)          // the beam keeps burning where it stands
                    Scheduler.later(sl, k, () -> {
                        for (LivingEntity e : foesNear(sl, caster, g, 3.5)) SpellUtil.spellDamage(this, caster, caster, e, dmg * 0.15f);
                    });
            }
            super.onCast(level, lvl, caster, src, data);
        }
    }

    /** 24. Plasma Blink - vanish in a burst of plasma and reappear up to 16 blocks where you look, shocking whatever is there. */
    @AutoSpellConfig
    public static class PlasmaBlink extends Base {
        public PlasmaBlink() { super("plasma_blink", PLASMA, SpellRarity.UNCOMMON, 6, 5, CastType.INSTANT, 0, 25, 4, 3, 1); }

        float damage(int lvl, LivingEntity c) { return 2f + getSpellPower(lvl, c) * 0.4f; }

        @Override public List<MutableComponent> getUniqueInfo(int lvl, LivingEntity c) { return List.of(aoe(damage(lvl, c)), range(16)); }

        @Override
        public void onCast(Level level, int lvl, LivingEntity caster, CastSource src, MagicData data) {
            if (level instanceof ServerLevel sl) {
                Vec3 from = caster.position();
                double dist = Math.max(0, SpellUtil.blockDistance(caster, 16) - 0.8);
                Vec3 p = caster.getEyePosition().add(caster.getLookAngle().scale(dist));
                Vec3 to = new Vec3(p.x, SpellUtil.groundY(sl, p.x, p.y + 0.5, p.z), p.z);
                Vfx.burst(sl, Vfx.fx("fx_blink"), from.add(0, 1, 0), 30, 0.6, 0.5);
                Vfx.line(sl, Vfx.fx("fx_beam"), from.add(0, 1, 0), to.add(0, 1, 0), 0.6, 0.3);
                caster.teleportTo(to.x, to.y, to.z);
                caster.resetFallDistance();
                float dmg = damage(lvl, caster);
                for (LivingEntity e : foesNear(sl, caster, to, 2.5)) SpellUtil.spellDamage(this, caster, caster, e, dmg);
                Vfx.burst(sl, Vfx.fx("fx_blink"), to.add(0, 1, 0), 30, 0.6, 0.6);
                com.obsidianarsenal.entity.SpellVisualEntity.flash(sl, 2, from.add(0, 1, 0), 1.4, 10);
                com.obsidianarsenal.entity.SpellVisualEntity.flash(sl, 2, to.add(0, 1, 0), 1.8, 12);
                Vfx.ring(sl, Vfx.fx("fx_beam"), to, 2.5, 18, 0.2, 0.02);
                sl.playSound(null, to.x, to.y, to.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.6f);
            }
            super.onCast(level, lvl, caster, src, data);
        }
    }

    /** 25. Ion Storm - for four seconds plasma arcs hammer down on the area you look at, seeking enemies inside it. */
    @AutoSpellConfig
    public static class IonStorm extends Base {
        public IonStorm() { super("ion_storm", PLASMA, SpellRarity.EPIC, 6, 24, CastType.INSTANT, 0, 65, 9, 4, 1); }

        float damage(int lvl, LivingEntity c) { return 2.5f + getSpellPower(lvl, c) * 0.45f; }

        @Override public List<MutableComponent> getUniqueInfo(int lvl, LivingEntity c) { return List.of(dmg(damage(lvl, c)), radius(6), length(80)); }
        @Override public AnimationHolder getCastStartAnimation() { return ANIM_SELF; }

        @Override
        public void onCast(Level level, int lvl, LivingEntity caster, CastSource src, MagicData data) {
            if (level instanceof ServerLevel sl) {
                Vec3 c = aimPoint(caster, 32);
                com.obsidianarsenal.entity.SpellVisualEntity.circle(sl, 2, c, 6.5, 84, 0);
                com.obsidianarsenal.entity.SpellVisualEntity.circle(sl, 2, c, 5.5, 84, 9);
                float dmg = damage(lvl, caster);
                for (int i = 0; i < 80; i += 4) {
                    Scheduler.later(sl, i, () -> {
                        List<LivingEntity> foes = foesNear(sl, caster, c, 6);
                        Vec3 hit;
                        if (!foes.isEmpty() && sl.random.nextInt(3) > 0) {
                            LivingEntity t = foes.get(sl.random.nextInt(foes.size()));
                            hit = t.position();
                            SpellUtil.spellDamage(this, caster, caster, t, dmg);
                        } else {
                            double a = sl.random.nextDouble() * Math.PI * 2, d = Math.sqrt(sl.random.nextDouble()) * 6;
                            hit = new Vec3(c.x + Math.cos(a) * d, c.y, c.z + Math.sin(a) * d);
                            hit = new Vec3(hit.x, SpellUtil.groundY(sl, hit.x, c.y + 3, hit.z), hit.z);
                        }
                        Vec3 top = hit.add((sl.random.nextDouble() - 0.5) * 2, 9, (sl.random.nextDouble() - 0.5) * 2);
                        Vec3 prev = top;
                        for (int s = 1; s <= 6; s++) {             // a jagged arc down to the hit
                            Vec3 q = top.lerp(hit, s / 6.0).add(s < 6 ? (sl.random.nextDouble() - 0.5) * 1.2 : 0, 0, s < 6 ? (sl.random.nextDouble() - 0.5) * 1.2 : 0);
                            Vfx.line(sl, Vfx.fx("fx_ion_storm"), prev, q, 0.25, 0.05);
                            prev = q;
                        }
                        Vfx.burst(sl, Vfx.fx("fx_blink"), hit.add(0, 0.3, 0), 10, 0.3, 0.45);
                        com.obsidianarsenal.entity.SpellVisualEntity.flash(sl, 2, hit.add(0, 0.5, 0), 1.1, 8);
                        sl.playSound(null, hit.x, hit.y, hit.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.5f, 1.8f);
                    });
                }
                for (int i = 0; i < 80; i += 10)                  // the storm's charged ceiling
                    Scheduler.later(sl, i, () -> Vfx.burst(sl, Vfx.fx("fx_ion_storm"), c.add(0, 9, 0), 16, 5.0, 0.05));
            }
            super.onCast(level, lvl, caster, src, data);
        }
    }

    // ====================================================================================== added in 1.0.13
    /** Abyssal Ray - Herobrine's Void Beam, but wider: a channelled black-cored purple ray that erases what it touches. */
    @AutoSpellConfig
    public static class AbyssalRay extends Base {
        private static final String TAG = "OA_AbyssalRay";

        public AbyssalRay() { super("abyssal_ray", OBSIDIAN, SpellRarity.EPIC, 8, 16, CastType.CONTINUOUS, 100, 14, 3, 6, 1); }

        float damage(int lvl, LivingEntity c) { return 3.5f + getSpellPower(lvl, c) * 0.4f; }

        @Override public List<MutableComponent> getUniqueInfo(int lvl, LivingEntity c) {
            return List.of(Component.translatable("ui.obsidian_arsenal.damage_per_second", Utils.stringTruncation(damage(lvl, c) * 10, 1)),
                    range((float) com.obsidianarsenal.entity.VoidRayEntity.RANGE));
        }
        @Override public AnimationHolder getCastStartAnimation() { return ANIM_CHANNEL; }
        @Override public AnimationHolder getCastFinishAnimation() { return AnimationHolder.none(); }

        @Override
        public void onCast(Level level, int lvl, LivingEntity caster, CastSource src, MagicData data) {
            if (level instanceof ServerLevel sl) {
                var ray = ray(sl, caster);
                if (ray == null) {
                    ray = com.obsidianarsenal.entity.VoidRayEntity.spawn(sl, caster, damage(lvl, caster));
                    caster.getPersistentData().putInt(TAG, ray.getId());
                    sl.playSound(null, caster.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1.4f, 1.3f);
                    sl.playSound(null, caster.blockPosition(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.6f, 1.8f);
                }
                ray.keepAlive(damage(lvl, caster));
            }
            super.onCast(level, lvl, caster, src, data);
        }

        @Override
        public void onServerCastComplete(Level level, int lvl, LivingEntity caster, MagicData data, boolean cancelled) {
            if (level instanceof ServerLevel sl) {
                var ray = ray(sl, caster);
                if (ray != null) ray.discard();
                caster.getPersistentData().remove(TAG);
                sl.playSound(null, caster.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0f, 0.6f);
            }
            super.onServerCastComplete(level, lvl, caster, data, cancelled);
        }

        @Nullable
        private static com.obsidianarsenal.entity.VoidRayEntity ray(ServerLevel level, LivingEntity caster) {
            if (!caster.getPersistentData().contains(TAG)) return null;
            return level.getEntity(caster.getPersistentData().getInt(TAG)) instanceof com.obsidianarsenal.entity.VoidRayEntity r && r.isAlive() ? r : null;
        }
    }

    /** Shared lookup of a caster's live Erasure orb by persistent-data tag. */
    @Nullable
    private static com.obsidianarsenal.entity.ErasureOrbEntity orb(ServerLevel level, LivingEntity caster, String tag) {
        if (!caster.getPersistentData().contains(tag)) return null;
        return level.getEntity(caster.getPersistentData().getInt(tag)) instanceof com.obsidianarsenal.entity.ErasureOrbEntity o && o.isAlive() ? o : null;
    }

    /**
     * Erasure - charge a black orb rimmed in red at your hand, then fire it dead straight. Where it hits a block or a
     * creature it swells a few blocks wider and erases everything inside.
     */
    @AutoSpellConfig
    public static class Erasure extends Base {
        private static final String TAG = "OA_Erasure";
        static final int CHARGE = 24;

        public Erasure() { super("erasure", PLASMA, SpellRarity.LEGENDARY, 5, 30, CastType.LONG, CHARGE, 110, 15, 8, 1); }

        float damage(int lvl, LivingEntity c) { return 30f + getSpellPower(lvl, c) * 3f; }
        float blast(int lvl) { return 3.5f + lvl * 0.4f; }

        @Override public List<MutableComponent> getUniqueInfo(int lvl, LivingEntity c) { return List.of(dmg(damage(lvl, c)), radius(blast(lvl))); }
        @Override public AnimationHolder getCastStartAnimation() { return ANIM_CHANNEL; }
        @Override public AnimationHolder getCastFinishAnimation() { return ANIM_THRUST; }

        @Override
        public void onServerPreCast(Level level, int lvl, LivingEntity caster, MagicData data) {
            if (level instanceof ServerLevel sl) {
                var old = orb(sl, caster, TAG);
                if (old != null) old.discard();
                var o = com.obsidianarsenal.entity.ErasureOrbEntity.create(sl, caster, this, com.obsidianarsenal.entity.ErasureOrbEntity.ORB,
                        0.7, blast(lvl), damage(lvl, caster), CHARGE);
                caster.getPersistentData().putInt(TAG, o.getId());
                sl.playSound(null, caster.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2f, 0.5f);
            }
            super.onServerPreCast(level, lvl, caster, data);
        }

        @Override
        public void onCast(Level level, int lvl, LivingEntity caster, CastSource src, MagicData data) {
            if (level instanceof ServerLevel sl) {
                var o = orb(sl, caster, TAG);
                if (o == null) o = com.obsidianarsenal.entity.ErasureOrbEntity.create(sl, caster, this, com.obsidianarsenal.entity.ErasureOrbEntity.ORB,
                        0.7, blast(lvl), damage(lvl, caster), 1);
                o.fire(caster.getLookAngle());
                caster.getPersistentData().remove(TAG);
            }
            super.onCast(level, lvl, caster, src, data);
        }

        @Override
        public void onServerCastComplete(Level level, int lvl, LivingEntity caster, MagicData data, boolean cancelled) {
            if (level instanceof ServerLevel sl) {
                var o = orb(sl, caster, TAG);
                if (o != null && o.phase() == com.obsidianarsenal.entity.ErasureOrbEntity.CHARGE) o.discard();
                caster.getPersistentData().remove(TAG);
            }
            super.onServerCastComplete(level, lvl, caster, data, cancelled);
        }
    }

    /**
     * Erasure Volley - while you channel, plasma magic circles open one after another at random spots behind you;
     * each fires a single small Erasure orb at your crosshair and fades, and the next one opens.
     */
    @AutoSpellConfig
    public static class ErasureVolley extends Base {
        private static final String TAG = "OA_ErasureVolley", NEXT = "OA_ErasureVolleyNext";

        public ErasureVolley() { super("erasure_volley", PLASMA, SpellRarity.EPIC, 6, 22, CastType.CONTINUOUS, 120, 16, 3, 6, 1); }

        float damage(int lvl, LivingEntity c) { return 10f + getSpellPower(lvl, c) * 1.2f; }
        float blast(int lvl) { return 1.8f + lvl * 0.15f; }

        @Override public List<MutableComponent> getUniqueInfo(int lvl, LivingEntity c) { return List.of(dmg(damage(lvl, c)), radius(blast(lvl))); }
        @Override public AnimationHolder getCastStartAnimation() { return ANIM_CHANNEL; }
        @Override public AnimationHolder getCastFinishAnimation() { return AnimationHolder.none(); }

        @Override
        public void onServerPreCast(Level level, int lvl, LivingEntity caster, MagicData data) {
            caster.getPersistentData().putLong(NEXT, 0);
            super.onServerPreCast(level, lvl, caster, data);
        }

        /** Every tick of the channel: when no circle is charging and the pause after the last shot is over, open the next. */
        @Override
        public void onServerCastTick(Level level, int lvl, LivingEntity caster, MagicData data) {
            if (level instanceof ServerLevel sl && orb(sl, caster, TAG) == null && sl.getGameTime() >= caster.getPersistentData().getLong(NEXT)) {
                var o = com.obsidianarsenal.entity.ErasureOrbEntity.create(sl, caster, this, com.obsidianarsenal.entity.ErasureOrbEntity.VOLLEY,
                        0.32, blast(lvl), damage(lvl, caster), 7 + sl.random.nextInt(4));
                caster.getPersistentData().putInt(TAG, o.getId());
                o.onFired(() -> {
                    caster.getPersistentData().remove(TAG);
                    caster.getPersistentData().putLong(NEXT, sl.getGameTime() + 1 + sl.random.nextInt(6));
                });
                sl.playSound(null, o.getX(), o.getY(), o.getZ(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, SoundSource.PLAYERS, 0.7f, 1.6f);
            }
            super.onServerCastTick(level, lvl, caster, data);
        }

        @Override
        public void onServerCastComplete(Level level, int lvl, LivingEntity caster, MagicData data, boolean cancelled) {
            if (level instanceof ServerLevel sl) {
                var o = orb(sl, caster, TAG);
                if (o != null && o.phase() == com.obsidianarsenal.entity.ErasureOrbEntity.CHARGE) o.discard();
                caster.getPersistentData().remove(TAG);
                caster.getPersistentData().remove(NEXT);
            }
            super.onServerCastComplete(level, lvl, caster, data, cancelled);
        }
    }

    /**
     * Erasure Grasp - an Erasure orb held telekinetically in front of your outstretched hand, following your gaze. It
     * erases only what it passes through.
     */
    @AutoSpellConfig
    public static class ErasureGrasp extends Base {
        private static final String TAG = "OA_ErasureGrasp";

        public ErasureGrasp() { super("erasure_grasp", PLASMA, SpellRarity.EPIC, 6, 20, CastType.CONTINUOUS, 140, 12, 2, 6, 1); }

        float damage(int lvl, LivingEntity c) { return 4f + getSpellPower(lvl, c) * 0.5f; }
        float size(int lvl) { return 1.3f + lvl * 0.1f; }

        @Override public List<MutableComponent> getUniqueInfo(int lvl, LivingEntity c) {
            return List.of(Component.translatable("ui.obsidian_arsenal.damage_per_second", Utils.stringTruncation(damage(lvl, c) * 10, 1)), radius(size(lvl)));
        }
        @Override public AnimationHolder getCastStartAnimation() { return ANIM_CHANNEL; }
        @Override public AnimationHolder getCastFinishAnimation() { return AnimationHolder.none(); }

        @Override
        public void onCast(Level level, int lvl, LivingEntity caster, CastSource src, MagicData data) {
            if (level instanceof ServerLevel sl) {
                var o = orb(sl, caster, TAG);
                if (o == null) {
                    o = com.obsidianarsenal.entity.ErasureOrbEntity.create(sl, caster, this, com.obsidianarsenal.entity.ErasureOrbEntity.GRASP,
                            size(lvl), size(lvl) * 1.6, damage(lvl, caster), 1);
                    caster.getPersistentData().putInt(TAG, o.getId());
                    sl.playSound(null, caster.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2f, 0.6f);
                }
                o.keepAlive(damage(lvl, caster));
            }
            super.onCast(level, lvl, caster, src, data);
        }

        @Override
        public void onServerCastComplete(Level level, int lvl, LivingEntity caster, MagicData data, boolean cancelled) {
            if (level instanceof ServerLevel sl) {
                var o = orb(sl, caster, TAG);
                if (o != null) o.detonate();          // released: it pops, erasing a little wider, and fades
                caster.getPersistentData().remove(TAG);
            }
            super.onServerCastComplete(level, lvl, caster, data, cancelled);
        }
    }

}
