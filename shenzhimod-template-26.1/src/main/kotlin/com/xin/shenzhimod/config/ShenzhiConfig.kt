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
	/**
	 * 锋利写入的等级（可超过原版上限 5）。
	 *
	 * 默认 **17**，目标是"下界合金剑 + 跳劈（暴击）一击带走 20 血的无装备普通僵尸"。推算过程（26.1 实测公式）：
	 * - 下界合金剑攻击力 = 1（玩家基础）+ 4（下界合金加成）+ 3（剑本身）= **8**；
	 * - 暴击只放大**基础**伤害（×1.5 → 12），锋利加成走 `magicBoost`、**不**被暴击放大；
	 * - 锋利伤害加成 = `0.5 × 等级 + 0.5`（附魔 JSON 的 linear：base 1.0 / per_level_above_first 0.5）；
	 * - 蓄力满但未到 100% 时 `attackStrengthScale` 略高于 0.9，取最坏值 0.9：
	 *   `12 + 0.9 × (0.5 × 17 + 0.5) = 12 + 8.1 = 20.1 ≥ 20` ✔（16 级只有 19.65，不够稳）。
	 */
	var sharpnessLevel: Int = 17,
	/** 效率写入的等级（可超过原版上限 5）。原版加成随等级平方增长，默认维持 10。 */
	var efficiencyLevel: Int = 10,
	/** 时运写入的等级（可超过原版上限 3）。默认维持 10。 */
	var fortuneLevel: Int = 10,
	/**
	 * 保护写入的等级（可超过原版上限 4），**只对胸甲生效**（`#minecraft:enchantable/chest_armor`）。
	 *
	 * 原版机制（26.1 源码核实）：
	 * - 保护提供的减伤点数为 `等级 × 1`（附魔 JSON 的 `damage_protection` linear：base 1 / per_level_above_first 1）；
	 * - 所有护甲上的保护点数**求和后统一封顶 20**，再按 `1 - 点数 / 25` 乘算（`CombatRules.getDamageAfterMagicAbsorb`），
	 *   也就是**最高 80% 减伤，超过 20 级的点数完全浪费**；
	 * - 换算：5 级 = 20%、8 级 = 32%、12 级 = 48%、16 级 = 64%（= 原版四件保护 IV 的总和）、20 级 = 80%（硬上限）。
	 *
	 * 默认 **12**：单件胸甲即 48% 减伤（约等于原版三件保护 IV），
	 * 和胸甲之外的原版保护 IV（3 件 = 12 点）叠加后刚好贴近封顶，
	 * 既明显强于原版、又不会把护甲附魔体系整体作废。想更暴力直接调到 16~20。
	 */
	var protectionLevel: Int = 12,
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
	/*
	 * 曾经的 `disableElytraGlideWhileFlying` 已删除（2026-09-29）：
	 * 滑翔**始终允许**，与创造模式一致（既能双击空格飞行，也能在空中按一次空格开始滑翔）。
	 * 之所以彻底去掉开关而不是留个默认 false 的字段：配置是"深度合并"的，
	 * 旧配置文件里残留的 `true` 会一直生效，反而让人以为改动没生效。
	 */
	/** 飞行时是否消耗鞘翅耐久（与原版滑翔一致；创造模式因"无限材料"天然不消耗）。 */
	var consumeElytraDurability: Boolean = true,
	/**
	 * 飞行时每多少 tick 消耗 1 点鞘翅耐久。
	 *
	 * 默认 **20 tick = 1 秒/点**，与原版滑翔的消耗速率完全一致：
	 * 432 点耐久的鞘翅 ≈ **连续飞行 7.2 分钟**；附魔耐久 III 大约延长到 4 倍（约 28.8 分钟）；
	 * 经验修补会在拾取经验时正常修复（与滑翔一样，走原版耐久流程）。
	 */
	var durabilityTicksPerDamage: Int = 20,
)

/** 客户端表现（粒子/音效）开关。 */
data class VisualSection(
	/** 全局粒子开关。 */
	var particlesEnabled: Boolean = true,
	/** 粒子密度倍率，0.0~1.0。 */
	var particleDensity: Double = 0.5,
	/** 全局机器音效开关。 */
	var soundsEnabled: Boolean = true,
	/**
	 * 下界之星碎片碎裂粒子颜色（`RRGGBB`，可带 `#`）。
	 *
	 * 默认 `E0D5FF`：贴近下界之星的"白中带紫"。改错了会回退到该默认值并写一条警告日志。
	 */
	var netherStarShardParticleColor: String = "E0D5FF",
	/**
	 * 穿飞行附魔鞘翅**飞行时**（`abilities.flying`，与滑翔无关）的鞘翅扇动动画开关。
	 *
	 * 实现方式是客户端改写玩家渲染状态里的鞘翅翼角（原版 `ElytraModel` 会读它），
	 * 让翅膀持续做小幅摆动——只影响表现，纯客户端，不参与判定。
	 */
	var elytraFlightAnimation: Boolean = true,
	/** 飞行时是否在鞘翅两侧拖出粒子尾迹（跟随翅膀摆动，同样只在"飞行"状态触发）。 */
	var elytraFlightTrailParticles: Boolean = true,
)
