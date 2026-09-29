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

		// 熔炉贴图在左列自带了三个"零件"：左上输入槽、中间火焰轮廓、下方燃料槽。
		// 本机只有两个输入（物品 + 催化剂）且输入槽在箭头左侧，所以把**整条左列**用界面底色盖掉，
		// 再自己画两个槽位框——否则会留下一堆用不了的假槽位（这是实测反馈）。
		graphics.fill(xo + 55, yo + 16, xo + 75, yo + 72, GUI_BACKGROUND)

		// 输入槽与催化剂槽所在的位置在熔炉贴图里没有槽位框，自己画
		drawSlotFrame(graphics, xo + TransmutationCoreMenu.INPUT_X, yo + TransmutationCoreMenu.INPUT_Y)
		drawSlotFrame(graphics, xo + TransmutationCoreMenu.CATALYST_X, yo + TransmutationCoreMenu.CATALYST_Y)

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
		fun drawSlotFrame(graphics: GuiGraphicsExtractor, x: Int, y: Int) {
			graphics.fill(x - 1, y - 1, x + 17, y + 17, SLOT_BORDER)
			graphics.fill(x, y, x + 16, y + 16, SLOT_INNER)
		}

		val BACKGROUND: Identifier = Identifier.withDefaultNamespace("textures/gui/container/furnace.png")
		val ARROW_SPRITE: Identifier = Identifier.withDefaultNamespace("container/furnace/burn_progress")

		/** 原版物品槽的配色：外深内浅。 */
		const val SLOT_BORDER = 0xFF373737.toInt()
		const val SLOT_INNER = 0xFF8B8B8B.toInt()

		/** 原版容器界面的标准底色（用于盖掉熔炉贴图里多余的燃料槽与火焰轮廓）。 */
		const val GUI_BACKGROUND = 0xFFC6C6C6.toInt()
	}
}
