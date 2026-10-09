package com.xin.shenzhimod.datagen

import com.xin.shenzhimod.machine.CompressionDefaults
import com.xin.shenzhimod.registry.ModItems
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider
import net.minecraft.core.HolderLookup
import java.util.concurrent.CompletableFuture

/**
 * 中英双语语言文件。
 *
 * 文案集中两处：
 * - 物品/方块名：注册表 + 压缩表的 `nameEn` / `nameZh`（新增压缩态不用改这里）；
 * - 界面与提示：下面的 [Texts] 数据类（新增字段时编译器会强制两个语言都补齐）。
 */
class ModLanguageProvider(
	output: FabricPackOutput,
	private val languageCode: String,
	registries: CompletableFuture<HolderLookup.Provider>,
) : FabricLanguageProvider(output, languageCode, registries) {

	override fun generateTranslations(registryLookup: HolderLookup.Provider, builder: TranslationBuilder) {
		val english = languageCode == "en_us"
		val text = if (english) EN else ZH

		builder.add(ModItems.LIMESTONE, text.limestone)
		builder.add(ModItems.NETHER_STAR_SHARD, text.netherStarShard)
		builder.add(ModItems.AGRICULTURE_CORE, text.agricultureCore)
		builder.add(ModItems.MINERAL_CORE, text.mineralCore)
		builder.add(ModItems.COMBAT_CORE, text.combatCore)
		builder.add(ModItems.CREATION_CORE, text.creationCore)
		builder.add(ModItems.SYNTHETIC_DRAGON_HEAD, text.syntheticDragonHead)
		builder.add(ModItems.TRANSMUTATION_CORE, text.transmutationCore)
		builder.add(ModItems.ULTIMATE_ALTAR, text.ultimateAltar)

		for (entry in CompressionDefaults.ENTRIES) {
			val item = ModItems.COMPRESSED[entry.outputPath] ?: continue
			builder.add(item, if (english) entry.nameEn else entry.nameZh)
		}

		builder.add("itemGroup.shenzhimod.main", text.tabMain)
		builder.add("tooltip.shenzhimod.compressed", text.tooltipCompressed)
		builder.add("enchantment.shenzhimod.totem_cooldown", text.enchantTotemCooldown)
		builder.add("enchantment.shenzhimod.flight", text.enchantFlight)
		// 附魔描述：Enchantment Descriptions 这类客户端模组约定的键名（`enchantment.<ns>.<id>.desc`）。
		// 纯语言键、零代码零依赖——装了那个模组就自动显示，没装则完全无影响。
		builder.add("enchantment.shenzhimod.totem_cooldown.desc", text.enchantTotemCooldownDesc)
		builder.add("enchantment.shenzhimod.flight.desc", text.enchantFlightDesc)
		// 附魔等级的罗马数字（§8.2-V2）：原版只提供 1~10（level.10 = "X"），而本模组的
		// 锋利默认写到 17 级（配置 coreEnchant.sharpnessLevel），所以这里把 1~20 都补齐，
		// 保证"锋利 XVII"在任何语言下都显示成罗马数字，而不是裸的语言键。
		// 两种语言的取值相同（罗马数字），故不放进 Texts。
		for (level in 1..20) {
			builder.add("enchantment.level.$level", ROMAN_NUMERALS[level - 1])
		}
		builder.add("message.shenzhimod.dragon_head.status", text.dragonHeadStatus)
		builder.add("message.shenzhimod.dragon_head.crafting", text.dragonHeadCrafting)
		builder.add("message.shenzhimod.altar.idle", text.altarIdle)
		builder.add("message.shenzhimod.altar.progress", text.altarProgress)
		builder.add("message.shenzhimod.altar.started", text.altarStarted)
		builder.add("message.shenzhimod.altar.not_ready", text.altarNotReady)
		builder.add("message.shenzhimod.altar.wrong_cores", text.altarWrongCores)
		builder.add("message.shenzhimod.altar.base_invalid", text.altarBaseInvalid)

		// 成就：标题与描述成对出现，按"文件名 → 文案"的映射一次写完，避免 14 个散字段。
		val advancements = if (english) ADVANCEMENTS_EN else ADVANCEMENTS_ZH
		for ((file, advancement) in advancements) {
			builder.add("advancements.shenzhimod.$file.title", advancement.title)
			builder.add("advancements.shenzhimod.$file.description", advancement.description)
		}

		// JEI 分类标题（没有装 JEI 时这些键不会被读到，纯粹是可选依赖的显示名）。
		builder.add("jei.shenzhimod.category.compression", text.jeiCompression)
		builder.add("jei.shenzhimod.category.core_assembly", text.jeiCoreAssembly)
		builder.add("jei.shenzhimod.category.transmutation", text.jeiTransmutation)
		builder.add("jei.shenzhimod.info.anvil_cost", text.jeiAnvilCost)
		builder.add("jei.shenzhimod.target.enchantable.sharp_weapon", text.jeiTargetSharpWeapon)
		builder.add("jei.shenzhimod.target.enchantable.chest_armor", text.jeiTargetChestArmor)
		builder.add("jei.shenzhimod.target.enchantable.mining", text.jeiTargetMining)
		builder.add("jei.shenzhimod.target.enchantable.mining_loot", text.jeiTargetMiningLoot)
	}

	private data class Texts(
		val limestone: String,
		val netherStarShard: String,
		val agricultureCore: String,
		val mineralCore: String,
		val combatCore: String,
		val creationCore: String,
		val syntheticDragonHead: String,
		val transmutationCore: String,
		val ultimateAltar: String,
		val tabMain: String,
		val tooltipCompressed: String,
		val enchantTotemCooldown: String,
		val enchantFlight: String,
		val enchantTotemCooldownDesc: String,
		val enchantFlightDesc: String,
		val dragonHeadStatus: String,
		val dragonHeadCrafting: String,
		val altarIdle: String,
		val altarProgress: String,
		val altarStarted: String,
		val altarNotReady: String,
		val altarWrongCores: String,
		val altarBaseInvalid: String,
		val jeiCompression: String,
		val jeiCoreAssembly: String,
		val jeiTransmutation: String,
		val jeiAnvilCost: String,
		val jeiTargetSharpWeapon: String,
		val jeiTargetChestArmor: String,
		val jeiTargetMining: String,
		val jeiTargetMiningLoot: String,
	)

	/** 成就文案（标题 + 描述）。 */
	private data class AdvancementTexts(val title: String, val description: String)

	private companion object {
		/** 1~20 的罗马数字。 */
		val ROMAN_NUMERALS = listOf(
			"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X",
			"XI", "XII", "XIII", "XIV", "XV", "XVI", "XVII", "XVIII", "XIX", "XX",
		)

		val EN = Texts(
			limestone = "Limestone",
			netherStarShard = "Nether Star Shard",
			agricultureCore = "Agriculture Core",
			mineralCore = "Mineral Core",
			combatCore = "Combat Core",
			creationCore = "Creation Core",
			syntheticDragonHead = "Synthetic Dragon Head",
			transmutationCore = "Transmutation Core",
			ultimateAltar = "Ultimate Altar",
			tabMain = "ShenzhiMod",
			tooltipCompressed = "Compressed from %s x %s",
			enchantTotemCooldown = "Cooldown",
			enchantFlight = "Flight",
			enchantTotemCooldownDesc = "Triggers death protection without consuming the totem, then puts every enchanted totem on a 60-second cooldown.",
			enchantFlightDesc = "Lets the wearer fly like in creative mode (double-tap jump). Gliding still works, and flying wears the elytra down.",
			dragonHeadStatus = "Stored %s / %s kinds of compressed items",
			dragonHeadCrafting = "Crafting %s (progress %s)",
			altarIdle = "Altar idle: throw the three cores onto the base, then sneak-right-click",
			altarProgress = "Fusing: %s / %s cores absorbed",
			altarStarted = "Fusion started",
			altarNotReady = "The three cores are not ready yet",
			altarWrongCores = "Needs exactly one Agriculture + one Mineral + one Combat Core, with no other items nearby",
			altarBaseInvalid = "The 3x3 netherite base is incomplete",
			jeiCompression = "Anvil Compression",
			jeiCoreAssembly = "Dragon Head Core Assembly",
			jeiTransmutation = "Transmutation",
			jeiAnvilCost = "Fixed cost: %s levels",
			jeiTargetSharpWeapon = "Sword / Spear / Axe",
			jeiTargetChestArmor = "Chestplate",
			jeiTargetMining = "Pickaxe / Axe / Shovel / Hoe / Shears",
			jeiTargetMiningLoot = "Pickaxe / Axe / Shovel / Hoe",
		)

		val ZH = Texts(
			limestone = "石灰岩",
			netherStarShard = "下界之星碎片",
			agricultureCore = "农业核心",
			mineralCore = "矿物核心",
			combatCore = "战斗核心",
			creationCore = "创世核心",
			syntheticDragonHead = "合成龙首",
			transmutationCore = "转化核心",
			ultimateAltar = "终极祭坛",
			tabMain = "深之模组",
			tooltipCompressed = "由 %s 个 %s 压缩而成",
			enchantTotemCooldown = "冷却",
			enchantFlight = "飞行",
			enchantTotemCooldownDesc = "触发免死时不消耗图腾，随后让身上所有带本附魔的图腾进入 60 秒冷却。",
			enchantFlightDesc = "穿戴后可以像创造模式一样飞行（双击空格起飞）；滑翔照常可用，飞行会消耗鞘翅耐久。",
			dragonHeadStatus = "已吸收 %s / %s 种压缩态",
			dragonHeadCrafting = "正在合成 %s（进度 %s）",
			altarIdle = "祭坛待机：把三核心扔到底座上，再 Shift+右键启动",
			altarProgress = "融合中：已吸收 %s / %s 个核心",
			altarStarted = "融合已启动",
			altarNotReady = "三核心尚未就位",
			altarWrongCores = "需要农业 / 矿物 / 战斗核心各一个，且附近不能有其它物品",
			altarBaseInvalid = "下方 3×3 下界合金底座不完整",
			jeiCompression = "铁砧压缩",
			jeiCoreAssembly = "龙首核心合成",
			jeiTransmutation = "转化核心",
			jeiAnvilCost = "固定消耗 %s 级经验",
			jeiTargetSharpWeapon = "剑 / 矛 / 斧",
			jeiTargetChestArmor = "胸甲",
			jeiTargetMining = "镐 / 斧 / 锹 / 锄 / 剪刀",
			jeiTargetMiningLoot = "镐 / 斧 / 锹 / 锄",
		)

		val ADVANCEMENTS_EN = mapOf(
			"root" to AdvancementTexts(
				title = "Deep in the Craft",
				description = "It all begins with a lump of limestone.",
			),
			"compression" to AdvancementTexts(
				title = "Brute Force",
				description = "The mace falls and the anvil rings: you hear the sound of \"compression\" for the first time.",
			),
			"core_agriculture" to AdvancementTexts(
				title = "Down to Earth",
				description = "The earth yields up its bounty, and the Agriculture Core grows warm in your hand.",
			),
			"core_mineral" to AdvancementTexts(
				title = "Born with a Silver Spoon",
				description = "Ore and metal bow down: the Mineral Core acknowledges you as its master.",
			),
			"core_combat" to AdvancementTexts(
				title = "Armed to the Teeth",
				description = "The blade remembers your name, and the Combat Core yearns for the next fight.",
			),
			"altar_base" to AdvancementTexts(
				title = "Well Grounded",
				description = "Nine blocks of netherite sink into the ground, and the sleeping altar opens its eyes.",
			),
			"creation_core" to AdvancementTexts(
				title = "Destiny Strikes",
				description = "Lightning pierces the altar, a Creation Core is born in the thunder - and destiny finally bows to you.",
			),
			"elytra_flight" to AdvancementTexts(
				title = "The Sky Is Not the Limit",
				description = "Bind flight into your wings - from now on, the sky is yours to cross.",
			),
		)

		val ADVANCEMENTS_ZH = mapOf(
			"root" to AdvancementTexts(
				title = "深之模组",
				description = "一切从一块石灰岩开始。",
			),
			"compression" to AdvancementTexts(
				title = "大力出奇迹",
				description = "重锤落下，铁砧轰鸣——你第一次听见了「压缩」的声音。",
			),
			"core_agriculture" to AdvancementTexts(
				title = "汗滴禾下土",
				description = "大地把丰饶交到你手里，农业核心在掌心微微发烫。",
			),
			"core_mineral" to AdvancementTexts(
				title = "家里有矿",
				description = "矿石与金属俯首称臣，矿物核心认你为主。",
			),
			"core_combat" to AdvancementTexts(
				title = "武德充沛",
				description = "刀锋记住了你的名字，战斗核心渴望着下一场厮杀。",
			),
			"altar_base" to AdvancementTexts(
				title = "地基打得牢",
				description = "九块下界合金嵌入大地，沉睡的祭坛睁开了眼睛。",
			),
			"creation_core" to AdvancementTexts(
				title = "天命所归",
				description = "雷光贯穿祭坛，创世核心在雷声中诞生——命运终于向你低头。",
			),
			"elytra_flight" to AdvancementTexts(
				title = "天空不再是极限",
				description = "将飞行烙进羽翼——从此天空任你穿行。",
			),
		)
	}
}
