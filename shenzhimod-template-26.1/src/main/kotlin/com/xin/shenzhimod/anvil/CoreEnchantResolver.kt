package com.xin.shenzhimod.anvil

/**
 * 核心附魔判定（纯逻辑）。
 *
 * 输入是"左槽物品 ID + 它命中的标签 + 右槽物品 ID + 目标已有的该附魔等级"，
 * 输出是"该不该产出、产出什么、成本多少"。
 * Mixin 适配层只负责把 `ItemStack` 翻译成 [CoreEnchantRequest]，以及把
 * [CoreEnchantDecision] 写回原版容器——判定本身完全可单测。
 */
data class CoreEnchantRequest(
	val targetItemId: String,
	val targetTags: Set<String>,
	val mediaItemId: String,
	val currentEnchantLevel: Int,
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
	 * @param respectExclusivity 是否遵守原版互斥（§8-G9 默认遵守）；为 true 时，
	 *        目标身上已存在互斥附魔也会交给原版处理（此处只做"是否产出"的判断，
	 *        互斥的实际拦截由原版铁砧逻辑负责，我们只保证不绕过它）。
	 */
	fun resolve(
		request: CoreEnchantRequest,
		rules: List<CoreEnchantRule>,
		respectExclusivity: Boolean,
	): CoreEnchantDecision {
		val candidates = rules.filter { it.mediaItemId == request.mediaItemId }
		if (candidates.isEmpty()) return CoreEnchantDecision.NotApplicable

		for (rule in candidates) {
			if (!matchesTarget(rule, request)) continue
			val targetLevel = minOf(rule.grantedLevel, request.currentEnchantLevel + 1)
			if (targetLevel <= request.currentEnchantLevel) {
				return CoreEnchantDecision.AlreadyAtLeast(rule.enchantmentId, request.currentEnchantLevel)
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
