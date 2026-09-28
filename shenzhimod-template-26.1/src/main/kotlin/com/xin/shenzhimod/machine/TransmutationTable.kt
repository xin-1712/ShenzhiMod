package com.xin.shenzhimod.machine

/**
 * 转化核心的配方模型（模块 C）。
 *
 * 配方 = 输入 + 催化剂 → 产物，固定耗时。催化剂每条配方消耗 1 个（需求 §4.3）。
 * 与压缩表一样，这里是纯逻辑，物品用字符串 ID 表示。
 */
data class TransmutationEntry(
	val id: String,
	val inputItemId: String,
	val inputCount: Int,
	val catalystItemId: String,
	val catalystCount: Int,
	val outputItemId: String,
	val outputCount: Int,
	val durationTicks: Int,
)

class TransmutationTable(entries: List<TransmutationEntry>) {
	val entries: List<TransmutationEntry> = entries.toList()

	/** 按输入的物品 ID 建索引；同一输入只允许一条配方，否则数据表自检会报错。 */
	private val byInput: Map<String, TransmutationEntry> = this.entries.associateBy { it.inputItemId }

	fun entryForInput(inputItemId: String): TransmutationEntry? = byInput[inputItemId]

	/** 数据表自检：返回问题列表（空表示健康）。 */
	fun validate(): List<String> {
		val problems = mutableListOf<String>()
		entries.groupBy { it.inputItemId }.filterValues { it.size > 1 }.keys.forEach {
			problems += "输入物品 $it 绑定了多条转化配方，行为不确定"
		}
		entries.filter { it.inputCount <= 0 || it.outputCount <= 0 }.forEach {
			problems += "配方 ${it.id} 的数量必须为正数"
		}
		entries.filter { it.durationTicks <= 0 }.forEach {
			problems += "配方 ${it.id} 的处理时间必须为正数"
		}
		return problems
	}
}

object TransmutationDefaults {
	/** 默认处理时间（tick）会被 `config.transmutation.defaultDurationTicks` 覆盖。 */
	fun build(defaultDurationTicks: Int): List<TransmutationEntry> = listOf(
		TransmutationEntry(
			id = "coal_block_to_diamond",
			inputItemId = "minecraft:coal_block",
			inputCount = 1,
			catalystItemId = "shenzhimod:nether_star_shard",
			catalystCount = 1,
			outputItemId = "minecraft:diamond",
			outputCount = 1,
			durationTicks = defaultDurationTicks,
		),
		TransmutationEntry(
			id = "calcite_to_amethyst_shard",
			inputItemId = "shenzhimod:calcite",
			inputCount = 8,
			catalystItemId = "minecraft:blaze_powder",
			catalystCount = 1,
			outputItemId = "minecraft:amethyst_shard",
			outputCount = 4,
			durationTicks = defaultDurationTicks,
		),
		TransmutationEntry(
			id = "limestone_to_lapis_lazuli",
			inputItemId = "shenzhimod:limestone",
			inputCount = 8,
			catalystItemId = "minecraft:ghast_tear",
			catalystCount = 1,
			outputItemId = "minecraft:lapis_lazuli",
			outputCount = 4,
			durationTicks = defaultDurationTicks,
		),
	)
}
