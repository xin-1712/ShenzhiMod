package com.xin.shenzhimod.registry

import com.xin.shenzhimod.ShenzhiMod
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockBehaviour

/**
 * 注册工具。
 *
 * 26.1 的注册约定（已核实）：`Item.Properties` / `BlockBehaviour.Properties` 必须带上
 * `setId(ResourceKey)`，注册时也用 `Registry.register(registry, ResourceKey, value)`，
 * 否则物品栏、数据生成、模型解析都会拿不到 ID。
 */
internal object ModRegistry {
	fun id(path: String): Identifier = ShenzhiMod.id(path)

	fun itemKey(path: String): ResourceKey<Item> = ResourceKey.create(Registries.ITEM, id(path))

	fun blockKey(path: String): ResourceKey<Block> = ResourceKey.create(Registries.BLOCK, id(path))

	fun <T : Item> item(path: String, factory: (Item.Properties) -> T): T {
		val key = itemKey(path)
		val value = factory(Item.Properties().setId(key))
		return Registry.register(BuiltInRegistries.ITEM, key, value)
	}

	fun <T : Block> block(path: String, factory: (BlockBehaviour.Properties) -> T): T {
		val key = blockKey(path)
		val value = factory(BlockBehaviour.Properties.of().setId(key))
		return Registry.register(BuiltInRegistries.BLOCK, key, value)
	}

	/** 给方块注册对应的 BlockItem（与方块同名）。 */
	fun blockItem(block: Block, path: String): Item =
		item(path) { properties -> BlockItem(block, properties.useBlockDescriptionPrefix()) }
}
