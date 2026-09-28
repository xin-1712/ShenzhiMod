package com.xin.shenzhimod.entity

import com.xin.shenzhimod.ShenzhiMod
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.SpawnPlacementTypes
import net.minecraft.world.entity.SpawnPlacements
import net.minecraft.world.level.levelgen.Heightmap

/**
 * 猪灵蛮兵的生成规则接线（模块 J）。
 *
 * 需要说明的一点：原版 `SpawnPlacements` **没有**注册 `PIGLIN_BRUTE`
 * （26.1 字节码已核实：只有 PIGLIN 与 ZOMBIFIED_PIGLIN），所以默认值是
 * `NO_RESTRICTIONS` + 允许生成。我们注册谓词后，堡垒内的蛮兵才会按空岛规则出现。
 *
 * `SpawnPlacements.register` 是包私有方法，由 Fabric 的
 * `fabric-transitive-access-wideners-v1` 通过 class tweaker 放开（§6.8.3），
 * 不需要我们自带 access widener。
 *
 * 采用 try/catch 而不是硬崩：如果别的模组抢先注册了 PIGLIN_BRUTE，
 * 原版会抛 `IllegalStateException`，我们只记录警告并放弃覆盖，避免整个游戏起不来。
 */
object PiglinBruteSpawnRules {
	fun register() {
		if (!ShenzhiMod.config.piglinBrute.spawningInBastions) {
			ShenzhiMod.logger.info("配置已关闭堡垒猪灵蛮兵刷怪，跳过生成谓词注册")
			return
		}
		try {
			SpawnPlacements.register(
				EntityType.PIGLIN_BRUTE,
				SpawnPlacementTypes.NO_RESTRICTIONS,
				Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				PiglinBruteSpawnPredicate(),
			)
		} catch (error: IllegalStateException) {
			ShenzhiMod.logger.warn("猪灵蛮兵生成谓词已被其他模组注册，已跳过覆盖：{}", error.message)
		}
	}
}
