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

		builder.add(ModItems.CALCITE, text.calcite)
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
		builder.add("message.shenzhimod.dragon_head.status", text.dragonHeadStatus)
		builder.add("message.shenzhimod.dragon_head.crafting", text.dragonHeadCrafting)
		builder.add("message.shenzhimod.altar.idle", text.altarIdle)
		builder.add("message.shenzhimod.altar.progress", text.altarProgress)
		builder.add("message.shenzhimod.altar.started", text.altarStarted)
		builder.add("message.shenzhimod.altar.not_ready", text.altarNotReady)
		builder.add("message.shenzhimod.altar.base_invalid", text.altarBaseInvalid)
	}

	private data class Texts(
		val calcite: String,
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
		val dragonHeadStatus: String,
		val dragonHeadCrafting: String,
		val altarIdle: String,
		val altarProgress: String,
		val altarStarted: String,
		val altarNotReady: String,
		val altarBaseInvalid: String,
	)

	private companion object {
		val EN = Texts(
			calcite = "Calcite",
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
			enchantTotemCooldown = "Totem Cooldown",
			enchantFlight = "Flight",
			dragonHeadStatus = "Stored %s / %s kinds of compressed items",
			dragonHeadCrafting = "Crafting %s (progress %s)",
			altarIdle = "Altar idle: throw the three cores onto the base, then sneak-right-click",
			altarProgress = "Fusing: %s / %s cores absorbed",
			altarStarted = "Fusion started",
			altarNotReady = "The three cores are not ready yet",
			altarBaseInvalid = "The 3x3 netherite base is incomplete",
		)

		val ZH = Texts(
			calcite = "方解石",
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
			enchantTotemCooldown = "不死图腾冷却",
			enchantFlight = "飞行",
			dragonHeadStatus = "已吸收 %s / %s 种压缩态",
			dragonHeadCrafting = "正在合成 %s（进度 %s）",
			altarIdle = "祭坛待机：把三核心扔到底座上，再 Shift+右键启动",
			altarProgress = "融合中：已吸收 %s / %s 个核心",
			altarStarted = "融合已启动",
			altarNotReady = "三核心尚未就位",
			altarBaseInvalid = "下方 3×3 下界合金底座不完整",
		)
	}
}
