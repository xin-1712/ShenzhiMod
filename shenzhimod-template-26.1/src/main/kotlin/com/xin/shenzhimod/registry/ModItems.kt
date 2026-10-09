package com.xin.shenzhimod.registry

import com.xin.shenzhimod.ShenzhiMod
import com.xin.shenzhimod.content.item.CompressedItem
import com.xin.shenzhimod.content.item.NetherStarShardItem
import com.xin.shenzhimod.machine.CompressionDefaults
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.Registries
import net.minecraft.tags.TagKey
import net.minecraft.world.damagesource.DamageType
import net.minecraft.world.item.Item
import net.minecraft.world.item.Rarity
import net.minecraft.world.item.component.DamageResistant

/**
 * 物品注册（模块 A/B/C/D/E）。
 *
 * 43 个物品 = 37 个压缩态 + 4 个核心 + 1 个下界之星碎片 + 1 个前体材料（石灰岩）。
 * 方解石**沿用原版 `minecraft:calcite`**，模组只提供配方、不新增同名物品（铜锭/下界合金锭同理）。
 * 压缩态**全部由 [CompressionDefaults] 推导**，新增一条配方只需改那张表。
 */
object ModItems {
	/**
	 * 创世核心的伤害免疫标签（防火 + 防雷击）。
	 *
	 * 需求 §4.5 要求创世核心"防火、防岩浆、防雷击"，而 `fireResistant()` 只给防火——
	 * 祭坛完工时会真实落雷，产物如果没有防雷就会当场被劈没。
	 * 标签内容由数据生成写入 `data/shenzhimod/tags/damage_type/creation_core_immune.json`。
	 */
	val CREATION_CORE_IMMUNE: TagKey<DamageType> =
		TagKey.create(Registries.DAMAGE_TYPE, ModRegistry.id("creation_core_immune"))

	// ---- 前体材料（模块 D） ----
	/** 石灰岩：可直接放置的方块（2026-09-30 反馈），物品形态即方块物品。 */
	val LIMESTONE: Item = ModRegistry.blockItem(ModBlocks.LIMESTONE, "limestone")

	// ---- 投掷物材料（模块 H） ----
	/** 右键直接投掷，参数对齐附魔之瓶；堆叠 64、防火（§4.8）。 */
	val NETHER_STAR_SHARD: NetherStarShardItem = ModRegistry.item("nether_star_shard") { NetherStarShardItem(it.stacksTo(64).fireResistant()) }

	// ---- 四核心（模块 B/E） ----
	val AGRICULTURE_CORE: Item = ModRegistry.item("agriculture_core") { Item(it.stacksTo(64).fireResistant().rarity(Rarity.RARE)) }
	val MINERAL_CORE: Item = ModRegistry.item("mineral_core") { Item(it.stacksTo(64).fireResistant().rarity(Rarity.RARE)) }
	val COMBAT_CORE: Item = ModRegistry.item("combat_core") { Item(it.stacksTo(64).fireResistant().rarity(Rarity.RARE)) }
	val CREATION_CORE: Item = ModRegistry.item("creation_core") { properties ->
		Item(
			properties.stacksTo(64)
				.rarity(Rarity.EPIC)
				.delayedComponent(DataComponents.DAMAGE_RESISTANT) { context ->
					DamageResistant(context.getOrThrow(CREATION_CORE_IMMUNE))
				},
		)
	}

	// ---- 37 个压缩态（模块 A） ----
	val COMPRESSED: Map<String, Item> = CompressionDefaults.ENTRIES.associate { entry ->
		entry.outputPath to ModRegistry.item(entry.outputPath) { properties ->
			// `ENCHANTMENT_GLINT_OVERRIDE` = 原版附魔闪光（"和附魔一样的发光特效"，
			// 2026-09-30 反馈 #7）；贴图另外叠了一圈偏黑的紫光晕。
			CompressedItem(
				properties
					.stacksTo(64)
					.fireResistant()
					.component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true),
				entry.sourceItemId,
				entry.count,
			)
		}
	}

	// ---- 方块物品 ----
	val SYNTHETIC_DRAGON_HEAD: Item = ModRegistry.blockItem(ModBlocks.SYNTHETIC_DRAGON_HEAD, "synthetic_dragon_head")
	val TRANSMUTATION_CORE: Item = ModRegistry.blockItem(ModBlocks.TRANSMUTATION_CORE, "transmutation_core")
	val ULTIMATE_ALTAR: Item = ModRegistry.blockItem(ModBlocks.ULTIMATE_ALTAR, "ultimate_altar")

	/** 稳定的展示顺序（创造栏、数据生成、测试都用它）。 */
	val ALL: List<Item> = buildList {
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
