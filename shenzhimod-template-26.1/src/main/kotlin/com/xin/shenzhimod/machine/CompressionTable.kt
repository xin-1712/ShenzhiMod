package com.xin.shenzhimod.machine

/**
 * 压缩体系的数据模型（模块 A）。
 *
 * 这个文件属于 `machine/` 层：**不引用任何 Minecraft 类型**，可以直接写单元测试。
 * 物品一律用字符串 ID 表示，由 `registry/` 与 `interaction/` 适配成真正物品。
 *
 * 玩法规则：
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

/**
 * 一条核心合成需求。
 *
 * @param ledgerKey **账本键 = 模组内路径，不含命名空间**（如 `compressed_wheat`）。
 *   必须与 [DragonHeadLedger] 使用的键格式完全一致——曾经因为这里写成 `shenzhimod:compressed_wheat`
 *   而账本存的是 `compressed_wheat`，导致"材料凑齐也永远合不出核心"。
 */
data class CoreRequirement(val ledgerKey: String, val count: Int)

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
 * 默认压缩表 —— **唯一权威来源是需求文档 §3.2 的三张逐项表**（共 37 项）。
 *
 * 这张表与 §3.2 逐条对齐，改动规则：
 * - 数值/物品只能跟着 §3.2 走，不要凭手感调；调完要同步 §0 的挂机时长估算与 §6.4 的贴图口径。
 * - 矿物核心吃**方块形态**（不搬粗矿/单体），并包含**下界合金锭**——这是 M8 猪灵蛮兵链条的意义所在。
 * - 战斗核心按 Q2 修正后的分布：3456×3 / 1728×4 / 432×4 / 256×1。
 */
object CompressionDefaults {
	private fun agri(output: String, source: String, count: Int, zh: String, en: String) =
		CompressionEntry(output, source, count, CompressionGroup.AGRICULTURE, en, zh)

	private fun mineral(output: String, source: String, count: Int, zh: String, en: String) =
		CompressionEntry(output, source, count, CompressionGroup.MINERAL, en, zh)

	private fun combat(output: String, source: String, count: Int, zh: String, en: String) =
		CompressionEntry(output, source, count, CompressionGroup.COMBAT, en, zh)

	val ENTRIES: List<CompressionEntry> = listOf(
		// ---- 农业核心压缩态（14）：3456 ×7 / 1728 ×5 / 432 ×2 ----
		agri("compressed_wheat", "minecraft:wheat", 3456, "压缩小麦", "Compressed Wheat"),
		agri("compressed_carrot", "minecraft:carrot", 3456, "压缩胡萝卜", "Compressed Carrot"),
		agri("compressed_potato", "minecraft:potato", 3456, "压缩马铃薯", "Compressed Potato"),
		agri("compressed_sugar_cane", "minecraft:sugar_cane", 3456, "压缩甘蔗", "Compressed Sugar Cane"),
		agri("compressed_bamboo", "minecraft:bamboo", 3456, "压缩竹子", "Compressed Bamboo"),
		agri("compressed_melon_slice", "minecraft:melon_slice", 3456, "压缩西瓜片", "Compressed Melon Slice"),
		agri("compressed_kelp", "minecraft:kelp", 3456, "压缩海带", "Compressed Kelp"),
		agri("compressed_beetroot", "minecraft:beetroot", 1728, "压缩甜菜根", "Compressed Beetroot"),
		agri("compressed_pumpkin", "minecraft:pumpkin", 1728, "压缩南瓜", "Compressed Pumpkin"),
		agri("compressed_cocoa_beans", "minecraft:cocoa_beans", 1728, "压缩可可豆", "Compressed Cocoa Beans"),
		agri("compressed_sweet_berries", "minecraft:sweet_berries", 1728, "压缩甜浆果", "Compressed Sweet Berries"),
		agri("compressed_glow_berries", "minecraft:glow_berries", 1728, "压缩发光浆果", "Compressed Glow Berries"),
		agri("compressed_honey_bottle", "minecraft:honey_bottle", 432, "压缩蜂蜜瓶", "Compressed Honey Bottle"),
		agri("compressed_cod", "minecraft:cod", 432, "压缩生鳕鱼", "Compressed Raw Cod"),

		// ---- 矿物核心压缩态（11）：1728 ×9 / 432 ×2（全部为方块形态 + 下界合金锭）----
		mineral("compressed_iron_block", "minecraft:iron_block", 1728, "压缩铁块", "Compressed Iron Block"),
		mineral("compressed_gold_block", "minecraft:gold_block", 1728, "压缩金块", "Compressed Gold Block"),
		mineral("compressed_copper_block", "minecraft:copper_block", 1728, "压缩铜块", "Compressed Copper Block"),
		mineral("compressed_coal_block", "minecraft:coal_block", 1728, "压缩煤炭块", "Compressed Coal Block"),
		mineral("compressed_redstone_block", "minecraft:redstone_block", 1728, "压缩红石块", "Compressed Redstone Block"),
		mineral("compressed_lapis_block", "minecraft:lapis_block", 1728, "压缩青金石块", "Compressed Lapis Block"),
		mineral("compressed_quartz_block", "minecraft:quartz_block", 1728, "压缩石英块", "Compressed Quartz Block"),
		mineral("compressed_emerald_block", "minecraft:emerald_block", 1728, "压缩绿宝石块", "Compressed Emerald Block"),
		mineral("compressed_amethyst_block", "minecraft:amethyst_block", 1728, "压缩紫水晶块", "Compressed Amethyst Block"),
		mineral("compressed_diamond_block", "minecraft:diamond_block", 432, "压缩钻石块", "Compressed Diamond Block"),
		mineral("compressed_netherite_ingot", "minecraft:netherite_ingot", 432, "压缩下界合金锭", "Compressed Netherite Ingot"),

		// ---- 战斗核心压缩态（12）：3456 ×3 / 1728 ×4 / 432 ×4 / 256 ×1 ----
		combat("compressed_gunpowder", "minecraft:gunpowder", 3456, "压缩火药", "Compressed Gunpowder"),
		combat("compressed_bone", "minecraft:bone", 3456, "压缩骨头", "Compressed Bone"),
		combat("compressed_rotten_flesh", "minecraft:rotten_flesh", 3456, "压缩腐肉", "Compressed Rotten Flesh"),
		combat("compressed_string", "minecraft:string", 1728, "压缩线", "Compressed String"),
		combat("compressed_spider_eye", "minecraft:spider_eye", 1728, "压缩蜘蛛眼", "Compressed Spider Eye"),
		combat("compressed_slime_ball", "minecraft:slime_ball", 1728, "压缩粘液球", "Compressed Slimeball"),
		combat("compressed_prismarine_shard", "minecraft:prismarine_shard", 1728, "压缩海晶碎片", "Compressed Prismarine Shard"),
		combat("compressed_ender_pearl", "minecraft:ender_pearl", 432, "压缩末影珍珠", "Compressed Ender Pearl"),
		combat("compressed_blaze_rod", "minecraft:blaze_rod", 432, "压缩烈焰棒", "Compressed Blaze Rod"),
		combat("compressed_ghast_tear", "minecraft:ghast_tear", 432, "压缩恶魂之泪", "Compressed Ghast Tear"),
		combat("compressed_magma_cream", "minecraft:magma_cream", 432, "压缩岩浆膏", "Compressed Magma Cream"),
		combat("compressed_shulker_shell", "minecraft:shulker_shell", 256, "压缩潜影壳", "Compressed Shulker Shell"),
	)
}
