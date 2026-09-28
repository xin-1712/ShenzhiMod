package com.xin.shenzhimod.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.xin.shenzhimod.ShenzhiMod
import net.fabricmc.loader.api.FabricLoader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * JSON 配置文件读写（`config/shenzhimod.json`）。
 *
 * 不依赖任何第三方配置库：只用到 Minecraft 自带的 Gson，避免给使用者增加前置 mod。
 *
 * 读取策略：先把用户文件与"默认值 JSON"做深度合并再反序列化。
 * 这样新增字段、删除字段、用户只写了一半的配置都不会导致崩溃或"字段为 null"。
 */
object ConfigManager {
	private const val FILE_NAME = "shenzhimod.json"

	private val gson: Gson = GsonBuilder()
		.setPrettyPrinting()
		.disableHtmlEscaping()
		.create()

	@Volatile
	var config: ShenzhiConfig = ShenzhiConfig()
		private set

	val configPath: Path
		get() = FabricLoader.getInstance().configDir.resolve(FILE_NAME)

	/** 载入配置；文件不存在或损坏时回退到默认值并写出模板。 */
	fun load() {
		val path = configPath
		config = try {
			if (Files.exists(path)) {
				val text = Files.readString(path, StandardCharsets.UTF_8)
				fromJson(text)
			} else {
				ShenzhiConfig().also { writeFile(path, it) }
			}
		} catch (error: Exception) {
			ShenzhiMod.logger.error("读取配置失败，已回退到默认值：{}", path, error)
			ShenzhiConfig()
		}
	}

	/** 把当前配置写回磁盘（原子替换）。 */
	fun save() {
		writeFile(configPath, config)
	}

	/** 供命令/测试使用的"从字符串解析"，同样走默认值合并。 */
	@JvmStatic
	fun fromJson(text: String): ShenzhiConfig {
		val defaults = gson.toJsonTree(ShenzhiConfig())
		val parsed = JsonParser.parseString(text)
		val merged = deepMerge(defaults, parsed)
		return gson.fromJson(merged, ShenzhiConfig::class.java)
	}

	/** 供命令/测试使用的"序列化"。 */
	@JvmStatic
	fun toJson(config: ShenzhiConfig): String = gson.toJson(config)

	private fun writeFile(path: Path, value: ShenzhiConfig) {
		try {
			Files.createDirectories(path.parent)
			val temp = path.resolveSibling("$FILE_NAME.tmp")
			Files.writeString(temp, toJson(value), StandardCharsets.UTF_8)
			Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING)
		} catch (error: Exception) {
			ShenzhiMod.logger.error("写出配置失败：{}", path, error)
		}
	}

	/**
	 * 深度合并：以 [base] 为骨架，用 [override] 里出现的键覆盖它。
	 * 缺失的键保留默认值，多余的键保留原样（写回时不会丢用户自定义字段）。
	 */
	private fun deepMerge(base: JsonElement, override: JsonElement): JsonElement {
		if (!base.isJsonObject || !override.isJsonObject) {
			return override
		}
		val result = JsonObject()
		val baseObject = base.asJsonObject
		val overrideObject = override.asJsonObject
		for (key in baseObject.keySet()) {
			val overrideValue = overrideObject.get(key)
			result.add(key, if (overrideValue == null) baseObject.get(key) else deepMerge(baseObject.get(key), overrideValue))
		}
		for (key in overrideObject.keySet()) {
			if (!baseObject.has(key)) {
				result.add(key, overrideObject.get(key))
			}
		}
		return result
	}
}
