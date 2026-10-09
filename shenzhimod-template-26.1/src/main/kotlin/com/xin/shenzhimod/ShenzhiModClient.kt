package com.xin.shenzhimod

import com.xin.shenzhimod.client.TransmutationCoreScreen
import com.xin.shenzhimod.client.TotemAnimationState
import com.xin.shenzhimod.client.SyntheticDragonHeadRenderer
import com.xin.shenzhimod.content.entity.ThrownNetherStarShard
import com.xin.shenzhimod.interaction.FlightEnchantService
import com.xin.shenzhimod.network.TotemTriggerPayload
import com.xin.shenzhimod.registry.ModEntities
import com.xin.shenzhimod.registry.ModBlockEntities
import com.xin.shenzhimod.registry.ModMenus
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.client.gui.screens.inventory.AnvilScreen
import net.minecraft.client.gui.screens.MenuScreens
import net.minecraft.client.renderer.entity.EntityRenderers
import net.minecraft.client.renderer.entity.ThrownItemRenderer
import net.minecraft.client.renderer.entity.player.AvatarRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AnvilMenu
import net.minecraft.world.phys.Vec3

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
		// 合成龙首：用原版头颅模型渲染（不再是一个立方体），并带"合成中"的眼睛/下颚表现
		BlockEntityRenderers.register(ModBlockEntities.SYNTHETIC_DRAGON_HEAD) { context -> SyntheticDragonHeadRenderer(context) }
		// 飞行中的鞘翅粒子尾迹（视觉部分；翼角扇动由 AvatarRendererFlightAnimationMixin 负责）
		registerElytraFlightTrail()
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
	 * 飞行附魔鞘翅的粒子尾迹。
	 *
	 * 触发条件与动画一致：**玩家在飞（`abilities.flying`）、穿的鞘翅带飞行附魔、且不在滑翔**。
	 * 与滑翔无关是刻意的——滑翔时原版本来就有自己的表现，不需要我们叠一层。
	 *
	 * 粒子从两侧翅膀位置冒出、速度轻微向后，看起来像"翅膀扇出来的气流"。
	 * 纯客户端、可配置关闭（`visual.elytraFlightTrailParticles`）。
	 */
	private fun registerElytraFlightTrail() {
		ClientTickEvents.END_CLIENT_TICK.register { client ->
			val player = client.player ?: return@register
			val level = client.level ?: return@register
			if (client.isPaused) return@register

			val visual = ShenzhiMod.config.visual
			if (!visual.particlesEnabled || !visual.elytraFlightTrailParticles) return@register
			if (!isFlyingWithEnchantedElytra(player)) return@register
			// 每 2 tick 一次即可形成连续尾迹，又不至于刷屏。
			if (player.tickCount % 2 != 0) return@register
			// 密度倍率直接当出现概率用：调低就是更稀疏的尾迹。
			if (level.random.nextDouble() > visual.particleDensity.coerceIn(0.0, 1.0)) return@register

			spawnWingTrail(level, player)
		}
	}

	/** 玩家是不是"靠本模组的飞行附魔在飞"（创造/旁观不算，滑翔不算）。 */
	private fun isFlyingWithEnchantedElytra(player: Player): Boolean {
		if (!player.abilities.flying || player.isSpectator || player.isFallFlying) return false
		return FlightEnchantService.hasFlightEnchant(player.getItemBySlot(EquipmentSlot.CHEST))
	}

	/** 在左右翅膀位置各放一枚 END_ROD 尾迹粒子。 */
	private fun spawnWingTrail(level: ClientLevel, player: Player) {
		val yaw = Math.toRadians(player.yRot.toDouble())
		// 26.1 的朝向约定：yaw = 0 面向 +Z，yaw 增大顺时针（与 `Vec3.directionFromRotation` 一致）。
		val forward = Vec3(-kotlin.math.sin(yaw), 0.0, kotlin.math.cos(yaw))
		val right = Vec3(kotlin.math.cos(yaw), 0.0, kotlin.math.sin(yaw))
		// 略微偏后 + 胸背高度，避免粒子糊在脸上。
		val base = player.position().add(forward.scale(-0.35)).add(0.0, player.eyeHeight * 0.8, 0.0)
		// 向后的小速度：看起来像翅膀把气流甩在后面。
		val drift = forward.scale(-0.05)

		for (side in arrayOf(-1.0, 1.0)) {
			val offset = right.scale(0.38 * side)
			val p = base.add(offset)
			level.addParticle(
				ParticleTypes.END_ROD,
				p.x, p.y, p.z,
				drift.x + level.random.nextGaussian() * 0.01,
				drift.y + level.random.nextGaussian() * 0.01,
				drift.z + level.random.nextGaussian() * 0.01,
			)
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
			AvatarRenderer::class.java,   // 飞行鞘翅：飞行时扇动翅膀
		)
		ShenzhiMod.logger.debug("已预加载 {} 个被注入的原版类", injected.size)
	}
}
