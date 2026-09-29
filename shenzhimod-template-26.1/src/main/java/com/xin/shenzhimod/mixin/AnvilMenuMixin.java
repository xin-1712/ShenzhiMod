package com.xin.shenzhimod.mixin;

import com.xin.shenzhimod.interaction.AnvilEnchantHandler;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 核心附魔（模块 F）在铁砧上的注入点，对应需求 §4.6 与 §6.3-F。
 *
 * 做法是**只替换"右槽是本模组核心"这一条分支**：
 * - 命中核心附魔规则时，在 `createResult` 的 HEAD 直接写入结果与固定成本并取消原方法，
 *   因此原版的 `>= 40 级过于昂贵` 判定、按公式算成本、写 `repair_cost` 都不会发生；
 * - 未命中（右槽不是核心 / 类型不匹配 / 已有同等级附魔）时**原样返回**，
 *   原版铁砧逻辑一字不改——这也保证了"普通铁砧仍然 40 级上限"。
 *
 * 取出结算不需要另外注入：原版 `AnvilMenu#onTake` 已经做了"扣等级 + 消耗右槽 + 消耗/损坏铁砧"，
 * 我们只要把 `repairItemCountCost` 设为 1，就能保证只消耗 **1 个**媒介物（而不是整摞）。
 * 创造模式的行为也因此天然与原版一致：消耗核心、不扣经验。
 *
 * 注意：本方法在客户端也会跑一遍（原版铁砧本来就是双端各算一次，客户端用于即时显示），
 * 判定逻辑与输入完全一致，因此两端结果相同。
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {
	@Shadow
	private int repairItemCountCost;

	@Shadow
	@Final
	private DataSlot cost;

	@Shadow
	private @Nullable String itemName;

	@Inject(method = "createResult", at = @At("HEAD"), cancellable = true)
	private void shenzhimod$coreEnchantResult(CallbackInfo ci) {
		AnvilMenu menu = (AnvilMenu) (Object) this;
		ItemStack target = menu.getSlot(AnvilMenu.INPUT_SLOT).getItem();
		ItemStack media = menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).getItem();
		AnvilEnchantHandler.Outcome outcome = AnvilEnchantHandler.INSTANCE.computeResult(
				target,
				media,
				((ItemCombinerMenuAccessor) (Object) this).shenzhimod$getPlayer(),
				this.itemName
		);
		if (outcome == null) {
			// 不是核心附魔：交回原版逻辑，普通铁砧行为保持不变。
			return;
		}

		menu.getSlot(AnvilMenu.RESULT_SLOT).set(outcome.getResult());
		this.cost.set(outcome.getCostLevels());
		// 1 = 取出时只消耗 1 个媒介物（原版 onTake 按这个数量扣右槽，而不是把整摞扣光）。
		this.repairItemCountCost = 1;
		menu.broadcastChanges();
		ci.cancel();
	}

	/**
	 * 原版"合书/合物品"分支会把等级夹到附魔上限（锋利 = 5），会把本模组写出的超上限等级**降级**
	 * （需求 §4.6"同种附魔取更高等级，上限 grantedLevel"）。这里在方法尾部做一次只增不减的修正，
	 * 并重新同步结果槽——只抬高"输入本来就有的等级"，对原版物品没有任何影响。
	 *
	 * 注意：`@At("TAIL")` 只挂在方法末尾（正常走到 `broadcastChanges` 的那条路径），
	 * 前置的"无结果"分支本来就返回空堆，不需要修正；核心附魔分支在 HEAD 已 `cancel`，也不会走到这里。
	 */
	@Inject(method = "createResult", at = @At("TAIL"))
	private void shenzhimod$noEnchantDowngrade(CallbackInfo ci) {
		AnvilMenu menu = (AnvilMenu) (Object) this;
		ItemStack input = menu.getSlot(AnvilMenu.INPUT_SLOT).getItem();
		ItemStack result = menu.getSlot(AnvilMenu.RESULT_SLOT).getItem();
		if (AnvilEnchantHandler.INSTANCE.enforceNoDowngrade(input, result)) {
			menu.broadcastChanges();
		}
	}
}
