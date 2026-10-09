package com.xin.shenzhimod.content.blockentity

import com.xin.shenzhimod.ShenzhiMod
import com.xin.shenzhimod.advancement.ModAdvancements
import com.xin.shenzhimod.content.block.UltimateAltarBlock
import com.xin.shenzhimod.machine.AltarEvent
import com.xin.shenzhimod.machine.AltarFusionState
import com.xin.shenzhimod.machine.AltarPhase
import com.xin.shenzhimod.machine.CoreAssemblyDefaults
import com.xin.shenzhimod.registry.ModBlockEntities
import com.xin.shenzhimod.util.ItemStacks
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LightningBolt
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.UUID

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
	private var effectTick: Int = 0

	/** 放下祭坛的玩家（成就"地基打得牢"优先发给他），随存档持久化。 */
	private var placerId: UUID? = null

	/** "地基打得牢"是否已经发过；只发一次，避免每次区块加载都重复尝试。 */
	private var baseAdvancementGranted: Boolean = false

	/** Shift+右键启动融合的玩家（成就"天打雷劈"优先发给他），随存档持久化。 */
	private var startedById: UUID? = null

	fun markBaseDirty() {
		baseCheckCooldown = 0
	}

	/** 由方块在 `setPlacedBy` 时登记"谁放的"，用于精准发放底座成就。 */
	fun rememberPlacer(player: Player) {
		if (placerId != player.uuid) {
			placerId = player.uuid
			setChanged()
		}
	}

	fun serverTick(level: ServerLevel, pos: BlockPos, state: BlockState) {
		effectTick++
		if (baseCheckCooldown > 0) baseCheckCooldown--
		if (baseCheckCooldown == 0) {
			baseValid = checkBase(level, pos)
			baseCheckCooldown = BASE_CHECK_INTERVAL_TICKS
		}

		// 成就"地基打得牢"：**底座第一次校验通过、贴图从"底座不正确"切走的那一刻**授予。
		// 放在这里而不是"放置方块时"，是因为玩家通常先放祭坛、再补底座（也可能反过来）。
		//
		// 只在"刚重新校验完底座"的那一 tick 尝试（每秒一次）：正常情况下放下祭坛的玩家就在旁边，
		// 一次就发出去；如果他刚好离线且附近没人，就先不写"已发放"标记，等有人在场的那一秒再发，
		// 既不会把成就弄丢，也不会每 tick 扫一次实体。
		if (baseCheckCooldown == BASE_CHECK_INTERVAL_TICKS && baseValid && !baseAdvancementGranted) {
			val handled = ModAdvancements.awardPreferredOrNearby(
				level = level,
				preferredPlayer = placerId,
				pos = pos,
				radius = ALTAR_ADVANCEMENT_RADIUS,
				advancement = ModAdvancements.ALTAR_BASE,
			)
			if (handled) {
				baseAdvancementGranted = true
				setChanged()
			}
		}

		if (!baseValid && fusion.isRunning()) {
			fusion.reset()
			setChanged()
			updateVisualState(level, pos, state, countCores(level, pos))
			return
		}

		val present = countCores(level, pos)
		val altarEvent = fusion.tick(present)
		// 本 tick 结束后底座上还剩几个核心；直接用已有计数推算，避免再扫一遍实体
		val coresLeft = when (altarEvent) {
			is AltarEvent.StageAbsorbed -> present - 1
			AltarEvent.FusionComplete -> 0
			else -> present
		}
		when (altarEvent) {
			is AltarEvent.StageAbsorbed -> {
				consumeOneCore(level, pos)
				playStageEffects(level, pos, altarEvent.absorbed)
				setChanged()
			}

			AltarEvent.FusionComplete -> {
				setChanged()
				// 先放特效（含落雷）再产出，避免产物先落地被雷声"抢戏"
				playCompletionEffects(level, pos)
				// 成就"天打雷劈"：雷电劈下的同一 tick，发给启动融合的玩家（不在线时发给附近玩家）。
				ModAdvancements.awardPreferredOrNearby(
					level = level,
					preferredPlayer = startedById,
					pos = pos,
					radius = ALTAR_ADVANCEMENT_RADIUS,
					advancement = ModAdvancements.CREATION_CORE,
				)
				spawnOutput(level, pos)
			}

			AltarEvent.Aborted -> {
				setChanged()
				strikeLightning(level, pos)
				level.playSound(null, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, SoundEvents.ENDER_DRAGON_HURT, SoundSource.BLOCKS, 1.0f, 0.7f)
			}

			AltarEvent.Idle -> if (fusion.isRunning()) spawnChargeParticles(level, pos)
		}

		updateVisualState(level, pos, state, coresLeft)
	}

	/**
	 * 把"在跑 / 底座不对 / 三核就位 / 空转"翻译成方块状态。
	 *
	 * 只服务表现：贴图与发光强度由方块状态驱动，客户端由原版同步机制自动拿到，
	 * 不需要自定义包。
	 *
	 * 优先级：融合中 > 底座不正确 > 三核就位 > 空转（底座被挖的那一瞬以"在跑"为准，
	 * 不会先闪一下红环）。
	 */
	private fun updateVisualState(level: ServerLevel, pos: BlockPos, state: BlockState, coresOnBase: Int) {
		val visual = when {
			fusion.isRunning() -> UltimateAltarBlock.AltarState.ACTIVE
			!baseValid -> UltimateAltarBlock.AltarState.INVALID
			// 先比数量再比种类：数量对不上就直接跳过，空转时不额外扫实体
			coresOnBase == ShenzhiMod.config.altar.requiredCores && hasCorrectCores(level, pos) ->
				UltimateAltarBlock.AltarState.READY
			else -> UltimateAltarBlock.AltarState.IDLE
		}
		if (state.getValue(UltimateAltarBlock.STATE) !== visual) {
			level.setBlock(pos, state.setValue(UltimateAltarBlock.STATE, visual), Block.UPDATE_ALL)
		}
	}

	// ---- 特效（全部用原版粒子与音效，不需要额外贴图/音频文件）----

	/**
	 * 融合进行中的"末影龙复活式光线"：
	 * 祭坛中心升起一道竖直的 END_ROD 光柱，底部伴随灵魂火与电流粒子。
	 */
	private fun spawnChargeParticles(level: ServerLevel, pos: BlockPos) {
		val visual = ShenzhiMod.config.visual
		if (!visual.particlesEnabled) return
		if (effectTick % 2 != 0) return

		val cx = pos.x + 0.5
		val cz = pos.z + 0.5
		val columnHeight = 6
		val y = pos.y + 1.0 + level.random.nextDouble() * columnHeight
		level.sendParticles(ParticleTypes.END_ROD, cx, y, cz, 1, 0.12, 0.0, 0.12, 0.0)

		val density = visual.particleDensity.coerceIn(0.0, 1.0)
		if (level.random.nextDouble() < density) {
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, cx, pos.y + 1.15, cz, 2, 0.35, 0.15, 0.35, 0.005)
		}
		if (level.random.nextDouble() < density * 0.5) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, cx, pos.y + 1.3, cz, 2, 0.45, 0.25, 0.45, 0.02)
		}
	}

	/** 一段核心被吸收：短促的龙翼声 + 一次小爆发。 */
	private fun playStageEffects(level: ServerLevel, pos: BlockPos, absorbed: Int) {
		val cx = pos.x + 0.5
		val cy = pos.y + 1.2
		val cz = pos.z + 0.5
		level.playSound(null, cx, cy, cz, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.BLOCKS, 1.0f, 0.8f + absorbed * 0.1f)
		if (!ShenzhiMod.config.visual.particlesEnabled) return
		level.sendParticles(ParticleTypes.END_ROD, cx, cy, cz, 24, 0.6, 0.6, 0.6, 0.05)
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, cx, cy, cz, 12, 0.5, 0.4, 0.5, 0.02)
	}

	/** 融合完成：落雷 + 龙吼 + 大爆发（落雷为视觉专用，不破坏方块、不引燃）。 */
	private fun playCompletionEffects(level: ServerLevel, pos: BlockPos) {
		strikeLightning(level, pos)
		val cx = pos.x + 0.5
		val cy = pos.y + 1.5
		val cz = pos.z + 0.5
		level.playSound(null, cx, cy, cz, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.BLOCKS, 1.0f, 1.0f)
		if (!ShenzhiMod.config.visual.particlesEnabled) return
		level.sendParticles(ParticleTypes.END_ROD, cx, cy, cz, 80, 1.2, 1.0, 1.2, 0.15)
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, cx, cy, cz, 40, 1.0, 0.8, 1.0, 0.1)
	}

	/** Shift+右键启动。 */
	fun tryStart(player: Player) {
		val level = this.level as? ServerLevel ?: return
		if (!checkBase(level, worldPosition)) {
			player.sendSystemMessage(Component.translatable("message.shenzhimod.altar.base_invalid"))
			return
		}
		// 必须是**三种不同的核心各一个**，且底座范围内不能有别的物品（2026-10-02 反馈：
		// 之前只数了数量，放三个农业核心也能开炉）。这里按"实体个数 + 种类集合 + 无杂项"三重判定。
		if (!hasCorrectCores(level, worldPosition)) {
			player.sendSystemMessage(Component.translatable("message.shenzhimod.altar.wrong_cores"))
			return
		}
		if (fusion.start(ShenzhiMod.config.altar.requiredCores)) {
			startedById = player.uuid
			setChanged()
			level.playSound(null, worldPosition.x + 0.5, worldPosition.y + 0.5, worldPosition.z + 0.5, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0f, 1.0f)
			player.sendSystemMessage(Component.translatable("message.shenzhimod.altar.started"))
			setChanged()
		} else {
			player.sendSystemMessage(Component.translatable("message.shenzhimod.altar.not_ready"))
		}
	}

	/**
	 * 底座附近是否为"合法三核"：
	 * 1. 核心掉落物**恰好** [ShenzhiMod.config.altar.requiredCores] 个（多了少了都不行）；
	 * 2. 种类集合正好是 农业 / 矿物 / 战斗 三种（重复种类 → 集合变小 → 拒绝）；
	 * 3. 范围内**没有其它物品**（有杂项一律不开炉）。
	 */
	private fun hasCorrectCores(level: ServerLevel, pos: BlockPos): Boolean {
		val required = ShenzhiMod.config.altar.requiredCores
		val cores = coreEntities(level, pos)
		if (cores.size != required) return false
		val ids = cores.mapNotNull { ItemStacks.idOf(it.item) }.toSet()
		if (ids != CoreAssemblyDefaults.coreItemIds()) return false
		val box = coreBox(pos)
		val others = level.getEntitiesOfClass(ItemEntity::class.java, box) { entity ->
			entity.isAlive && ItemStacks.idOf(entity.item) !in CoreAssemblyDefaults.coreItemIds()
		}
		return others.isEmpty()
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
		// 判定逻辑与"放下瞬间"共用一处，避免两边规则漂移
		return UltimateAltarBlock.isBaseComplete(level, pos)
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

	/**
	 * 落雷。
	 *
	 * 需求原稿要求"不破坏、不引燃"（视觉专用），实测后按反馈改为**真实落雷**：
	 * 有伤害、在落点引燃（配置项 `altar.realLightning` 可切回纯视觉）。
	 * 落雷在产出之前发生，且三核心与创世核心都是防火物品，不会被雷击毁掉。
	 */
	private fun strikeLightning(level: ServerLevel, pos: BlockPos) {
		if (!ShenzhiMod.config.altar.strikeLightningOnAbort) return
		val bolt = LightningBolt(EntityType.LIGHTNING_BOLT, level)
		bolt.setVisualOnly(!ShenzhiMod.config.altar.realLightning)
		bolt.setPos(pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5)
		level.addFreshEntity(bolt)
	}

	// ---- 持久化 ----

	override fun saveAdditional(output: ValueOutput) {
		super.saveAdditional(output)
		output.putString("Phase", fusion.phase.name)
		output.putInt("Absorbed", fusion.absorbed)
		output.putInt("StageProgress", fusion.currentStageProgress())
		output.putBoolean("BaseAdvancementGranted", baseAdvancementGranted)
		placerId?.let { output.putString("Placer", it.toString()) }
		startedById?.let { output.putString("StartedBy", it.toString()) }
	}

	override fun loadAdditional(input: ValueInput) {
		super.loadAdditional(input)
		val phase = runCatching { AltarPhase.valueOf(input.getStringOr("Phase", AltarPhase.IDLE.name)) }
			.getOrDefault(AltarPhase.IDLE)
		fusion.restore(phase, input.getIntOr("Absorbed", 0), input.getIntOr("StageProgress", 0))
		baseAdvancementGranted = input.getBooleanOr("BaseAdvancementGranted", false)
		placerId = input.getStringOr("Placer", "").takeIf { it.isNotEmpty() }?.let { runCatching { UUID.fromString(it) }.getOrNull() }
		startedById = input.getStringOr("StartedBy", "").takeIf { it.isNotEmpty() }?.let { runCatching { UUID.fromString(it) }.getOrNull() }
		baseValid = false
		baseCheckCooldown = 0
	}

	private companion object {
		const val BASE_CHECK_INTERVAL_TICKS = 20

		/** 成就的"就近发放"半径：祭坛流程都是近距离交互，16 格足够覆盖实际玩家。 */
		const val ALTAR_ADVANCEMENT_RADIUS = 16.0
	}
}
