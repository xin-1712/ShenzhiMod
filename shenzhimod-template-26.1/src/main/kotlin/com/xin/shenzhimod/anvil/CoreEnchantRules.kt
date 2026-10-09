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
 * 这里只用字符串表示物品与标签，真正解析 `TagKey` / `Holder<Enchantment>` 的工作在适配层
 * （`interaction/AnvilEnchantHandler.kt`）完成，这样规则表可以脱离 Minecraft 单独测试。
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

	/** 所有媒介物品 ID：用于"右槽是不是本模组核心"的快速判断（服务端与客户端都要用）。 */
	val MEDIA_ITEM_IDS: Set<String> = setOf(AGRICULTURE_CORE, MINERAL_CORE, COMBAT_CORE, CREATION_CORE)

	/**
	 * 目标类型一律用**原版物品标签**表达（§6.3-F：用标签而非硬编码物品），
	 * 而且直接复用原版附魔 JSON 里的 `supported_items` 标签——这样"原版能附这个附魔的物品"
	 * 与"核心附魔接受的物品"永远一致（含今后原版新增的武器类型，例如 26.1 的矛）：
	 * - 锋利 → `#minecraft:enchantable/sharp_weapon`（剑 / 矛 / 斧）
	 * - 保护 → `#minecraft:enchantable/chest_armor`（**只有胸甲**，不含头盔 / 护腿 / 靴子）
	 * - 效率 → `#minecraft:enchantable/mining`（镐 / 斧 / 锹 / 锄 / 剪刀）
	 * - 时运 → `#minecraft:enchantable/mining_loot`（镐 / 斧 / 锹 / 锄）
	 * - 冷却 / 飞行 → 单个物品 ID（原版没有"可附冷却/飞行的物品"标签）
	 *
	 * 等级按附魔分别配置：锋利的等级要能把"下界合金剑跳劈"抬过 20 点，效率/时运按需另调。
	 */
	fun build(
		coreCostLevels: Int,
		creationCoreCostLevels: Int,
		sharpnessLevel: Int,
		protectionLevel: Int,
		efficiencyLevel: Int,
		fortuneLevel: Int,
	): List<CoreEnchantRule> = listOf(
		CoreEnchantRule(
			id = "sharpness_from_combat_core",
			mediaItemId = COMBAT_CORE,
			targetTags = setOf("minecraft:enchantable/sharp_weapon"),
			targetItemIds = emptySet(),
			enchantmentId = "minecraft:sharpness",
			costLevels = coreCostLevels,
			grantedLevel = sharpnessLevel,
		),
		CoreEnchantRule(
			// 战斗核心的第二个用途：给胸甲写超上限的保护（其余护甲槽不匹配，直接退回原版逻辑）。
			id = "protection_from_combat_core",
			mediaItemId = COMBAT_CORE,
			targetTags = setOf("minecraft:enchantable/chest_armor"),
			targetItemIds = emptySet(),
			enchantmentId = "minecraft:protection",
			costLevels = coreCostLevels,
			grantedLevel = protectionLevel,
		),
		CoreEnchantRule(
			id = "efficiency_from_mineral_core",
			mediaItemId = MINERAL_CORE,
			targetTags = setOf("minecraft:enchantable/mining"),
			targetItemIds = emptySet(),
			enchantmentId = "minecraft:efficiency",
			costLevels = coreCostLevels,
			grantedLevel = efficiencyLevel,
		),
		CoreEnchantRule(
			id = "fortune_from_agriculture_core",
			mediaItemId = AGRICULTURE_CORE,
			targetTags = setOf("minecraft:enchantable/mining_loot"),
			targetItemIds = emptySet(),
			enchantmentId = "minecraft:fortune",
			costLevels = coreCostLevels,
			grantedLevel = fortuneLevel,
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
