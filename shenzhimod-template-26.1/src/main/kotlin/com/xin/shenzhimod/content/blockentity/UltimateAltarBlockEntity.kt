package com.xin.shenzhimod.content.blockentity

import com.xin.shenzhimod.ShenzhiMod
import com.xin.shenzhimod.machine.AltarEvent
import com.xin.shenzhimod.machine.AltarFusionState
import com.xin.shenzhimod.machine.AltarPhase
import com.xin.shenzhimod.machine.CoreAssemblyDefaults
import com.xin.shenzhimod.registry.ModBlockEntities
import com.xin.shenzhimod.util.ItemStacks
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LightningBolt
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/**
 * 终极祭坛的方块实体（模块 E）。
 *
 * 服务端权威 + 可中断 + 可存档恢复（难点 D8/D9）：
 * - 底座校验：祭坛正下方 3×3 必须全是配置指定的方块（默认下界合金块）；
 * - 核心检测：底座上方 3×3 范围内的掉落物里，数出"三核心"的数量；
 * - 中断：任一核心消失 → 落雷 + 复位，已吸收的核心不返还（需求 §4.5）。
 *
 * 状态机本体在 [AltarFusionState]（纯逻辑），这里只做世界交互与持久化。
 */
class UltimateAltarBlockEntity(pos: BlockPos, state: BlockState) :
	BlockEntity(ModBlockEntities.ULTIMATE_ALTAR, pos, state) {

	private val fusion: AltarFusionState = AltarFusionState(
		ShenzhiMod.config.altar.requiredCores,
		ShenzhiMod.config.altar.absorbStageTicks,
	)

	private var baseValid: Boolean = false
	private var baseCheckCooldown: Int = 0

	fun markBaseDirty() {
		baseCheckCooldown = 0
	}

	fun serverTick(level: ServerLevel, pos: BlockPos, state: BlockState) {
		if (baseCheckCooldown > 0) baseCheckCooldown--
		if (baseCheckCooldown == 0) {
			baseValid = checkBase(level, pos)
			baseCheckCooldown = BASE_CHECK_INTERVAL_TICKS
		}

		if (!baseValid && fusion.isRunning()) {
			fusion.reset()
			setChanged()
			return
		}

		val present = countCores(level, pos)
		when (val event = fusion.tick(present)) {
			is AltarEvent.StageAbsorbed -> {
				consumeOneCore(level, pos)
				setChanged()
			}

			AltarEvent.FusionComplete -> {
				setChanged()
				spawnOutput(level, pos)
			}

			AltarEvent.Aborted -> {
				setChanged()
				strikeVisualLightning(level, pos)
			}

			AltarEvent.Idle -> Unit
		}
	}

	/** Shift+右键启动。 */
	fun tryStart(player: Player) {
		val level = this.level as? ServerLevel ?: return
		if (!checkBase(level, worldPosition)) {
			player.sendSystemMessage(Component.translatable("message.shenzhimod.altar.base_invalid"))
			return
		}
		val present = countCores(level, worldPosition)
		if (fusion.start(present)) {
			player.sendSystemMessage(Component.translatable("message.shenzhimod.altar.started"))
			setChanged()
		} else {
			player.sendSystemMessage(Component.translatable("message.shenzhimod.altar.not_ready"))
		}
	}

	fun describeStatus(): Component = when {
		!baseValid -> Component.translatable("message.shenzhimod.altar.base_invalid")
		fusion.isRunning() -> Component.translatable(
			"message.shenzhimod.altar.progress",
			fusion.absorbed,
			ShenzhiMod.config.altar.requiredCores,
		)

		else -> Component.translatable("message.shenzhimod.altar.idle")
	}

	// ---- 世界交互 ----

	private fun checkBase(level: ServerLevel, pos: BlockPos): Boolean {
		val config = ShenzhiMod.config.altar
		val expected = ItemStacks.of(config.baseBlockId).item
		if (expected === net.minecraft.world.item.Items.AIR) return false
		val block = expected as? net.minecraft.world.item.BlockItem ?: return false
		val half = config.baseSize / 2
		for (dx in -half..half) {
			for (dz in -half..half) {
				val state = level.getBlockState(pos.offset(dx, -1, dz))
				if (!state.`is`(block.block)) return false
			}
		}
		return true
	}

	private fun coreBox(pos: BlockPos): AABB {
		val half = ShenzhiMod.config.altar.baseSize / 2
		return AABB.ofSize(Vec3.atCenterOf(pos), (half * 2 + 1).toDouble(), 2.0, (half * 2 + 1).toDouble())
	}

	private fun coreEntities(level: ServerLevel, pos: BlockPos): List<ItemEntity> {
		val coreIds = CoreAssemblyDefaults.coreItemIds()
		return level.getEntitiesOfClass(ItemEntity::class.java, coreBox(pos)) { entity ->
			entity.isAlive && ItemStacks.idOf(entity.item) in coreIds
		}
	}

	private fun countCores(level: ServerLevel, pos: BlockPos): Int = coreEntities(level, pos).size

	/** 吸收一个核心：从世界里销毁一个核心实体（已吸收的不返还）。 */
	private fun consumeOneCore(level: ServerLevel, pos: BlockPos) {
		val entity = coreEntities(level, pos).firstOrNull() ?: return
		val stack = entity.item
		stack.shrink(1)
		if (stack.isEmpty) entity.discard() else entity.item = stack
	}

	private fun spawnOutput(level: ServerLevel, pos: BlockPos) {
		val config = ShenzhiMod.config.altar
		val stack = ItemStacks.of(config.outputItemId, config.outputCount)
		if (stack.isEmpty) {
			ShenzhiMod.logger.warn("祭坛产物 {} 未注册，融合结果被丢弃", config.outputItemId)
			return
		}
		// 先清掉场上的核心（可能因为掉落物合并而多出），再产出，保证"扣了再产"。
		coreEntities(level, pos).forEach { it.discard() }
		val entity = ItemEntity(level, pos.x + 0.5, pos.y + 1.5, pos.z + 0.5, stack)
		entity.setDefaultPickUpDelay()
		level.addFreshEntity(entity)
		fusion.reset()
	}

	/** 视觉落雷：不破坏方块、不引燃（`isVisualOnly`）。 */
	private fun strikeVisualLightning(level: ServerLevel, pos: BlockPos) {
		if (!ShenzhiMod.config.altar.strikeLightningOnAbort) return
		val bolt = LightningBolt(EntityType.LIGHTNING_BOLT, level)
		bolt.setVisualOnly(true)
		bolt.setPos(pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5)
		level.addFreshEntity(bolt)
	}

	// ---- 持久化 ----

	override fun saveAdditional(output: ValueOutput) {
		super.saveAdditional(output)
		output.putString("Phase", fusion.phase.name)
		output.putInt("Absorbed", fusion.absorbed)
		output.putInt("StageProgress", fusion.currentStageProgress())
	}

	override fun loadAdditional(input: ValueInput) {
		super.loadAdditional(input)
		val phase = runCatching { AltarPhase.valueOf(input.getStringOr("Phase", AltarPhase.IDLE.name)) }
			.getOrDefault(AltarPhase.IDLE)
		fusion.restore(phase, input.getIntOr("Absorbed", 0), input.getIntOr("StageProgress", 0))
		baseValid = false
		baseCheckCooldown = 0
	}

	private companion object {
		const val BASE_CHECK_INTERVAL_TICKS = 20
	}
}
