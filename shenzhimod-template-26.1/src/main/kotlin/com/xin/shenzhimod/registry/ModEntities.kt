package com.xin.shenzhimod.registry

import com.xin.shenzhimod.ShenzhiMod
import com.xin.shenzhimod.content.entity.ThrownNetherStarShard
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory

/**
 * 实体类型注册。
 *
 * 目前只有一个：下界之星碎片的投掷物（模块 H）。尺寸/追踪范围/更新间隔照抄原版附魔之瓶
 * （0.25×0.25、4 格追踪、10 tick 更新一次），保证联机下的弹道表现与原版一致。
 */
object ModEntities {
	val NETHER_STAR_SHARD: EntityType<ThrownNetherStarShard> = register("nether_star_shard")

	private fun register(path: String): EntityType<ThrownNetherStarShard> {
		val key: ResourceKey<EntityType<*>> = ResourceKey.create(Registries.ENTITY_TYPE, ModRegistry.id(path))
		val type = EntityType.Builder
			.of({ entityType, level -> ThrownNetherStarShard(entityType, level) }, MobCategory.MISC)
			.noLootTable()
			.sized(0.25f, 0.25f)
			.clientTrackingRange(4)
			.updateInterval(10)
			.build(key)
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type)
	}

	internal fun bootstrap(): List<EntityType<*>> {
		val all = listOf<EntityType<*>>(NETHER_STAR_SHARD)
		ShenzhiMod.logger.debug("已注册 {} 个实体类型", all.size)
		return all
	}
}
