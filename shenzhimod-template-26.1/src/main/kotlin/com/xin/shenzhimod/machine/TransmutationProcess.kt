package com.xin.shenzhimod.machine

/**
 * 转化核心的进度状态机（纯逻辑）。
 *
 * 需求（§5.1 性能）：用整数 tick 计时，**不每 tick 构造对象**；配方不匹配则归零。
 */
class TransmutationProcess {
	var progressTicks: Int = 0
		private set

	/** 当前正在处理的配方 ID，未在运行时为 null（用于客户端进度同步与持久化）。 */
	var activeRecipeId: String? = null
		private set

	/**
	 * 推进一 tick。
	 *
	 * @param recipe 当前输入/催化剂匹配到的配方，null 表示不匹配
	 * @return 本 tick 是否刚好完成
	 */
	fun tick(recipe: TransmutationEntry?): Boolean {
		if (recipe == null || recipe.durationTicks <= 0) {
			reset()
			return false
		}
		if (activeRecipeId != recipe.id) {
			activeRecipeId = recipe.id
			progressTicks = 0
		}
		progressTicks++
		return progressTicks >= recipe.durationTicks
	}

	/** 完成结算后调用：重置进度，等待下一次运行。 */
	fun complete() {
		progressTicks = 0
		activeRecipeId = null
	}

	fun reset() {
		progressTicks = 0
		activeRecipeId = null
	}

	/** 0.0~1.0 的进度，供 GUI 与包同步使用。 */
	fun progressFraction(durationTicks: Int): Double {
		if (durationTicks <= 0) return 0.0
		return (progressTicks.toDouble() / durationTicks.toDouble()).coerceIn(0.0, 1.0)
	}

	fun restore(recipeId: String?, ticks: Int) {
		activeRecipeId = recipeId
		progressTicks = ticks.coerceAtLeast(0)
	}
}
