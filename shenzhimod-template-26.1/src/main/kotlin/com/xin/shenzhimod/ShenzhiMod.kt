package com.xin.shenzhimod

import com.xin.shenzhimod.config.ConfigManager
import com.xin.shenzhimod.config.ShenzhiConfig
import com.xin.shenzhimod.data.ShenzhiTables
import com.xin.shenzhimod.entity.PiglinBruteSpawnRules
import com.xin.shenzhimod.interaction.AnvilCompressionHandler
import com.xin.shenzhimod.loot.ModLootTables
import com.xin.shenzhimod.registry.ModBlockEntities
import com.xin.shenzhimod.registry.ModBlocks
import com.xin.shenzhimod.registry.ModCreativeTabs
import com.xin.shenzhimod.registry.ModItems
import com.xin.shenzhimod.registry.ModSounds
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.resource.v1.ResourceLoader
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import org.slf4j.LoggerFactory

/**
 * 模组服务端主入口。
 *
 * 初始化顺序是刻意固定的（注册之间存在依赖，必须先于任何访问完成）：
 * 配置 → 数据表 → 方块 → 物品（含方块物品）→ 方块实体 → 音效 → 创造栏 → 事件/数据包接线。
 */
object ShenzhiMod : ModInitializer {
	const val MOD_ID: String = "shenzhimod"

	val logger = LoggerFactory.getLogger(MOD_ID)!!

	/** 全局配置快照；只在启动时读取一次，运行中不会变。 */
	val config: ShenzhiConfig
		get() = ConfigManager.config

	override fun onInitialize() {
		ConfigManager.load()
		ShenzhiTables.rebuildFromConfig(config)

		ModBlocks.bootstrap()
		ModItems.bootstrap()
		ModBlockEntities.bootstrap()
		ModSounds.ALL
		ModCreativeTabs.bootstrap()

		registerDataTableReloader()
		AnvilCompressionHandler.register()
		ModLootTables.register()
		PiglinBruteSpawnRules.register()

		logger.info(
			"ShenzhiMod 初始化完成：{} 个物品 / {} 个方块 / {} 条压缩配方",
			ModItems.ALL.size,
			ModBlocks.ALL.size,
			ShenzhiTables.compression.entries.size,
		)
	}

	/**
	 * 让数据包可以覆盖压缩表 / 转化表：
	 * `data/shenzhimod/shenzhi_table/` 下 compression 与 transmutation 两个子目录里的 json 文件。
	 */
	private fun registerDataTableReloader() {
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(
			id("shenzhi_tables"),
			ResourceManagerReloadListener { manager -> ShenzhiTables.applyResourceOverrides(manager) },
		)
	}

	fun id(path: String): Identifier
		= Identifier.fromNamespaceAndPath(MOD_ID, path)
}
