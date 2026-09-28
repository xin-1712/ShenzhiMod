package com.xin.shenzhimod.anvil

/**
 * 核心附魔规则（模块 F，纯逻辑）。
 *
 * 需求（§4.6）：把三核心/创世核心当"终极附魔书"在铁砧右槽使用。
 * 规则：
 * - 成本固定显示，不按原版公式；
 * - 同种附魔取更高等级，上限为配置的 `grantedLevel`；
 * - 类型不匹配不产出结果；
 * - 只拦截"右槽是本模组核心"的分支，其余交互走原版。
 *
 * 这里只用字符串表示物品与标签，真正解析 `TagKey` 的工作在适配层完成，
 * 这样规则表可以脱离 Minecraft 单独测试。
 */
data class CoreEnchantRule(
	/** 规则名，用于日志与数据生成。 */
	val id: String,
	/** 媒介物品 ID（右槽），如 `shenzhimod:combat_core`。 */
	val mediaItemId: String,
	/** 目标物品需要命中的原版物品标签（任一命中即可）。 */
	val targetTags: Set<String>,
	/** 兜底的目标物品 ID（没有合适标签时使用），如 `minecraft:totem_of_undying`。 */
	val targetItemIds: Set<String>,
	/** 写入的原版附魔 ID。 */
	val enchantmentId: String,
	/** 固定经验等级成本。 */
	val costLevels: Int,
	/** 写入的附魔等级上限。 */
	val grantedLevel: Int,
)

object CoreEnchantDefaults {
	const val AGRICULTURE_CORE = "shenzhimod:agriculture_core"
	const val MINERAL_CORE = "shenzhimod:mineral_core"
	const val COMBAT_CORE = "shenzhimod:combat_core"
	const val CREATION_CORE = "shenzhimod:creation_core"

	const val ENCHANT_TOTEM_COOLDOWN = "shenzhimod:totem_cooldown"
	const val ENCHANT_FLIGHT = "shenzhimod:flight"

	fun build(coreCostLevels: Int, creationCoreCostLevels: Int, grantedLevel: Int): List<CoreEnchantRule> = listOf(
		CoreEnchantRule(
			id = "sharpness_from_combat_core",
			mediaItemId = COMBAT_CORE,
			targetTags = setOf("minecraft:enchantable/sharp_weapon"),
			targetItemIds = emptySet(),
			enchantmentId = "minecraft:sharpness",
			costLevels = coreCostLevels,
			grantedLevel = grantedLevel,
		),
		CoreEnchantRule(
			id = "efficiency_from_mineral_core",
			mediaItemId = MINERAL_CORE,
			targetTags = setOf("minecraft:enchantable/mining"),
			targetItemIds = emptySet(),
			enchantmentId = "minecraft:efficiency",
			costLevels = coreCostLevels,
			grantedLevel = grantedLevel,
		),
		CoreEnchantRule(
			id = "fortune_from_agriculture_core",
			mediaItemId = AGRICULTURE_CORE,
			targetTags = setOf("minecraft:enchantable/mining_loot"),
			targetItemIds = emptySet(),
			enchantmentId = "minecraft:fortune",
			costLevels = coreCostLevels,
			grantedLevel = grantedLevel,
		),
		CoreEnchantRule(
			id = "totem_cooldown_from_combat_core",
			mediaItemId = COMBAT_CORE,
			targetTags = emptySet(),
			targetItemIds = setOf("minecraft:totem_of_undying"),
			enchantmentId = ENCHANT_TOTEM_COOLDOWN,
			costLevels = coreCostLevels,
			grantedLevel = 1,
		),
		CoreEnchantRule(
			id = "flight_from_creation_core",
			mediaItemId = CREATION_CORE,
			targetTags = emptySet(),
			targetItemIds = setOf("minecraft:elytra"),
			enchantmentId = ENCHANT_FLIGHT,
			costLevels = creationCoreCostLevels,
			grantedLevel = 1,
		),
	)
}
