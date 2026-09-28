package com.xin.shenzhimod.registry

import com.xin.shenzhimod.ShenzhiMod
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack

/**
 * 创造模式物品栏。
 *
 * 模组物品全部收进一个自建分类，避免污染原版分类；
 * 分类标题走语言键，由数据生成提供中英文本。
 */
object ModCreativeTabs {
	val MAIN_KEY: ResourceKey<CreativeModeTab> =
		ResourceKey.create(Registries.CREATIVE_MODE_TAB, ModRegistry.id("main"))

	val MAIN: CreativeModeTab = FabricCreativeModeTab.builder()
		.title(Component.translatable("itemGroup.shenzhimod.main"))
		.icon { ItemStack(ModItems.CREATION_CORE) }
		.displayItems { _, output ->
			ModItems.ALL.forEach(output::accept)
		}
		.build()

	internal fun bootstrap() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, MAIN_KEY, MAIN)
		ShenzhiMod.logger.debug("已注册创造模式分类 {}", MAIN_KEY.identifier())
	}
}
