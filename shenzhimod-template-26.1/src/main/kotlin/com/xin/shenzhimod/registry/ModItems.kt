package com.xin.shenzhimod.registry

import com.xin.shenzhimod.ShenzhiMod
import com.xin.shenzhimod.content.item.CompressedItem
import com.xin.shenzhimod.machine.CompressionDefaults
import net.minecraft.world.item.Item
import net.minecraft.world.item.Rarity

/**
 * 物品注册（模块 A/B/C/D/E）。
 *
 * 44 个物品 = 37 个压缩态 + 4 个核心 + 1 个下界之星碎片 + 2 个前体材料（方解石/石灰岩）。
 * 压缩态**全部由 [CompressionDefaults] 推导**，新增一条配方只需改那张表。
 */
object ModItems {
	// ---- 前体材料（模块 D） ----
	val CALCITE: Item = ModRegistry.item("calcite") { Item(it.stacksTo(64)) }
	val LIMESTONE: Item = ModRegistry.item("limestone") { Item(it.stacksTo(64)) }

	// ---- 投掷物材料（模块 H） ----
	val NETHER_STAR_SHARD: Item = ModRegistry.item("nether_star_shard") { Item(it.stacksTo(64).fireResistant()) }

	// ---- 四核心（模块 B/E） ----
	val AGRICULTURE_CORE: Item = ModRegistry.item("agriculture_core") { Item(it.stacksTo(64).fireResistant().rarity(Rarity.RARE)) }
	val MINERAL_CORE: Item = ModRegistry.item("mineral_core") { Item(it.stacksTo(64).fireResistant().rarity(Rarity.RARE)) }
	val COMBAT_CORE: Item = ModRegistry.item("combat_core") { Item(it.stacksTo(64).fireResistant().rarity(Rarity.RARE)) }
	val CREATION_CORE: Item = ModRegistry.item("creation_core") { Item(it.stacksTo(64).fireResistant().rarity(Rarity.EPIC)) }

	// ---- 37 个压缩态（模块 A） ----
	val COMPRESSED: Map<String, Item> = CompressionDefaults.ENTRIES.associate { entry ->
		entry.outputPath to ModRegistry.item(entry.outputPath) { properties ->
			CompressedItem(properties.stacksTo(64).fireResistant(), entry.sourceItemId, entry.count)
		}
	}

	// ---- 方块物品 ----
	val SYNTHETIC_DRAGON_HEAD: Item = ModRegistry.blockItem(ModBlocks.SYNTHETIC_DRAGON_HEAD, "synthetic_dragon_head")
	val TRANSMUTATION_CORE: Item = ModRegistry.blockItem(ModBlocks.TRANSMUTATION_CORE, "transmutation_core")
	val ULTIMATE_ALTAR: Item = ModRegistry.blockItem(ModBlocks.ULTIMATE_ALTAR, "ultimate_altar")

	/** 稳定的展示顺序（创造栏、数据生成、测试都用它）。 */
	val ALL: List<Item> = buildList {
		add(CALCITE)
		add(LIMESTONE)
		add(NETHER_STAR_SHARD)
		add(AGRICULTURE_CORE)
		add(MINERAL_CORE)
		add(COMBAT_CORE)
		add(CREATION_CORE)
		addAll(COMPRESSED.values)
		add(SYNTHETIC_DRAGON_HEAD)
		add(TRANSMUTATION_CORE)
		add(ULTIMATE_ALTAR)
	}

	internal fun bootstrap(): List<Item> {
		ShenzhiMod.logger.debug("已注册 {} 个物品", ALL.size)
		return ALL
	}
}
