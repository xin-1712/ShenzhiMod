package com.xin.shenzhimod.datagen

import com.google.common.hash.Hashing
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.xin.shenzhimod.ShenzhiMod
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput
import net.minecraft.data.CachedOutput
import net.minecraft.data.DataProvider
import java.security.MessageDigest
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.CompletableFuture

/**
 * 直接写 JSON 的数据生成基类。
 *
 * 为什么不用原版 `ItemModelProvider` 之类的 DSL：26.1 的模型生成 API
 * （`ItemModelOutput` / `ModelInstance`）与配方 builder 仍在演进，而我们要写的东西形状非常固定。
 * 直接产出**与 26.1 数据格式一致**的 JSON 更稳、更好 review，也不会被上游重构打断。
 *
 * 路径都是相对数据包/资源包根目录的，例如 `assets/shenzhimod/items/creation_core.json`。
 */
abstract class JsonDataProvider(
	private val packOutput: FabricPackOutput,
	private val providerName: String,
) : DataProvider {

	/** 返回"相对路径 → JSON 内容"。 */
	protected abstract fun buildEntries(): Map<String, JsonElement>

	override fun run(cache: CachedOutput): CompletableFuture<*> {
		val root = packOutput.outputFolder
		for ((relative, json) in buildEntries()) {
			val path = root.resolve(relative)
			try {
				Files.createDirectories(path.parent)
				val bytes = GSON.toJson(json).toByteArray(StandardCharsets.UTF_8)
				cache.writeIfNeeded(path, bytes, sha1(bytes))
			} catch (error: Exception) {
				ShenzhiMod.logger.error("写出数据文件失败：{}", path, error)
			}
		}
		return CompletableFuture.completedFuture(null)
	}

	override fun getName(): String = providerName

	companion object {
		val GSON = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

		/** 用 JDK 自带的 SHA-1 计算内容哈希，避免依赖 Guava 的弃用 API。 */
		private fun sha1(bytes: ByteArray): com.google.common.hash.HashCode =
			com.google.common.hash.HashCode.fromBytes(MessageDigest.getInstance("SHA-1").digest(bytes))
	}
}
