package com.xin.shenzhimod.entity

import com.xin.shenzhimod.ShenzhiMod
import net.minecraft.core.BlockPos
import net.minecraft.util.RandomSource
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.SpawnPlacements
import net.minecraft.world.entity.monster.piglin.Piglin
import net.minecraft.world.entity.monster.piglin.PiglinBrute
import net.minecraft.world.level.NaturalSpawner
import net.minecraft.world.level.ServerLevelAccessor

/**
 * 猪灵蛮兵的生成谓词（模块 J）。
 *
 * 与 Carpet Sky Additions 的字面一致（§6.8.3，MIT 归属见仓库 LICENSE）：
 * 1. 下方方块必须 `isValidSpawn`；
 * 2. 生成位与上方必须 `isValidEmptySpawnBlock`；
 * 3. 复用 `Piglin.checkPiglinSpawnRules` —— 即"下方不能是下界疣块"。
 *
 * 唯一改动：把 Carpet 的 `CarpetSettings.piglinsSpawningInBastions` 换成本模组配置项
 * `piglinBrute.spawningInBastions`（默认开，与空岛一致）。
 * 关闭配置时直接拒绝生成，等价于把堡垒刷怪关掉。
 */
class PiglinBruteSpawnPredicate : SpawnPlacements.SpawnPredicate<PiglinBrute> {
	override fun test(
		type: EntityType<PiglinBrute>,
		level: ServerLevelAccessor,
		spawnReason: EntitySpawnReason,
		pos: BlockPos,
		random: RandomSource,
	): Boolean {
		if (!ShenzhiMod.config.piglinBrute.spawningInBastions) return false

		val underPos = pos.below()
		val underBlock = level.getBlockState(underPos)
		if (!underBlock.isValidSpawn(level, underPos, type)) return false

		val abovePos = pos.above()
		return NaturalSpawner.isValidEmptySpawnBlock(
			level,
			pos,
			level.getBlockState(pos),
			level.getFluidState(pos),
			type,
		) && NaturalSpawner.isValidEmptySpawnBlock(
			level,
			abovePos,
			level.getBlockState(abovePos),
			level.getFluidState(abovePos),
			type,
		) && Piglin.checkPiglinSpawnRules(EntityType.PIGLIN, level, spawnReason, pos, random)
	}
}
