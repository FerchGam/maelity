package dev.maelity;

import dev.maelity.entity.MaelityEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MaelityMod.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<MaelityEntity>> MAELITY =
            ENTITIES.register("maelity", () -> EntityType.Builder.of(MaelityEntity::new, MobCategory.MISC)
                    .sized(0.6f, 0.6f)
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .build("maelity"));

    private ModEntities() {}
}
