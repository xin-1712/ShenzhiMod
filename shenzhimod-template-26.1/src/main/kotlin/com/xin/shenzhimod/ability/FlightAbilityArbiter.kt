package com.xin.shenzhimod.ability

/**
 * 飞行能力仲裁（模块 I，纯逻辑）。
 *
 * 需求/难点（§4.9、D4）：
 * - 服务端权威：能不能飞由服务端算，客户端只表现；
 * - **只加不夺**：创造/旁观/其他模组给的飞行能力不允许被我们关掉；
 * - 卸下鞘翅、死亡、换维度、重新登录都要重算。
 *
 * 仲裁器通过"我们是否曾经授予过"这个标记来判断该不该回收能力，
 * 避免把别人给的能力误删。
 */
data class FlightState(
	val hasFlightEnchant: Boolean,
	val isCreativeOrSpectator: Boolean,
	val mayFly: Boolean,
	val grantedByUs: Boolean,
)

sealed interface FlightAction {
	/** 需要授予飞行。 */
	data object Grant : FlightAction

	/** 需要回收飞行（仅当本模组授予过时）。 */
	data object Revoke : FlightAction

	/** 保持现状。 */
	data object Keep : FlightAction
}

object FlightAbilityArbiter {
	fun decide(state: FlightState): FlightAction {
		if (state.isCreativeOrSpectator) {
			// 创造/旁观的能力永远不动；如果之前是我们加的，标记交还由上层处理。
			return FlightAction.Keep
		}
		return when {
			state.hasFlightEnchant && !state.mayFly -> FlightAction.Grant
			!state.hasFlightEnchant && state.mayFly && state.grantedByUs -> FlightAction.Revoke
			else -> FlightAction.Keep
		}
	}
}
