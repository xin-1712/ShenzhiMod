package com.xin.shenzhimod.mixin;

import com.xin.shenzhimod.ShenzhiMod;
import com.xin.shenzhimod.ability.ElytraFlightAnimation;
import com.xin.shenzhimod.interaction.FlightEnchantService;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 飞行附魔鞘翅的"飞行中扇动"动画（仅客户端，纯表现）。
 *
 * 需求：穿飞行附魔鞘翅**飞行时**（`abilities.flying`）要有动画，可以简单但不能一直不动；
 * 这段表现**与滑翔无关**——滑翔交给原版姿态，我们只在"本模组飞行"时补上翼角摆动。
 *
 * 为什么注入 `AvatarRenderer#extractRenderState`：
 * - 玩家的鞘翅由 `WingsLayer` 用 `ElytraModel` 渲染，模型读的是渲染状态里的
 *   `elytraRotX/Y/Z`（`HumanoidRenderState` 的字段）；原版只给生物填这三个值，
 *   玩家恒为 0，所以玩家的鞘翅在飞的时候完全不动；
 * - 这个方法同时拿得到**实体**（用来判断"是不是玩家正在飞"）和**渲染状态**（写翼角），
 *   而且是每帧调用、在图层提交之前，改完立刻生效。
 *
 * 只改"我们的飞行 + 带我们的飞行附魔 + 不在滑翔"这一种情况，其余一律不动。
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererFlightAnimationMixin {
	@Inject(
			method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
			at = @At("TAIL")
	)
	private void shenzhimod$animateElytraWhileFlying(
			Avatar entity,
			AvatarRenderState state,
			float partialTicks,
			CallbackInfo ci
	) {
		// 滑翔有原版自己的姿态，与本动画互不干涉（需求："和滑翔状态无关"）。
		if (state.isFallFlying) {
			return;
		}
		if (!ShenzhiMod.INSTANCE.getConfig().getVisual().getElytraFlightAnimation()) {
			return;
		}
		if (!(entity instanceof Player player)) {
			return;
		}
		// 只认"玩家真的在飞"：创造/旁观原本也能飞，但他们的鞘翅不走本动画。
		if (!player.getAbilities().flying || player.isSpectator()) {
			return;
		}
		// 必须穿的是带飞行附魔的鞘翅（胸甲槽）。
		if (!FlightEnchantService.INSTANCE.hasFlightEnchant(state.chestEquipment)) {
			return;
		}

		ElytraFlightAnimation.WingAngles angles =
				ElytraFlightAnimation.INSTANCE.angles(state.ageInTicks);
		state.elytraRotX = angles.getRotX();
		state.elytraRotY = angles.getRotY();
		state.elytraRotZ = angles.getRotZ();
	}
}
