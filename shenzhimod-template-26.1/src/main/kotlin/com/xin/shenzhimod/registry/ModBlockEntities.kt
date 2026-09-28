package com.xin.shenzhimod.registry

import com.xin.shenzhimod.ShenzhiMod
import com.xin.shenzhimod.content.blockentity.SyntheticDragonHeadBlockEntity
import com.xin.shenzhimod.content.blockentity.TransmutationCoreBlockEntity
import com.xin.shenzhimod.content.blockentity.UltimateAltarBlockEntity
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType

/**
 * 方块实体注册。
 *
 * 26.1 的 `BlockEntityType` 构造器是包私有的、且**没有原版 Builder**，
 * 因此必须走 Fabric 的 `fabric-object-builder-api-v1`：
 * 由 Java 侧的 [BlockEntityTypes] 调 `FabricBlockEntityTypeBuilder.create(...).build()`
 * （Kotlin 无法直接 import 含 `object` 关键字的包名）。
 */
object ModBlockEntities {
	val SYNTHETIC_DRAGON_HEAD: BlockEntityType<SyntheticDragonHeadBlockEntity> = register(
		"synthetic_dragon_head",
		BlockEntityTypes.create(::SyntheticDragonHeadBlockEntity, ModBlocks.SYNTHETIC_DRAGON_HEAD),
	)

	val TRANSMUTATION_CORE: BlockEntityType<TransmutationCoreBlockEntity> = register(
		"transmutation_core",
		BlockEntityTypes.create(::TransmutationCoreBlockEntity, ModBlocks.TRANSMUTATION_CORE),
	)

	val ULTIMATE_ALTAR: BlockEntityType<UltimateAltarBlockEntity> = register(
		"ultimate_altar",
		BlockEntityTypes.create(::UltimateAltarBlockEntity, ModBlocks.ULTIMATE_ALTAR),
	)

	val ALL: List<BlockEntityType<*>> = listOf(SYNTHETIC_DRAGON_HEAD, TRANSMUTATION_CORE, ULTIMATE_ALTAR)

	private fun <T : BlockEntity> register(
		path: String,
		type: BlockEntityType<T>,
	): BlockEntityType<T> {
		val key: ResourceKey<BlockEntityType<*>> = ResourceKey.create(Registries.BLOCK_ENTITY_TYPE, ModRegistry.id(path))
		return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, key, type)
	}

	internal fun bootstrap(): List<BlockEntityType<*>> {
		ShenzhiMod.logger.debug("已注册 {} 个方块实体类型", ALL.size)
		return ALL
	}
}
