package com.xin.shenzhimod.config

/**
 * ShenzhiMod 的全部可调参数。
 *
 * 设计约束：
 * - 代码里**不允许**出现玩法数值字面量；一切数值都从这里读。
 * - 每个字段都必须有合理默认值，缺省的配置文件也能正常运行。
 * - 新增字段时同步更新 README 与 CHANGELOG。
 *
 * 这个类刻意保持"纯数据"（无 Minecraft 类型），方便单元测试与将来做 GUI 配置界面。
 */
data class ShenzhiConfig(
	var piglinBrute: PiglinBruteSection = PiglinBruteSection(),
	var compression: CompressionSection = CompressionSection(),
	var dragonHead: DragonHeadSection = DragonHeadSection(),
	var transmutation: TransmutationSection = TransmutationSection(),
	var altar: AltarSection = AltarSection(),
	var coreEnchant: CoreEnchantSection = CoreEnchantSection(),
	var totem: TotemSection = TotemSection(),
	var flight: FlightSection = FlightSection(),
	var visual: VisualSection = VisualSection(),
)

/** 模块 J：堡垒遗迹猪灵蛮兵刷怪（与李芒果空岛完全一致）。 */
data class PiglinBruteSection(
	/** 是否在堡垒遗迹刷猪灵蛮兵/猪灵/疣猪兽。关闭后谓词直接拒绝生成，等价于放空刷怪。 */
	var spawningInBastions: Boolean = true,
	/** 远古残骸基础掉落概率（空岛数值 5%）。 */
	var ancientDebrisBaseChance: Double = 0.05,
	/**
	 * 每高一级抢夺额外增加的概率（空岛数值 +1%）。
	 * 实际概率 = 基础值 + 等级 × 该值，因此 I 级 6%、II 级 7%、III 级 8%（§6.8.5）。
	 */
	var ancientDebrisChancePerLootingLevel: Double = 0.01,
	/** 是否要求玩家击杀才掉远古残骸（空岛为 true）。 */
	var ancientDebrisRequiresPlayerKill: Boolean = true,
)

/** 模块 A：压缩体系。 */
data class CompressionSection(
	/**
	 * 一次 Shift+右键最多压缩出多少个产物。
	 * 仅作为安全阀；`0` 表示不限制（受重锤耐久与世界物品数天然限制）。
	 */
	var maxOutputsPerInteraction: Int = 0,
	/** 每产出 1 个压缩态消耗的重锤耐久。 */
	var durabilityCostPerOutput: Int = 1,
	/** 是否允许在受损铁砧（chipped/damaged anvil）上压缩。 */
	var allowDamagedAnvil: Boolean = true,
)

/** 模块 B：合成龙首（吸收账本）。 */
data class DragonHeadSection(
	/** 吸收扫描间隔（tick）。 */
	var absorbIntervalTicks: Int = 5,
	/** 扫描半径（方块）。3×3×3 对应 1。 */
	var absorbRadiusBlocks: Int = 1,
	/** 内部账本容量（按"压缩态种类"计，§8-G15 确认为 14）。 */
	var ledgerCapacity: Int = 14,
	/** 匹配配方后的合成时长（tick）。 */
	var craftDurationTicks: Int = 100,
	/** 非充能状态下吐回物品时的弹出速度。 */
	var ejectVelocity: Double = 0.25,
	/** 吐出物在离开吸收范围前是否免疫再次吸收（§8-G6 建议）。 */
	var markEjectedItems: Boolean = true,
)

/** 模块 C：转化核心。 */
data class TransmutationSection(
	/** 默认配方处理时间（tick），数据表未指定时使用。 */
	var defaultDurationTicks: Int = 200,
	/** 是否允许漏斗从上方输入 / 侧面催化剂 / 下方抽取输出。 */
	var hopperInteractionEnabled: Boolean = true,
	/** 最大同时运行台数提示值（仅用于性能自检日志，不参与逻辑）。 */
	var expectedConcurrentMachines: Int = 10,
)

