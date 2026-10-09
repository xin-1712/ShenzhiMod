package com.xin.shenzhimod.ability

import kotlin.math.sin

/**
 * "穿飞行附魔鞘翅**飞行时**"的鞘翅扇动角度（纯逻辑，不依赖 Minecraft，可单测）。
 *
 * 背景（26.1 源码）：玩家的鞘翅由 `WingsLayer` + `ElytraModel` 渲染，
 * 模型的翼角直接读渲染状态里的 `elytraRotX/Y/Z`；原版**只给生物**填这三个值
 * （`HumanoidMobRenderer` 从 `LivingEntity.elytraAnimationState` 取），
 * 玩家这条链路一直是 0——所以玩家的鞘翅平时是"完全静止"的。
 *
 * 本对象只负责算角度：给定时间相位，返回一组**小幅、连续变化**的翼角，
 * 让翅膀看起来在扇动（而不是永远一张不动的静态贴图）。
 * 相位建议直接传 `EntityRenderState.ageInTicks`（自带插值，帧间平滑）。
 */
object ElytraFlightAnimation {
	/** 一个相位单位（tick）内的摆动速度：约 0.45 rad/tick → 扇动一个来回约 0.7 秒。 */
	private const val SPEED = 0.45f

	/** 摆动幅度（弧度）。控制得很小：0.14 rad ≈ 8°，肉眼可见但不夸张。 */
	private const val AMPLITUDE = 0.14f

	/**
	 * 静止姿态的基准角。
	 *
	 * 取原版 `ElytraModel` 里翅膀 `PartPose` 的默认值（x = π/12 ≈ 0.26，z = ∓π/12）的
	 * 一半左右——比 0 更接近原版模型意图，又不会让翅膀在"起飞瞬间"突然大幅位移。
	 */
	private const val BASE_X = 0.10f
	private const val BASE_Z = -0.14f

	/** 一组翼角，单位弧度；`rotZ` 由渲染模型自动对另一侧取反。 */
	data class WingAngles(val rotX: Float, val rotY: Float, val rotZ: Float)

	/** 由相位（tick）算翼角。 */
	fun angles(phase: Float): WingAngles {
		val flutter = sin(phase * SPEED) * AMPLITUDE
		return WingAngles(
			rotX = BASE_X + flutter * 0.6f,
			rotY = flutter * 0.5f,
			rotZ = BASE_Z - flutter,
		)
	}
}
