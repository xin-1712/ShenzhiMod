package com.xin.shenzhimod.machine

/**
 * 压缩体系的数据模型（模块 A）。
 *
 * 这个文件属于 `machine/` 层：**不引用任何 Minecraft 类型**，可以直接写单元测试。
 * 物品一律用字符串 ID 表示，由 `registry/` 与 `interaction/` 适配成真正物品。
 *
 * 玩法规则（需求 §4.1）：
 * - 铁砧上方 3×3×3 内的掉落物必须是**同一种**物品，且在压缩表中；
 * - 数量 N ≥ 配方需求 R 才生效，产出 `floor(N / R)` 个压缩态，余数 `N % R` 原样保留；
 * - 原子性：要么整体成功，要么完全不动。
 */
enum class CompressionGroup {
	/** 农业核心材料。 */
	AGRICULTURE,
	/** 矿物核心材料。 */
	MINERAL,
	/** 战斗核心材料。 */
	COMBAT,
}

/**
 * 一条压缩配方。
 *
 * @param outputPath 压缩态物品的路径（不含命名空间），如 `compressed_wheat`
 * @param sourceItemId 被压缩的原版物品 ID，如 `minecraft:wheat`
 * @param count 压缩所需数量 R
 * @param group 所属核心分组
 * @param nameEn 英文显示名（用于语言数据生成）
 * @param nameZh 中文显示名（用于语言数据生成）
 */
data class CompressionEntry(
	val outputPath: String,
	val sourceItemId: String,
	val count: Int,
	val group: CompressionGroup,
	val nameEn: String,
	val nameZh: String,
)

/** 一条核心合成需求。 */
data class CoreRequirement(val itemId: String, val count: Int)

/**
 * 压缩表：支持按"产物"和"被压缩物"双向查询。
 *
 * 之所以两种索引都要，是因为：
 * - 交互时需要"手里/地上的物品 → 配方"（按 source 查）；
 * - 龙首吸收时需要"这个物品是不是压缩态白名单"（按 output 查）。
 */
class CompressionTable(entries: List<CompressionEntry>) {
	val entries: List<CompressionEntry> = entries.toList()

	private val bySource: Map<String, CompressionEntry> = this.entries.associateBy { it.sourceItemId }
	private val byOutput: Map<String, CompressionEntry> = this.entries.associateBy { it.outputPath }

	fun entryForSource(sourceItemId: String): CompressionEntry? = bySource[sourceItemId]

	fun entryForOutput(outputPath: String): CompressionEntry? = byOutput[outputPath]

	/** 该压缩态是否在白名单内（龙首吸收 / 压缩产物校验）。 */
	fun isCompressedOutput(outputPath: String): Boolean = byOutput.containsKey(outputPath)

	fun byGroup(group: CompressionGroup): List<CompressionEntry> = entries.filter { it.group == group }

	/**
	 * 按数量结算压缩。
	 *
	 * @return 产出个数与剩余数量；`produced == 0` 表示条件不足、调用方必须什么都不做。
	 */
	fun settle(required: Int, available: Int, availableDurability: Int, durabilityCostPerOutput: Int, maxOutputs: Int): CompressionOutcome {
		if (required <= 0 || available < required) return CompressionOutcome(0, available)

		val byCount = available / required
		val durabilityLimited = if (durabilityCostPerOutput <= 0) {
			byCount
		} else {
			availableDurability / durabilityCostPerOutput
		}
		val capped = if (maxOutputs <= 0) byCount else minOf(byCount, maxOutputs)
		val produced = minOf(capped, durabilityLimited)
		if (produced <= 0) return CompressionOutcome(0, available)

		return CompressionOutcome(produced, available - produced * required)
	}
}

/** 压缩结算结果。 */
data class CompressionOutcome(val produced: Int, val remaining: Int) {
	val effective: Boolean get() = produced > 0
}

/**
 * 默认压缩表。
 *
 * ⚠️ 数据来源与待办：
 * 需求文档 §3.2 只给出了分组数量（农业 14 / 矿物 11 / 战斗 12 = 37），
 * 逐项明细来自已不在仓库里的原始需求文档 A。下面这份表是**按分组数量与生电常见农场资源补出的候选表**，
 * 上线前必须逐项核对（这是当前唯一的阻塞性数据缺口，见 CHANGELOG 的"待确认"一节）。
 *
 * 另外记录一处**文档内部矛盾**：§3.2 战斗核心一行写"12"，但后面列的
 * `3456×4、1728×5、432×4、256×1` 实际相加是 14。本表按"总数 37 / 战斗核心 12"实现，
 * 暂定分布为 `3456×4、1728×5、432×2、256×1`。
 * 备选条目（回血时直接加回即可）：`minecraft:echo_shard`、`minecraft:wither_skeleton_skull`。
 */
object CompressionDefaults {
	private fun agri(output: String, source: String, count: Int, zh: String, en: String) =
		CompressionEntry(output, source, count, CompressionGroup.AGRICULTURE, en, zh)

	private fun mineral(output: String, source: String, count: Int, zh: String, en: String) =
		CompressionEntry(output, source, count, CompressionGroup.MINERAL, en, zh)

	private fun combat(output: String, source: String, count: Int, zh: String, en: String) =
		CompressionEntry(output, source, count, CompressionGroup.COMBAT, en, zh)

