package com.xin.shenzhimod

import net.fabricmc.api.ClientModInitializer

/**
 * 客户端入口。
 *
 * 目前是空壳：本模组的客户端表现（图腾冷却圆弧、GUI 进度、实体渲染）**全部复用原版管线**
 * （26.1 的 `GuiGraphicsExtractor` 与冷却组圆弧），无需自定义渲染，见需求 §6.3-G / §6.6。
 * 后续加入的客户端内容（转化核心 GUI、投掷物渲染器）都在这里接线。
 */
object ShenzhiModClient : ClientModInitializer {
	override fun onInitializeClient() {
		ShenzhiMod.logger.debug("ShenzhiMod 客户端初始化完成")
	}
}