/** 模块 E：终极祭坛。 */
data class AltarSection(
	/** 融合每一段吸收时长（tick）。三段合计 = 600 tick = **30 秒**（定案值）。 */
	var absorbStageTicks: Int = 200,
	/** 需要的核心数量。 */
	var requiredCores: Int = 3,
	/** 底座边长方块数（3 表示 3×3）。 */
	var baseSize: Int = 3,
	/** 底座方块（下界合金块）。 */
	var baseBlockId: String = "minecraft:netherite_block",
	/** 已就绪的核心在祭坛附近是否免疫 5 分钟消失（§4.5 风险 1）。 */
	var protectReadyCoresFromDespawn: Boolean = true,
	/** 中断时是否落雷（落雷是否造成真实效果由 [realLightning] 决定）。 */
	var strikeLightningOnAbort: Boolean = true,
	/**
	 * 落雷是否造成**真实效果**（伤害实体 + 在落点引燃）。
	 * 默认 `true`（实测反馈：雷电只有特效不够）；改成 `false` 则退回纯视觉落雷。
	 */
	var realLightning: Boolean = true,
	/** 融合产物。 */
	var outputItemId: String = "shenzhimod:creation_core",
	/** 产物数量。 */
	var outputCount: Int = 1,
)

/** 模块 F：核心附魔（铁砧）。 */
data class CoreEnchantSection(
	/** 三核心附魔的固定经验等级成本。 */
	var coreCostLevels: Int = 100,
	/** 创世核心附魔的固定经验等级成本。 */
	var creationCoreCostLevels: Int = 200,
	/** 写入的附魔等级（可超过原版上限）。 */
	var grantedLevel: Int = 10,
	/** 创造模式是否消耗媒介（§8-G12：消耗核心、不消耗经验）。 */
	var creativeConsumesCore: Boolean = true,
	/** 创造模式是否消耗经验（§8-G12：不消耗）。 */
	var creativeConsumesLevels: Boolean = false,
	/** 是否遵守原版互斥附魔（§8-G9：遵守）。 */
	var respectVanillaExclusivity: Boolean = true,
	/** 是否在结果物品上写入 repair_cost（不得开启，否则物品会永久"过于昂贵"）。 */
	var writeRepairCost: Boolean = false,
)

/** 模块 G：不死图腾冷却。 */
data class TotemSection(
	/** 冷却时长（秒）。 */
	var cooldownSeconds: Int = 60,
	/** 冷却组 ID，同组图腾共享冷却。 */
	var cooldownGroupId: String = "shenzhimod:enchanted_totem",
	/** 附魔图腾触发时是否消耗物品（需求：不消耗）。 */
	var consumeOnTrigger: Boolean = false,
)

/** 模块 I：鞘翅飞行。 */
data class FlightSection(
	/** 总倍率，供服务器整体调整手感。 */
	var speedScale: Double = 1.0,
	/**
	 * 非疾跑飞行速度基准：**等于原版生存走路速度**。
	 *
	 * 换算依据（需实机校准，见文档 V12）：
	 * - 原版创造飞行的 `Abilities.flyingSpeed` 默认 0.05 → 约 10.89 格/秒；
	 * - 生存走路 4.317 格/秒（`MOVEMENT_SPEED` 0.1），疾跑 5.612 格/秒（×1.3）；
	 * - 水平速度与 `flyingSpeed` 成正比，故 0.05 × 4.317 / 10.89 ≈ 0.0198。
	 */
	var baseFlyingSpeed: Double = 0.0198,
	/** 疾跑飞行速度基准：等于原版生存疾跑速度，0.05 × 5.612 / 10.89 ≈ 0.0258。 */
	var sprintFlyingSpeed: Double = 0.0258,
	/** 是否把移动速度属性与速度类状态效果计入飞行速度。 */
	var applyMovementSpeedAttributes: Boolean = true,
	/** 是否在穿戴飞行鞘翅时禁用原版滑翔，避免"滑翔/飞行"抖动。 */
	var disableElytraGlideWhileFlying: Boolean = true,
)

/** 客户端表现（粒子/音效）开关。 */
data class VisualSection(
	/** 全局粒子开关。 */
	var particlesEnabled: Boolean = true,
	/** 粒子密度倍率，0.0~1.0。 */
	var particleDensity: Double = 0.5,
	/** 全局机器音效开关。 */
	var soundsEnabled: Boolean = true,
)
