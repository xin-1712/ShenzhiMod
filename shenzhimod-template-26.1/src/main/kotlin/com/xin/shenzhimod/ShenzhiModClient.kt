package com.xin.shenzhimod

import com.xin.shenzhimod.client.TransmutationCoreScreen
import com.xin.shenzhimod.registry.ModMenus
import net.fabricmc.api.ClientModInitializer
import net.minecraft.client.gui.screens.MenuScreens

/**
 * 客户端入口。
 *
 * 目前是空壳：本模组的客户端表现（图腾冷却圆弧、GUI 进度、实体渲染）**全部复用原版管线**
 * （26.1 的 `GuiGraphicsExtractor` 与冷却组圆弧），无需自定义渲染，见需求 §6.3-G / §6.6。
 * 后续加入的客户端内容（转化核心 GUI、投掷物渲染器）都在这里接线。
 */
object ShenzhiModClient : ClientModInitializer {
	override fun onInitializeClient() {
		// 容器菜单 → 界面的映射（26.1 的 register 由 Fabric 的 class tweaker 放开）
		MenuScreens.register(ModMenus.TRANSMUTATION_CORE) { menu, inventory, title ->
			TransmutationCoreScreen(menu, inventory, title)
		}
		ShenzhiMod.logger.debug("ShenzhiMod 客户端初始化完成")
	}
}
