package com.xin.shenzhimod

import com.xin.shenzhimod.client.TransmutationCoreScreen
import com.xin.shenzhimod.client.TotemAnimationState
import com.xin.shenzhimod.content.entity.ThrownNetherStarShard
import com.xin.shenzhimod.network.TotemTriggerPayload
import com.xin.shenzhimod.registry.ModEntities
import com.xin.shenzhimod.registry.ModMenus
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.client.gui.screens.inventory.AnvilScreen
import net.minecraft.client.gui.screens.MenuScreens
import net.minecraft.client.renderer.entity.EntityRenderers
import net.minecraft.client.renderer.entity.ThrownItemRenderer
import net.minecraft.world.inventory.AnvilMenu

/**
 * 客户端入口。
 *
 * 客户端的表现尽量**复用原版管线**（26.1 的 `GuiGraphicsExtractor` 与冷却组圆弧），见需求 §6.3 / §6.6。
 * 目前在这里接线的内容：转化核心 GUI、投掷物渲染器、铁砧界面注入的前置校验。
 * 「不死图腾冷却圆弧」不需要任何客户端代码——原版按冷却组自动绘制（§6.3-G）。
 */
object ShenzhiModClient : ClientModInitializer {
	override fun onInitializeClient() {
		// 容器菜单 → 界面的映射（26.1 的 register 由 Fabric 的 class tweaker 放开）
		MenuScreens.register(ModMenus.TRANSMUTATION_CORE) { menu, inventory, title ->
			TransmutationCoreScreen(menu, inventory, title)
		}
		// 投掷物渲染：直接用原版的"投掷物品"渲染器（拿物品模型当贴图，表现与附魔之瓶一致）
		EntityRenderers.register(ModEntities.NETHER_STAR_SHARD) { context -> ThrownItemRenderer<ThrownNetherStarShard>(context) }
		registerPayloadReceivers()
		preloadInjectedVanillaClasses()
		ShenzhiMod.logger.debug("ShenzhiMod 客户端初始化完成")
	}

	/** 收服务端的"这次免死是哪只手触发的"信号（处理器已在渲染线程，可直接改客户端状态）。 */
	private fun registerPayloadReceivers() {
		ClientPlayNetworking.registerGlobalReceiver(TotemTriggerPayload.TYPE) { payload, context ->
			TotemAnimationState.onServerTrigger(context.player(), payload.handOrdinal)
		}
	}

	/**
	 * 提前触发"被本模组注入的原版类"的**类加载**。
	 *
	 * Mixin 是在类加载时应用并校验注入点/签名的，所以在这里引用一次类，注入点写错就会在**启动时**
	 * 立刻报错，而不是等玩家打开铁砧才崩。只有类字面量引用，不产生任何运行时行为。
	 */
	private fun preloadInjectedVanillaClasses() {
		val injected = arrayOf(
			AnvilScreen::class.java,      // 铁砧界面：固定成本显示
			AnvilMenu::class.java,        // 铁砧菜单：核心附魔结果 + 不降级
			ClientPacketListener::class.java, // 免死动画：显示真正触发的那件图腾
		)
		ShenzhiMod.logger.debug("已预加载 {} 个被注入的原版类", injected.size)
	}
}
