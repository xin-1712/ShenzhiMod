package com.xin.shenzhimod.machine

/**
 * 合成龙首的配方表（模块 B）。
 *
 * 三核心的配方**直接从压缩表派生**：每种核心需要它所属分组的全部压缩态各 1 个。
 * 这样"压缩表改了但合成表忘了改"这类不一致在结构上就不可能发生。
 */
data class CoreAssemblyRecipe(
	val outputItemId: String,
	val group: CompressionGroup,
	val requirements: List<CoreRequirement>,
)

object CoreAssemblyDefaults {
	const val AGRICULTURE_CORE = "shenzhimod:agriculture_core"
	const val MINERAL_CORE = "shenzhimod:mineral_core"
	const val COMBAT_CORE = "shenzhimod:combat_core"

	fun build(table: CompressionTable): List<CoreAssemblyRecipe> = listOf(
		recipe(AGRICULTURE_CORE, CompressionGroup.AGRICULTURE, table),
		recipe(MINERAL_CORE, CompressionGroup.MINERAL, table),
		recipe(COMBAT_CORE, CompressionGroup.COMBAT, table),
	)

	private fun recipe(output: String, group: CompressionGroup, table: CompressionTable): CoreAssemblyRecipe =
		CoreAssemblyRecipe(
			outputItemId = output,
			group = group,
			requirements = table.byGroup(group).map { CoreRequirement("shenzhimod:${it.outputPath}", 1) },
		)

	/** 核心物品 ID 与分组的对应关系（龙首判定用）。 */
	fun coreItemIds(): Set<String> = setOf(AGRICULTURE_CORE, MINERAL_CORE, COMBAT_CORE)
}

/** 合成龙首的配方匹配结果。 */
sealed interface AssemblyMatch {
	/** 还没有凑齐任何配方。 */
	data object None : AssemblyMatch

	/** 已凑齐某个配方，可以开始/继续合成。 */
	data class Matched(val recipe: CoreAssemblyRecipe) : AssemblyMatch
}

/**
 * 从账本快照里找出"应当匹配"的配方。
 * 三种核心材料互斥（需求 §4.2 "多配方匹配 → 不允许同时匹配多个配方"），
 * 因此这里最多返回一个；若真的同时满足多个，按枚举顺序取第一个并交由上层记录告警。
 */
fun List<CoreAssemblyRecipe>.match(stored: Map<String, Int>): AssemblyMatch {
	for (recipe in this) {
		val satisfied = recipe.requirements.all { (stored[it.itemId] ?: 0) >= it.count }
		if (satisfied) return AssemblyMatch.Matched(recipe)
	}
	return AssemblyMatch.None
}
