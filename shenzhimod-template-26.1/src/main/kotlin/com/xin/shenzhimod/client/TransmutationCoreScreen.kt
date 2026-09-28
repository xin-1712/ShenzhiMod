package com.xin.shenzhimod.client

import com.xin.shenzhimod.content.menu.TransmutationCoreMenu
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Inventory

/**
 * 转化核心界面：熔炉式布局，箭头就是 10 秒进度条。
 *
 * 贴图暂时借用原版熔炉的界面贴图（`textures/gui/container/furnace.png`）与进度精灵，
 * 这样**不需要先做 GUI 贴图**就能跑；将来画好专属贴图后，只要换掉 [BACKGROUND] 与两个精灵常量。
 *
 * 26.1 的界面绘制走新的"抽取"管线：重写 [extractBackground]，用 `GuiGraphicsExtractor` 的
 * `blit` / `blitSprite` 绘制；旧版 `renderBg(GuiGraphics, ...)` 在这个版本已不存在。
 */
@Environment(EnvType.CLIENT)
class TransmutationCoreScreen(
	menu: TransmutationCoreMenu,
	inventory: Inventory,
	title: Component,
) : AbstractContainerScreen<TransmutationCoreMenu>(menu, inventory, title) {

	override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick)
		val xo = leftPos
		val yo = topPos

		// 背景（原版熔炉界面，占位）
		graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, xo, yo, 0.0f, 0.0f, imageWidth, imageHeight, 256, 256)

		// 催化剂槽：原版熔炉背景这个位置没有槽位框，自己画一个
		val slotX = xo + TransmutationCoreMenu.CATALYST_X
		val slotY = yo + TransmutationCoreMenu.CATALYST_Y
		graphics.fill(slotX - 1, slotY - 1, slotX + 17, slotY + 17, SLOT_BORDER)
		graphics.fill(slotX, slotY, slotX + 16, slotY + 16, SLOT_INNER)

		// 进度箭头：宽度按进度增长
		val progressWidth = Mth.ceil(menu.progressFraction() * TransmutationCoreMenu.ARROW_WIDTH.toFloat())
		if (progressWidth > 0) {
			graphics.blitSprite(
				RenderPipelines.GUI_TEXTURED,
				ARROW_SPRITE,
				TransmutationCoreMenu.ARROW_WIDTH,
				TransmutationCoreMenu.ARROW_HEIGHT,
				0,
				0,
				xo + TransmutationCoreMenu.ARROW_X,
				yo + TransmutationCoreMenu.ARROW_Y,
				progressWidth,
				TransmutationCoreMenu.ARROW_HEIGHT,
			)
		}
	}

	private companion object {
		val BACKGROUND: Identifier = Identifier.withDefaultNamespace("textures/gui/container/furnace.png")
		val ARROW_SPRITE: Identifier = Identifier.withDefaultNamespace("container/furnace/burn_progress")

		/** 原版物品槽的配色：外深内浅。 */
		const val SLOT_BORDER = 0xFF373737.toInt()
		const val SLOT_INNER = 0xFF8B8B8B.toInt()
	}
}
