package com.xin.shenzhimod.util

import com.xin.shenzhimod.ShenzhiMod
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack

/**
 * 物品 ID ↔ [ItemStack] 的转换工具。
 *
 * 模组的逻辑层（`machine/`）一律用字符串 ID 交流，只有这一层负责翻译成 Minecraft 类型，
 * 保证"纯逻辑可单测"这条工程护栏不被破坏。
 */
object ItemStacks {
	/** 取物品的 `namespace:path`；取不到时返回 null（例如空气或未注册物品）。 */
	fun idOf(stack: ItemStack): String? {
		if (stack.isEmpty) return null
		val key: Identifier = BuiltInRegistries.ITEM.getKey(stack.item)
		return key.toString()
	}

	/** 只有本模组的物品才返回其 path（压缩态白名单判定用）。 */
	fun modPathOf(stack: ItemStack): String? {
		if (stack.isEmpty) return null
		val key: Identifier = BuiltInRegistries.ITEM.getKey(stack.item)
		return if (key.namespace == ShenzhiMod.MOD_ID) key.path else null
	}

	/** 用 `namespace:path` 造一个物品堆；ID 非法或未注册时返回 [ItemStack.EMPTY]。 */
	fun of(id: String, count: Int = 1): ItemStack {
		if (count <= 0) return ItemStack.EMPTY
		val identifier = Identifier.tryParse(id) ?: return ItemStack.EMPTY
		val item = BuiltInRegistries.ITEM.getValue(identifier)
		if (item === net.minecraft.world.item.Items.AIR) return ItemStack.EMPTY
		return ItemStack(item, count)
	}

	/** 用本模组的 path（无命名空间）造一个物品堆。 */
	fun ofMod(path: String, count: Int = 1): ItemStack =
		of(Identifier.fromNamespaceAndPath(ShenzhiMod.MOD_ID, path).toString(), count)
}
