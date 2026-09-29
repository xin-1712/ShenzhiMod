package com.xin.shenzhimod.anvil

/**
 * 核心附魔判定（纯逻辑）。
 *
 * 输入是"左槽物品 ID + 它命中的标签 + 右槽物品 ID + 目标已有的附魔等级表"，
 * 输出是"该不该产出、产出什么、成本多少"。
 * Mixin 适配层只负责把 `ItemStack` 翻译成 [CoreEnchantRequest]，以及把
 * [CoreEnchantDecision] 写回原版容器——判定本身完全可单测。
 *
 * 与"原版互斥附魔"（§8-G9）有关的判定不在这里：它需要真正的 `Holder<Enchantment>` 与
 * `Enchantment.areCompatible`，属于适配层的活（见 `interaction/AnvilEnchantHandler.kt`）。
 */
data class CoreEnchantRequest(
	val targetItemId: String,
	val targetTags: Set<String>,
	val mediaItemId: String,
	/** 目标当前已有的附魔：附魔 ID → 等级（没有的附魔即 0）。 */
	val currentLevels: Map<String, Int>,
)

sealed interface CoreEnchantDecision {
	/** 右槽不是本模组核心，或目标类型不匹配：交由原版逻辑处理。 */
	data object NotApplicable : CoreEnchantDecision

	/** 命中规则，应当产出已附魔副本。 */
	data class Apply(val ruleId: String, val enchantmentId: String, val level: Int, val costLevels: Int) : CoreEnchantDecision

	/** 目标已有不低于目标等级的该附魔：不产出，避免等级回退。 */
	data class AlreadyAtLeast(val enchantmentId: String, val level: Int) : CoreEnchantDecision
}

object CoreEnchantResolver {
	/**
	 * @param rules 规则表（由配置生成）
	 */
	fun resolve(
		request: CoreEnchantRequest,
		rules: List<CoreEnchantRule>,
	): CoreEnchantDecision {
		val candidates = rules.filter { it.mediaItemId == request.mediaItemId }
		if (candidates.isEmpty()) return CoreEnchantDecision.NotApplicable

		for (rule in candidates) {
			if (!matchesTarget(rule, request)) continue
			val current = request.currentLevels[rule.enchantmentId] ?: 0
			// §4.6："同种附魔取更高等级，上限 grantedLevel" —— 已有 V 级也直接抬到 X 级。
			val targetLevel = maxOf(current, rule.grantedLevel)
			if (targetLevel <= current) {
				return CoreEnchantDecision.AlreadyAtLeast(rule.enchantmentId, current)
			}
			return CoreEnchantDecision.Apply(rule.id, rule.enchantmentId, targetLevel, rule.costLevels)
		}
		return CoreEnchantDecision.NotApplicable
	}

	private fun matchesTarget(rule: CoreEnchantRule, request: CoreEnchantRequest): Boolean {
		if (rule.targetItemIds.contains(request.targetItemId)) return true
		return rule.targetTags.any { request.targetTags.contains(it) }
	}
}
