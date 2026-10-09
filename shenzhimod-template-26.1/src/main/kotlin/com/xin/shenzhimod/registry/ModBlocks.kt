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
	/**
	 * 石灰岩：普通可放置方块（2026-09-30 反馈：它应该像其他石头一样能放下来）。
	 * 与其他原版石头同级的硬度/抗爆，需要镐才能正常掉落，音效与地图色偏暖灰。
	 */
	val LIMESTONE: Block = ModRegistry.block("limestone") { properties ->
		Block(
			properties
				.mapColor(MapColor.TERRACOTTA_WHITE)
				.strength(1.5f, 6.0f)
				.requiresCorrectToolForDrops()
				.sound(SoundType.STONE),
		)
	}

	val SYNTHETIC_DRAGON_HEAD: Block = ModRegistry.block("synthetic_dragon_head") { properties ->
		// 机器方块不设"必须用镐"：生存里需要频繁拆装/复测，掉率也不该被工具卡住。
		// `noOcclusion()`：龙首本体只渲染一个头颅模型（方块模型是空的），
		// 如果照常遮挡，旁边方块朝向它的那一面会被剔除、看起来像"接触面消失了"（2026-09-30 反馈 #1）。
		// 不遮挡后邻居的面照常渲染，头颅模型再盖在前面，视觉上就没有洞。
		SyntheticDragonHeadBlock(
			properties
				.mapColor(MapColor.COLOR_BLACK)
				.strength(2.0f, 6.0f)
				.noOcclusion()
				// 充能就微亮、合成中更亮：这样"充能/合成"在视觉上一定看得出来（发光由方块状态驱动，与渲染器无关）
				.lightLevel { state ->
					when {
						state.getValue(SyntheticDragonHeadBlock.ACTIVE) -> 15
						state.getValue(SyntheticDragonHeadBlock.POWERED) -> 9
						else -> 0
					}
				}
				.sound(SoundType.STONE),
		)
	}

	val TRANSMUTATION_CORE: Block = ModRegistry.block("transmutation_core") { properties ->
		TransmutationCoreBlock(
			properties
				.mapColor(MapColor.COLOR_PURPLE)
				.strength(2.0f, 6.0f)
				.sound(SoundType.AMETHYST)
				// 工作时更亮：远处也能一眼看出这台机器在干活
				.lightLevel { state ->
					if (state.getValue(TransmutationCoreBlock.LIT)) CORE_LIT_LIGHT_LEVEL else LIGHT_LEVEL
				},
		)
	}

	val ULTIMATE_ALTAR: Block = ModRegistry.block("ultimate_altar") { properties ->
		UltimateAltarBlock(
			properties
				.mapColor(MapColor.COLOR_BLACK)
				.strength(3.0f, 12.0f)
				.sound(SoundType.STONE)
				// 四档亮度：底座不对 / 空转 / 三核就位 / 融合中
				.lightLevel { state ->
					when (state.getValue(UltimateAltarBlock.STATE)) {
						UltimateAltarBlock.AltarState.ACTIVE -> ALTAR_ACTIVE_LIGHT_LEVEL
						UltimateAltarBlock.AltarState.READY -> ALTAR_READY_LIGHT_LEVEL
						UltimateAltarBlock.AltarState.INVALID -> ALTAR_INVALID_LIGHT_LEVEL
						else -> LIGHT_LEVEL
					}
				},
		)
	}

	/** 注册顺序稳定，供数据生成与测试遍历。 */
	val ALL: List<Block> = listOf(LIMESTONE, SYNTHETIC_DRAGON_HEAD, TRANSMUTATION_CORE, ULTIMATE_ALTAR)

	/** 机器方块自发光强度。 */
	private const val LIGHT_LEVEL = 7

	/** 转化核心工作时的自发光强度。 */
	private const val CORE_LIT_LIGHT_LEVEL = 12

	/** 祭坛待启动时的自发光强度。 */
	private const val ALTAR_READY_LIGHT_LEVEL = 9

	/** 祭坛融合中的自发光强度。 */
	private const val ALTAR_ACTIVE_LIGHT_LEVEL = 13

	/** 祭坛底座不对时的自发光强度（比空转还暗一点，避免看起来像在工作）。 */
	private const val ALTAR_INVALID_LIGHT_LEVEL = 5

	internal fun bootstrap(): List<Block> {
		ShenzhiMod.logger.debug("已注册 {} 个方块", ALL.size)
		return ALL
	}
}
