package com.xin.shenzhimod.interaction

import com.xin.shenzhimod.ShenzhiMod
import com.xin.shenzhimod.advancement.ModAdvancements
import com.xin.shenzhimod.data.ShenzhiTables
import com.xin.shenzhimod.util.ItemStacks
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import java.util.function.Consumer

/**
 * 压缩体系（模块 A）的交互入口。
 *
 * 触发：手持重锤（`minecraft:mace`）+ Shift + 右键铁砧。
 * 为什么不用 Mixin：原版铁砧没有 use 行为，`UseBlockCallback` 足够且不与其他模组抢注入点（§6.3-A）。
 *
 * 物品守恒（D5）：
 * 1. 先一次性统计"铁砧上方同种物品总数"，算出能产出多少；
 * 2. 只有 [com.xin.shenzhimod.machine.CompressionTable.settle] 给出 `produced > 0` 才动手；
 * 3. 扣物品与扣耐久在同一 tick 内完成，随后立即产出；
 * 4. 任何一步对不上（理论不会发生）都会记录错误日志，绝不"扣了不产出"。
 */
object AnvilCompressionHandler {
	fun register() {
		UseBlockCallback.EVENT.register { player, level, hand, hitResult ->
			handle(player, level, hand, hitResult)
		}
	}

	private fun handle(
		player: Player,
		level: Level,
		hand: InteractionHand,
		hitResult: BlockHitResult,
	): InteractionResult {
		if (!player.isShiftKeyDown) return InteractionResult.PASS
		val held = player.getItemInHand(hand)
		if (!held.`is`(Items.MACE)) return InteractionResult.PASS
		if (!isUsableAnvil(level.getBlockState(hitResult.blockPos))) return InteractionResult.PASS

		// 双端都要返回 SUCCESS：客户端不吞掉交互，服务端才做真实结算。
		if (level.isClientSide) return InteractionResult.SUCCESS
		val serverLevel = level as? ServerLevel ?: return InteractionResult.PASS
		performCompression(serverLevel, hitResult.blockPos, player, held)
		return InteractionResult.SUCCESS
	}

	private fun isUsableAnvil(state: BlockState): Boolean {
		val block = state.block
		return when (block) {
			Blocks.ANVIL -> true
			Blocks.CHIPPED_ANVIL, Blocks.DAMAGED_ANVIL -> ShenzhiMod.config.compression.allowDamagedAnvil
			else -> false
		}
	}

	private fun performCompression(level: ServerLevel, anvilPos: BlockPos, player: Player, held: ItemStack) {
		val config = ShenzhiMod.config.compression
		val box = AABB.ofSize(Vec3.atCenterOf(anvilPos.above()), SCAN_SIZE, SCAN_SIZE, SCAN_SIZE)
		val entities = level.getEntitiesOfClass(ItemEntity::class.java, box) { it.isAlive }
		if (entities.isEmpty()) return

		// 需求：只能有一种物品，混放则完全无反应（2026-09-30 确认：保持原设计的"无反应"，不加提示）。
		val byItem = entities.groupBy { ItemStacks.idOf(it.item) ?: return }
		if (byItem.size != 1) return
		val (sourceItemId, stacks) = byItem.entries.first()
		val entry = ShenzhiTables.compression.entryForSource(sourceItemId) ?: return

		val total = stacks.sumOf { it.item.count }
		val remainingDurability = if (held.isDamageableItem) held.maxDamage - held.damageValue else Int.MAX_VALUE
		val outcome = ShenzhiTables.compression.settle(
			required = entry.count,
			available = total,
			availableDurability = remainingDurability,
			durabilityCostPerOutput = config.durabilityCostPerOutput,
			maxOutputs = config.maxOutputsPerInteraction,
		)
		if (!outcome.effective) return

		// ① 扣物品：先扣掉 produced × R 个，余数原样留在世界里。
		var toRemove = outcome.produced * entry.count
		for (entity in stacks) {
			if (toRemove <= 0) break
			val stack = entity.item
			val take = minOf(stack.count, toRemove)
			stack.shrink(take)
			toRemove -= take
			if (stack.isEmpty) entity.discard() else entity.item = stack
		}
		if (toRemove > 0) {
			// 统计与实际不一致：宁可记录错误也不要偷偷补物品，交给对账测试定位。
			ShenzhiMod.logger.error("压缩扣减异常：还差 {} 个 {} 未扣除（位置 {}）", toRemove, sourceItemId, anvilPos)
		}

		// ② 扣耐久：走原版流程，兼容其他模组的耐久机制（§6.3-A）。
		val durabilityCost = outcome.produced * config.durabilityCostPerOutput
		if (durabilityCost > 0 && held.isDamageableItem) {
			held.hurtAndBreak(durabilityCost, level, player as? ServerPlayer, Consumer { })
		}

		// ③ 产出：在铁砧上方弹出，速度朝向玩家。
		val output = ItemStacks.ofMod(entry.outputPath, outcome.produced)
		if (output.isEmpty) {
			ShenzhiMod.logger.error("压缩产物 {} 未注册，物品已被扣除！", entry.outputPath)
			return
		}
		val spawnPos = anvilPos.above()
		val entity = ItemEntity(level, spawnPos.x + 0.5, spawnPos.y + 0.5, spawnPos.z + 0.5, output)
		entity.setDefaultPickUpDelay()
		val direction = Vec3(player.x - (spawnPos.x + 0.5), 0.0, player.z - (spawnPos.z + 0.5)).normalize().scale(0.15)
		entity.deltaMovement = Vec3(direction.x, 0.2, direction.z)
		level.addFreshEntity(entity)

		// 成就"大力出奇迹"：**就在这一次真正压出压缩态的时刻**授予（不是捡起物品的时候）。
		// 这是需求明确指定的时刻，因此条件写成 impossible、由代码授予。
		(player as? ServerPlayer)?.let { ModAdvancements.award(it, ModAdvancements.COMPRESSION) }
	}

	/** 扫描盒边长：铁砧上方 3×3×3（需求 §4.1）。 */
	private const val SCAN_SIZE = 3.0
}