	val ENTRIES: List<CompressionEntry> = listOf(
		// ---- 农业核心（14）：3456 ×7 / 1728 ×5 / 432 ×2 ----
		agri("compressed_wheat", "minecraft:wheat", 3456, "压缩小麦", "Compressed Wheat"),
		agri("compressed_carrot", "minecraft:carrot", 3456, "压缩胡萝卜", "Compressed Carrot"),
		agri("compressed_potato", "minecraft:potato", 3456, "压缩马铃薯", "Compressed Potato"),
		agri("compressed_beetroot", "minecraft:beetroot", 3456, "压缩甜菜根", "Compressed Beetroot"),
		agri("compressed_sugar_cane", "minecraft:sugar_cane", 3456, "压缩甘蔗", "Compressed Sugar Cane"),
		agri("compressed_pumpkin", "minecraft:pumpkin", 3456, "压缩南瓜", "Compressed Pumpkin"),
		agri("compressed_melon_slice", "minecraft:melon_slice", 3456, "压缩西瓜片", "Compressed Melon Slice"),
		agri("compressed_cactus", "minecraft:cactus", 1728, "压缩仙人掌", "Compressed Cactus"),
		agri("compressed_bamboo", "minecraft:bamboo", 1728, "压缩竹子", "Compressed Bamboo"),
		agri("compressed_cocoa_beans", "minecraft:cocoa_beans", 1728, "压缩可可豆", "Compressed Cocoa Beans"),
		agri("compressed_sweet_berries", "minecraft:sweet_berries", 1728, "压缩甜浆果", "Compressed Sweet Berries"),
		agri("compressed_kelp", "minecraft:kelp", 1728, "压缩海带", "Compressed Kelp"),
		agri("compressed_nether_wart", "minecraft:nether_wart", 432, "压缩下界疣", "Compressed Nether Wart"),
		agri("compressed_apple", "minecraft:apple", 432, "压缩苹果", "Compressed Apple"),

		// ---- 矿物核心（11）：1728 ×9 / 432 ×2 ----
		mineral("compressed_coal", "minecraft:coal", 1728, "压缩煤炭", "Compressed Coal"),
		mineral("compressed_raw_iron", "minecraft:raw_iron", 1728, "压缩粗铁", "Compressed Raw Iron"),
		mineral("compressed_raw_copper", "minecraft:raw_copper", 1728, "压缩粗铜", "Compressed Raw Copper"),
		mineral("compressed_raw_gold", "minecraft:raw_gold", 1728, "压缩粗金", "Compressed Raw Gold"),
		mineral("compressed_redstone", "minecraft:redstone", 1728, "压缩红石粉", "Compressed Redstone"),
		mineral("compressed_lapis_lazuli", "minecraft:lapis_lazuli", 1728, "压缩青金石", "Compressed Lapis Lazuli"),
		mineral("compressed_quartz", "minecraft:quartz", 1728, "压缩下界石英", "Compressed Nether Quartz"),
		mineral("compressed_amethyst_shard", "minecraft:amethyst_shard", 1728, "压缩紫水晶碎片", "Compressed Amethyst Shard"),
		mineral("compressed_glowstone_dust", "minecraft:glowstone_dust", 1728, "压缩荧石粉", "Compressed Glowstone Dust"),
		mineral("compressed_diamond", "minecraft:diamond", 432, "压缩钻石", "Compressed Diamond"),
		mineral("compressed_emerald", "minecraft:emerald", 432, "压缩绿宝石", "Compressed Emerald"),

		// ---- 战斗核心（12）：3456 ×4 / 1728 ×5 / 432 ×2 / 256 ×1 ----
		combat("compressed_rotten_flesh", "minecraft:rotten_flesh", 3456, "压缩腐肉", "Compressed Rotten Flesh"),
		combat("compressed_bone", "minecraft:bone", 3456, "压缩骨头", "Compressed Bone"),
		combat("compressed_string", "minecraft:string", 3456, "压缩线", "Compressed String"),
		combat("compressed_gunpowder", "minecraft:gunpowder", 3456, "压缩火药", "Compressed Gunpowder"),
		combat("compressed_spider_eye", "minecraft:spider_eye", 1728, "压缩蜘蛛眼", "Compressed Spider Eye"),
		combat("compressed_slime_ball", "minecraft:slime_ball", 1728, "压缩粘液球", "Compressed Slimeball"),
		combat("compressed_ender_pearl", "minecraft:ender_pearl", 1728, "压缩末影珍珠", "Compressed Ender Pearl"),
		combat("compressed_blaze_rod", "minecraft:blaze_rod", 1728, "压缩烈焰棒", "Compressed Blaze Rod"),
		combat("compressed_ghast_tear", "minecraft:ghast_tear", 1728, "压缩恶魂之泪", "Compressed Ghast Tear"),
		combat("compressed_phantom_membrane", "minecraft:phantom_membrane", 432, "压缩幻翼膜", "Compressed Phantom Membrane"),
		combat("compressed_magma_cream", "minecraft:magma_cream", 432, "压缩岩浆膏", "Compressed Magma Cream"),
		combat("compressed_shulker_shell", "minecraft:shulker_shell", 256, "压缩潜影壳", "Compressed Shulker Shell"),
	)
}
