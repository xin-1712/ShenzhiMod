package com.xin.shenzhimod.interaction

import com.xin.shenzhimod.anvil.CoreEnchantDefaults
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.item.enchantment.EnchantmentHelper

/**
 * 本模组两个**数据驱动附魔**的注册表键与查询工具（模块 F/G/I 共用）。
 *
 * 附魔在 26.1 是数据驱动的动态注册表（数据包 `shenzhimod/enchantment` 目录下的 json），
 * 代码里拿不到静态常量，只能按 `ResourceKey` 比对——因此这里统一用一个按 ID 匹配的写法：
 * 既不需要查注册表，也不会因为注册表尚未加载而失败（客户端/服务端都安全）。
 */
object ModEnchantments {
	/** 不死图腾冷却（`shenzhimod:totem_cooldown`）。 */
	val TOTEM_COOLDOWN: ResourceKey<Enchantment> = keyOf(CoreEnchantDefaults.ENCHANT_TOTEM_COOLDOWN)

	/** 飞行（`shenzhimod:flight`）。 */
	val FLIGHT: ResourceKey<Enchantment> = keyOf(CoreEnchantDefaults.ENCHANT_FLIGHT)

	/** 物品是否带有指定附魔（等级 > 0）。 */
	fun has(stack: ItemStack, enchantment: ResourceKey<Enchantment>): Boolean {
		if (stack.isEmpty) return false
		for (holder in EnchantmentHelper.getEnchantmentsForCrafting(stack).keySet()) {
			if (holder.`is`(enchantment)) return true
			// 兜底：`Holder#is(ResourceKey)` 用的是 ResourceKey 的**引用相等**（原版靠 intern 保证），
			// 万一拿到的是未被 intern 的键，这里按 ID 再比一次，避免附魔"看不见"。
			if (holder.unwrapKey().map { it.identifier() == enchantment.identifier() }.orElse(false)) return true
		}
		return false
	}

	private fun keyOf(id: String): ResourceKey<Enchantment> =
		ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(id))
}
