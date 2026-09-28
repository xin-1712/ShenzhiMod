package com.xin.shenzhimod.machine

/**
 * 终极祭坛的状态机（模块 E，纯逻辑）。
 *
 * 流程（需求 §4.5）：
 * `IDLE → ABSORBING(第 1/2/3 个核心) → STRIKE → DONE`
 * 每段吸收固定时长；任一核心消失立刻中断、复位，且**已吸收的核心不返还**。
 */
enum class AltarPhase {
	IDLE,
	ABSORBING,
	STRIKE,
	DONE,
}

/** 状态机推进的结果。 */
sealed interface AltarEvent {
	/** 本 tick 无进展。 */
	data object Idle : AltarEvent

	/** 完成了一段吸收，`absorbed` 为已吸收核心数。 */
	data class StageAbsorbed(val absorbed: Int) : AltarEvent

	/** 三个核心全部吸收完毕，祭坛开始落雷/产出。 */
	data object FusionComplete : AltarEvent

	/** 核心中途消失，融合中断。 */
	data object Aborted : AltarEvent
}

/**
 * @param requiredCores 需要的核心数（默认 3）
 * @param stageTicks 每段吸收耗时
 */
class AltarFusionState(private val requiredCores: Int, private val stageTicks: Int) {
	var phase: AltarPhase = AltarPhase.IDLE
		private set

	var absorbed: Int = 0
		private set

	private var stageProgress: Int = 0

	fun isRunning(): Boolean = phase == AltarPhase.ABSORBING || phase == AltarPhase.STRIKE

	/** Shift+右键启动；只有 IDLE 且核心数量正确时才接受。 */
	fun start(availableCores: Int): Boolean {
		if (phase != AltarPhase.IDLE) return false
		if (availableCores < requiredCores) return false
		phase = AltarPhase.ABSORBING
		absorbed = 0
		stageProgress = 0
		return true
	}

	/**
	 * 推进一 tick。
	 *
	 * @param coresStillPresent 底座上仍然存在的核心数量（只算地上剩下的）
	 */
	fun tick(coresStillPresent: Int): AltarEvent {
		if (!isRunning()) return AltarEvent.Idle

		// 中断判定：地上应该还有"待吸收"数量的核心；少一个就中断。
		val remaining = requiredCores - absorbed
		if (coresStillPresent < remaining) {
			reset()
			return AltarEvent.Aborted
		}

		if (phase == AltarPhase.STRIKE) {
			phase = AltarPhase.DONE
			return AltarEvent.FusionComplete
		}

		stageProgress++
		if (stageProgress < stageTicks) return AltarEvent.Idle

		stageProgress = 0
		absorbed++
		if (absorbed >= requiredCores) {
			phase = AltarPhase.STRIKE
		}
		return AltarEvent.StageAbsorbed(absorbed)
	}

	/** 结束时复位（保留 DONE 由调用方决定何时清空）。 */
	fun reset() {
		phase = AltarPhase.IDLE
		absorbed = 0
		stageProgress = 0
	}

	fun restore(phase: AltarPhase, absorbed: Int, stageProgress: Int) {
		this.phase = phase
		this.absorbed = absorbed.coerceIn(0, requiredCores)
		this.stageProgress = stageProgress.coerceAtLeast(0)
	}

	fun currentStageProgress(): Int = stageProgress
}
