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
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
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
			return
		}

		if (absorbCooldown > 0) absorbCooldown--

		tickCounter++
		if (tickCounter % config.dragonHead.absorbIntervalTicks == 0 && absorbCooldown <= 0) {
			absorbNearby(level, pos)
		}

		tickCrafting(level, pos, config.dragonHead.craftDurationTicks)
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
		spawn(level, pos, ItemStacks.of(matched.recipe.outputItemId), 0.2)
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
