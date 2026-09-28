package com.xin.shenzhimod.registry

import com.xin.shenzhimod.ShenzhiMod
import com.xin.shenzhimod.content.block.SyntheticDragonHeadBlock
import com.xin.shenzhimod.content.block.TransmutationCoreBlock
import com.xin.shenzhimod.content.block.UltimateAltarBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.material.MapColor

/**
 * 方块注册（模块 B / C / E）。
 *
 * 注意：这里**只注册方块本身**，对应的 BlockItem 在 [ModItems] 里注册，
 * 避免两个 object 相互触发初始化造成顺序问题。
 */
object ModBlocks {
	val SYNTHETIC_DRAGON_HEAD: Block = ModRegistry.block("synthetic_dragon_head") { properties ->
		SyntheticDragonHeadBlock(
			properties
				.mapColor(MapColor.COLOR_BLACK)
				.strength(3.5f, 6.0f)
				.sound(SoundType.STONE)
				.requiresCorrectToolForDrops(),
		)
	}

	val TRANSMUTATION_CORE: Block = ModRegistry.block("transmutation_core") { properties ->
		TransmutationCoreBlock(
			properties
				.mapColor(MapColor.COLOR_PURPLE)
				.strength(3.5f, 6.0f)
				.sound(SoundType.AMETHYST)
				.requiresCorrectToolForDrops()
				.lightLevel { LIGHT_LEVEL },
		)
	}

	val ULTIMATE_ALTAR: Block = ModRegistry.block("ultimate_altar") { properties ->
		UltimateAltarBlock(
			properties
				.mapColor(MapColor.COLOR_BLACK)
				.strength(5.0f, 12.0f)
				.sound(SoundType.STONE)
				.requiresCorrectToolForDrops()
				.lightLevel { LIGHT_LEVEL },
		)
	}

	/** 注册顺序稳定，供数据生成与测试遍历。 */
	val ALL: List<Block> = listOf(SYNTHETIC_DRAGON_HEAD, TRANSMUTATION_CORE, ULTIMATE_ALTAR)

	/** 机器方块自发光强度。 */
	private const val LIGHT_LEVEL = 7

	internal fun bootstrap(): List<Block> {
		ShenzhiMod.logger.debug("已注册 {} 个方块", ALL.size)
		return ALL
	}
}
