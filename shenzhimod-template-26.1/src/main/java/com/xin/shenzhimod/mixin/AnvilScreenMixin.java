package com.xin.shenzhimod.mixin;

import com.xin.shenzhimod.interaction.AnvilEnchantHandler;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.inventory.AnvilMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 铁砧界面的成本显示（模块 F，仅客户端）。
 *
 * 原版 `AnvilScreen#extractLabels` 里写死了"成本 ≥ 40 就显示'过于昂贵!'"，
 * 而本模组的核心附魔成本是**固定的 100 / 200 级**并且是合法可付的，
 * 因此必须把这条分支改成正常的"附魔花费：N"。
 *
 * 做法（完全不碰原版渲染代码，只改两个取值）：
 * 1. 把本次绘制用的 `cost` 降到 40 以下 → 原版跳过"过于昂贵"分支，走到
 *    "有结果槽 → 画花费行"的正常分支；
 * 2. 把画花费行时的参数换回**真实成本**（第 1 步只是骗过上限判定，不能真的显示 39）。
 *
 * 这样颜色逻辑也顺带正确：付得起是普通色，付不起由原版按 `mayPickup` 标红。
 */
@Mixin(AnvilScreen.class)
public abstract class AnvilScreenMixin {
	@Redirect(
			method = "extractLabels",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/AnvilMenu;getCost()I")
	)
	private static int shenzhimod$hideTooExpensiveLine(AnvilMenu menu) {
		int cost = menu.getCost();
		// 40 是原版"过于昂贵"的阈值；核心附魔时先降到 39 骗过该分支，
		// 真正显示的数字由下面的 translatable 重定向补回。
		if (cost >= 40 && AnvilEnchantHandler.INSTANCE.isCoreEnchantResult(menu)) {
			return 39;
		}
		return cost;
	}

	@Redirect(
			method = "extractLabels",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/network/chat/Component;translatable(Ljava/lang/String;[Ljava/lang/Object;)Lnet/minecraft/network/chat/MutableComponent;"
			)
	)
	private MutableComponent shenzhimod$showRealCost(String key, Object[] args) {
		AnvilScreen screen = (AnvilScreen) (Object) this;
		AnvilMenu menu = screen.getMenu();
		if (AnvilEnchantHandler.INSTANCE.isCoreEnchantResult(menu)) {
			return Component.translatable(key, menu.getCost());
		}
		return Component.translatable(key, args);
	}
}
