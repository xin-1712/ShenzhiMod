package com.xin.shenzhimod.content.blockentity

import com.xin.shenzhimod.ShenzhiMod
import com.xin.shenzhimod.content.block.SyntheticDragonHeadBlock
import com.xin.shenzhimod.data.ShenzhiTables
import com.xin.shenzhimod.machine.AssemblyMatch
import com.xin.shenzhimod.machine.CoreAssemblyRecipe
import com.xin.shenzhimod.machine.DragonHeadLedger
import com.xin.shenzhimod.machine.match
import com.xin.shenzhimod.registry.ModBlockEntities
import com.xin.shenzhimod.util.ItemStacks
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/**
 * 合成龙首的方块实体（模块 B）。
 *
 * 服务端权威：吸收、账本、合成进度全部在服务端计算并持久化到 NBT；
 * 客户端只看到方块本身与弹出物。
 *
 * 物品守恒（D5）实现要点：
 * - 吸收：先算出"能收多少"，再 `ledger.absorb` 成功后才销毁世界里的实体；
 * - 吐出：先把账本 `drainAll` 快照出来，再逐个生成实体，任何一步失败都不会重复计数；
 * - 破坏：`dropStoredContents` 把账本全部实体化，账本随之清空。
 */
class SyntheticDragonHeadBlockEntity(pos: BlockPos, state: BlockState) :
	BlockEntity(ModBlockEntities.SYNTHETIC_DRAGON_HEAD, pos, state) {

	private var ledger: DragonHeadLedger = DragonHeadLedger(ShenzhiMod.config.dragonHead.ledgerCapacity)
	private var tickCounter: Int = 0
	private var absorbCooldown: Int = 0
	private var craftTicks: Int = 0
	private var craftRecipe: CoreAssemblyRecipe? = null

	fun serverTick(level: ServerLevel, pos: BlockPos, state: BlockState) {
		val config = ShenzhiMod.config
		val powered = state.getValue(SyntheticDragonHeadBlock.POWERED)

		if (!powered) {
			if (craftTicks != 0 || craftRecipe != null) {
				craftTicks = 0
				craftRecipe = null
				setChanged()
			}
			if (!ledger.isEmpty()) {
				ejectAll(level, pos)
			}
			setActive(level, pos, false)
			return
		}

		if (absorbCooldown > 0) absorbCooldown--

		tickCounter++
		if (tickCounter % config.dragonHead.absorbIntervalTicks == 0 && absorbCooldown <= 0) {
			absorbNearby(level, pos)
		}

		tickCrafting(level, pos, config.dragonHead.craftDurationTicks)
		// 倒计时是否在进行 → 方块状态（客户端渲染器据此做"下颚动 + 眼睛发亮"）
		setActive(level, pos, craftRecipe != null)
		emitWorkingEffects(level, pos, crafting = craftRecipe != null)
	}

	/**
	 * 同步"正在合成"的方块状态（2026-09-30 反馈 #2：整个合成过程龙首毫无变化）。
	 *
	 * 这个状态只用于**方块自发光**（lightLevel）与"开始合成"的那一刻音效；
	 * 眼睛颜色由渲染器恒定为选定配色（2026-10-02：状态驱动的双贴图切换在 26.1 上会慢一拍/卡住）。
	 * 只在状态真正变化时写方块，避免每 tick 刷方块更新。
	 */
	private fun setActive(level: ServerLevel, pos: BlockPos, active: Boolean) {
		val state = level.getBlockState(pos)
		if (state.getValue(SyntheticDragonHeadBlock.ACTIVE) != active) {
			level.setBlock(pos, state.setValue(SyntheticDragonHeadBlock.ACTIVE, active), Block.UPDATE_CLIENTS)
			if (active) {
				// 开始合成：一声"启动"音 + 一圈粒子
				level.playSound(null, pos.x + 0.5, pos.y + 0.8, pos.z + 0.5, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.7f, 1.4f)
				if (ShenzhiMod.config.visual.particlesEnabled) {
					level.sendParticles(ParticleTypes.END_ROD, pos.x + 0.5, pos.y + 1.1, pos.z + 0.5, 24, 0.45, 0.25, 0.45, 0.06)
				}
			}
		}
	}

	/**
	 * 运转中的粒子与音效（2026-10-02 反馈：合成过程"没有任何特效和音效"）。
	 *
	 * - 充能（吸收/待机）：每 10 tick 少量火花，头顶轻轻冒；
	 * - 合成中：每 3 tick 一簇紫色粒子、每 40 tick 一声低沉的嗡鸣。
	 * 全部用原版粒子与音效，不需要额外素材。
	 */
	private fun emitWorkingEffects(level: ServerLevel, pos: BlockPos, crafting: Boolean) {
		if (!ShenzhiMod.config.visual.particlesEnabled) return
		if (crafting) {
			level.sendParticles(
				ParticleTypes.ENCHANT,
				pos.x + 0.5, pos.y + 1.35, pos.z + 0.5,
				4, 0.4, 0.15, 0.4, 0.03,
			)
			if (tickCounter % 40 == 0) {
				level.playSound(null, pos.x + 0.5, pos.y + 0.6, pos.z + 0.5, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.5f, 0.7f)
			}
			return
		}
		if (tickCounter % 10 == 0) {
			level.sendParticles(
				ParticleTypes.END_ROD,
				pos.x + 0.5, pos.y + 1.25, pos.z + 0.5,
				1, 0.3, 0.05, 0.3, 0.01,
			)
		}
	}

	/** 扫描 3×3×3 范围内的掉落物，只接受压缩态白名单里的物品。 */
	private fun absorbNearby(level: ServerLevel, pos: BlockPos) {
		val config = ShenzhiMod.config
		val radius = config.dragonHead.absorbRadiusBlocks * 2 + 1
		val box: AABB = AABB.ofSize(Vec3.atCenterOf(pos.above()), radius.toDouble(), radius.toDouble(), radius.toDouble())
		val items = level.getEntitiesOfClass(ItemEntity::class.java, box) { it.isAlive }
		if (items.isEmpty()) return

		var changed = false
		for (entity in items) {
			val stack = entity.item
			val path = ItemStacks.modPathOf(stack) ?: continue
			if (!ShenzhiTables.compression.isCompressedOutput(path)) continue
			if (!ledger.canAccept(path)) continue

			val accepted = ledger.absorb(path, stack.count)
			if (accepted <= 0) continue

			// 先扣实体再记账的顺序已在上面用"先算接受量"表达：
			// 只有 ledger 明确接受后，才允许从世界里扣除。
			stack.shrink(accepted)
			if (stack.isEmpty) entity.discard() else entity.item = stack
			changed = true
		}
		if (changed) setChanged()
	}

	/** 账本满足某个核心配方后开始倒计时，结束时产出核心。 */
	private fun tickCrafting(level: ServerLevel, pos: BlockPos, durationTicks: Int) {
		if (ledger.isEmpty()) {
			if (craftTicks != 0) {
				craftTicks = 0
				craftRecipe = null
				setChanged()
			}
			return
		}

		val matched = ShenzhiTables.assembly.match(ledger.snapshot())
		if (matched !is AssemblyMatch.Matched) {
			if (craftTicks != 0) {
				craftTicks = 0
				craftRecipe = null
				setChanged()
			}
			return
		}

		if (craftRecipe?.outputItemId != matched.recipe.outputItemId) {
			craftRecipe = matched.recipe
			craftTicks = 0
			setChanged()
		}

		craftTicks++
		if (craftTicks < durationTicks) return

		if (!ledger.tryConsume(matched.recipe.requirements)) {
			// 理论上不会发生（刚匹配成功）；保守处理：重置，不产出，避免刷物品。
			craftTicks = 0
			craftRecipe = null
			setChanged()
			return
		}

		craftTicks = 0
		craftRecipe = null
		setChanged()
		level.playSound(null, pos.x + 0.5, pos.y + 1.0, pos.z + 0.5, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.0f, 1.2f)
		if (ShenzhiMod.config.visual.particlesEnabled) {
			level.sendParticles(ParticleTypes.END_ROD, pos.x + 0.5, pos.y + 1.2, pos.z + 0.5, 30, 0.5, 0.5, 0.5, 0.08)
		}
		// 产物从**龙嘴向前**吐出（2026-10-02 反馈：像投掷器一样），而不是从头顶掉下来
		spawnForward(level, pos, ItemStacks.of(matched.recipe.outputItemId))
	}

	/** 非充能吐回：把账本内容全部实体化到方块上方。 */
	private fun ejectAll(level: ServerLevel, pos: BlockPos) {
		val contents = ledger.drainAll()
		if (contents.isEmpty()) return
		for ((path, amount) in contents) {
			spawn(level, pos, ItemStacks.ofMod(path, amount), ShenzhiMod.config.dragonHead.ejectVelocity)
		}
		// 标记吐出物：给吸收一个短暂的"冷静期"，避免高频红石把刚吐出的物品立刻吸回。
		absorbCooldown = EJECT_COOLDOWN_TICKS
		setChanged()
	}

	/** 方块被破坏时把账本内容掉落到地上（内容物不能丢，§4.2）。 */
	fun dropStoredContents() {
		val level = this.level as? ServerLevel ?: return
		val contents = ledger.drainAll()
		if (contents.isEmpty()) return
		for ((path, amount) in contents) {
			spawn(level, worldPosition, ItemStacks.ofMod(path, amount), 0.1)
		}
	}

	/**
	 * 方块被移除时的原版钩子（26.1）。
	 *
	 * ⚠️ 不要改用方块的 `affectNeighborsAfterRemoval`：实测那时方块实体已经取不到，
	 * 账本内容会凭空消失（这是踩过的坑）。也不要在方块里额外调用 `Containers.dropContents`。
	 */
	override fun preRemoveSideEffects(pos: BlockPos, state: BlockState) {
		super.preRemoveSideEffects(pos, state)
		dropStoredContents()
	}

	private fun spawn(level: ServerLevel, pos: BlockPos, stack: ItemStack, velocity: Double) {
		if (stack.isEmpty) return
		val entity = ItemEntity(level, pos.x + 0.5, pos.y + 1.0, pos.z + 0.5, stack)
		entity.setDefaultPickUpDelay()
		if (velocity > 0.0) {
			entity.deltaMovement = Vec3(
				(level.random.nextDouble() - 0.5) * velocity,
				velocity,
				(level.random.nextDouble() - 0.5) * velocity,
			)
		}
		level.addFreshEntity(entity)
	}

	/**
	 * 从龙嘴（朝向那一面的中部）向前吐出产物，模拟"从龙嘴吐出来"。
	 * 初速贴近投掷器的手感：水平 0.45、略微上抛 0.08。
	 */
	private fun spawnForward(level: ServerLevel, pos: BlockPos, stack: ItemStack) {
		if (stack.isEmpty) return
		val facing = level.getBlockState(pos).getValue(SyntheticDragonHeadBlock.FACING)
		val mouth = Vec3(
			pos.x + 0.5 + facing.stepX * 0.8,
			pos.y + 0.55,
			pos.z + 0.5 + facing.stepZ * 0.8,
		)
		val entity = ItemEntity(level, mouth.x, mouth.y, mouth.z, stack)
		entity.setDefaultPickUpDelay()
		entity.deltaMovement = Vec3(facing.stepX * 0.45, 0.08, facing.stepZ * 0.45)
		level.addFreshEntity(entity)
	}

	/** 右键查看：给出账本占用与合成进度。 */
	fun describeStatus(): Component {
		val capacity = ledger.usedSlots
		val recipe = craftRecipe?.outputItemId
		return if (recipe == null) {
			Component.translatable("message.shenzhimod.dragon_head.status", capacity, ShenzhiMod.config.dragonHead.ledgerCapacity)
		} else {
			Component.translatable("message.shenzhimod.dragon_head.crafting", recipe, craftTicks)
		}
	}

	override fun saveAdditional(output: ValueOutput) {
		super.saveAdditional(output)
		val ledgerOutput = output.child("Ledger")
		for ((path, amount) in ledger.snapshot()) {
			ledgerOutput.putInt(path, amount)
		}
		output.putInt("CraftTicks", craftTicks)
		craftRecipe?.let { output.putString("CraftRecipe", it.outputItemId) }
	}

	override fun loadAdditional(input: ValueInput) {
		super.loadAdditional(input)
		val values = mutableMapOf<String, Int>()
		val ledgerInput = input.childOrEmpty("Ledger")
		for (path in ShenzhiTables.compression.entries.map { it.outputPath }) {
			val amount = ledgerInput.getIntOr(path, 0)
			if (amount > 0) values[path] = amount
		}
		ledger = DragonHeadLedger(ShenzhiMod.config.dragonHead.ledgerCapacity)
		if (!ledger.restore(values)) {
			ShenzhiMod.logger.warn("合成龙首账本数据超出容量，已截断（位置 {}）", worldPosition)
		}
		craftTicks = input.getIntOr("CraftTicks", 0)
		val recipeId = input.getStringOr("CraftRecipe", "")
		craftRecipe = ShenzhiTables.assembly.firstOrNull { it.outputItemId == recipeId }
	}

	private companion object {
		/** 吐出后暂停吸收的 tick 数，替代"给实体打标记"的复杂方案。 */
		const val EJECT_COOLDOWN_TICKS = 20
	}
}
