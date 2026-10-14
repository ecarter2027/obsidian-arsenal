package com.obsidianarsenal.registry;

import com.obsidianarsenal.ObsidianArsenal;
import com.obsidianarsenal.entity.BlackHoleEntity;
import com.obsidianarsenal.entity.ConstructEntity;
import com.obsidianarsenal.entity.PlasmaBeamEntity;
import com.obsidianarsenal.entity.HellfireStreamEntity;
import com.obsidianarsenal.entity.IndraArrowEntity;
import com.obsidianarsenal.entity.ObsidianOrbProjectile;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ObsidianArsenal.MODID);

    /** One GeckoLib entity type per construct kind (spike, shard, tentacle, storm spike, hell column, gyroscope). */
    public static final Map<ConstructEntity.Kind, RegistryObject<EntityType<ConstructEntity>>> CONSTRUCTS = new EnumMap<>(ConstructEntity.Kind.class);

    static {
        for (ConstructEntity.Kind k : ConstructEntity.Kind.values()) {
            CONSTRUCTS.put(k, ENTITIES.register(k.model, () ->
                    EntityType.Builder.<ConstructEntity>of((t, l) -> new ConstructEntity(t, l, k), MobCategory.MISC)
                            .sized(k.width, k.height).clientTrackingRange(10).updateInterval(1).fireImmune().noSave()
                            .build(ObsidianArsenal.MODID + ":" + k.model)));
        }
    }

    public static final RegistryObject<EntityType<ObsidianOrbProjectile>> OBSIDIAN_ORB = ENTITIES.register("obsidian_orb", () ->
            EntityType.Builder.<ObsidianOrbProjectile>of(ObsidianOrbProjectile::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(8).updateInterval(1).noSave()
                    .build(ObsidianArsenal.MODID + ":obsidian_orb"));

    public static final RegistryObject<EntityType<IndraArrowEntity>> INDRA_ARROW = ENTITIES.register("indra_arrow", () ->
            EntityType.Builder.<IndraArrowEntity>of(IndraArrowEntity::new, MobCategory.MISC)
                    .sized(0.6f, 0.6f).clientTrackingRange(16).updateInterval(1).fireImmune().noSave()
                    .build(ObsidianArsenal.MODID + ":indra_arrow"));

    public static final RegistryObject<EntityType<HellfireStreamEntity>> HELLFIRE_STREAM = ENTITIES.register("hellfire_stream", () ->
            EntityType.Builder.<HellfireStreamEntity>of(HellfireStreamEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(10).updateInterval(1).fireImmune().noSave()
                    .build(ObsidianArsenal.MODID + ":hellfire_stream"));

    public static final RegistryObject<EntityType<PlasmaBeamEntity>> PLASMA_BEAM = ENTITIES.register("plasma_beam", () ->
            EntityType.Builder.<PlasmaBeamEntity>of(PlasmaBeamEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(12).updateInterval(1).fireImmune().noSave()
                    .build(ObsidianArsenal.MODID + ":plasma_beam"));

    public static final RegistryObject<EntityType<BlackHoleEntity>> BLACK_HOLE = ENTITIES.register("black_hole", () ->
            EntityType.Builder.<BlackHoleEntity>of(BlackHoleEntity::new, MobCategory.MISC)
                    .sized(1.0f, 1.0f).clientTrackingRange(12).updateInterval(1).fireImmune().noSave()
                    .build(ObsidianArsenal.MODID + ":black_hole"));
}
