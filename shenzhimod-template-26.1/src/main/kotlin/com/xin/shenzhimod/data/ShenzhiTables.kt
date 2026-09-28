package com.xin.shenzhimod.data

import com.google.gson.JsonParser
import com.xin.shenzhimod.ShenzhiMod
import com.xin.shenzhimod.config.ShenzhiConfig
import com.xin.shenzhimod.machine.CompressionDefaults
import com.xin.shenzhimod.machine.CompressionEntry
import com.xin.shenzhimod.machine.CompressionGroup
import com.xin.shenzhimod.machine.CompressionTable
import com.xin.shenzhimod.machine.CoreAssemblyDefaults
import com.xin.shenzhimod.machine.CoreAssemblyRecipe
import com.xin.shenzhimod.machine.TransmutationDefaults
import com.xin.shenzhimod.machine.TransmutationEntry
import com.xin.shenzhimod.machine.TransmutationTable
import net.minecraft.server.packs.resources.Resource
import net.minecraft.server.packs.resources.ResourceManager
import java.io.Reader

/**
 * 运行时数据表：压缩表 / 转化表 / 核心合成表。
 *
 * 三层来源，优先级从低到高：
 * 1. 代码里的默认表（[CompressionDefaults] 等，唯一"可写死"的地方，且集中在一处）；
 * 2. **数据包覆盖**：`data/shenzhimod/shenzhi_table/` 下各子目录里的 json 文件，
 *    由资源重载（进入世界 / `/reload`）时读取并整体替换默认表；
 * 3. 配置项（`config/shenzhimod.json`）控制时长、容量等运行参数。
 *
 * 这样服务器管理员可以只改数据包调整配方，不需要重新编译模组。
 */
object ShenzhiTables {
	private const val COMPRESSION_DIR = "shenzhi_table/compression"
	private const val TRANSMUTATION_DIR = "shenzhi_table/transmutation"

	@Volatile
	var compression: CompressionTable = CompressionTable(CompressionDefaults.ENTRIES)
		private set

	@Volatile
	var transmutation: TransmutationTable = TransmutationTable(TransmutationDefaults.build(200))
		private set

	@Volatile
	var assembly: List<CoreAssemblyRecipe> = CoreAssemblyDefaults.build(compression)
		private set

	/** 用配置重建默认表（模组初始化时调用一次）。 */
	fun rebuildFromConfig(config: ShenzhiConfig) {
		compression = CompressionTable(CompressionDefaults.ENTRIES)
		transmutation = TransmutationTable(TransmutationDefaults.build(config.transmutation.defaultDurationTicks))
		assembly = CoreAssemblyDefaults.build(compression)
		logProblems()
	}

	/** 资源重载时用数据包内容覆盖默认表；任何解析失败都会保留默认值并记录日志。 */
	fun applyResourceOverrides(manager: ResourceManager) {
		try {
			val compressionEntries = readCompression(manager)
			if (compressionEntries.isNotEmpty()) {
				compression = CompressionTable(compressionEntries)
				ShenzhiMod.logger.info("已从数据包载入 {} 条压缩配方", compressionEntries.size)
			}
			val transmutationEntries = readTransmutation(manager)
			if (transmutationEntries.isNotEmpty()) {
				transmutation = TransmutationTable(transmutationEntries)
				ShenzhiMod.logger.info("已从数据包载入 {} 条转化配方", transmutationEntries.size)
			}
			assembly = CoreAssemblyDefaults.build(compression)
			logProblems()
		} catch (error: Exception) {
			ShenzhiMod.logger.error("载入数据包数据表失败，继续使用默认表", error)
		}
	}

	private fun logProblems() {
		transmutation.validate().forEach { ShenzhiMod.logger.warn("转化表自检：{}", it) }
	}

	private fun readCompression(manager: ResourceManager): List<CompressionEntry> {
		val entries = mutableListOf<CompressionEntry>()
		loadJson(manager, COMPRESSION_DIR) { json ->
			val array = json.asJsonObject.getAsJsonArray("entries") ?: return@loadJson
			for (element in array) {
				val obj = element.asJsonObject
				entries += CompressionEntry(
					outputPath = obj.get("output").asString,
					sourceItemId = obj.get("source").asString,
					count = obj.get("count").asInt,
					group = CompressionGroup.valueOf(obj.get("group").asString.uppercase()),
					nameEn = obj.get("name_en")?.asString ?: obj.get("output").asString,
					nameZh = obj.get("name_zh")?.asString ?: obj.get("output").asString,
				)
			}
		}
		return entries
	}

	private fun readTransmutation(manager: ResourceManager): List<TransmutationEntry> {
		val entries = mutableListOf<TransmutationEntry>()
		loadJson(manager, TRANSMUTATION_DIR) { json ->
			val array = json.asJsonObject.getAsJsonArray("entries") ?: return@loadJson
			for (element in array) {
				val obj = element.asJsonObject
				entries += TransmutationEntry(
					id = obj.get("id").asString,
					inputItemId = obj.get("input").asString,
					inputCount = obj.get("input_count").asInt,
					catalystItemId = obj.get("catalyst").asString,
					catalystCount = obj.get("catalyst_count").asInt,
					outputItemId = obj.get("output").asString,
					outputCount = obj.get("output_count").asInt,
					durationTicks = obj.get("duration_ticks")?.asInt ?: 200,
				)
			}
		}
		return entries
	}

	private fun loadJson(manager: ResourceManager, directory: String, consumer: (com.google.gson.JsonElement) -> Unit) {
		val resources: Map<net.minecraft.resources.Identifier, Resource> =
			manager.listResources(directory) { path -> path.path.endsWith(".json") }
		resources.forEach { (id, resource) ->
			resource.openAsReader().use { reader: Reader ->
				consumer(JsonParser.parseReader(reader))
			}
			ShenzhiMod.logger.debug("已读取数据表 {}", id)
		}
	}
}
