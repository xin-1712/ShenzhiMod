package com.xin.shenzhimod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.xin.shenzhimod.content.block.SyntheticDragonHeadBlock;
import com.xin.shenzhimod.content.blockentity.SyntheticDragonHeadBlockEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.skull.DragonHeadModel;
import net.minecraft.client.model.object.skull.SkullModelBase;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 合成龙首的渲染器（2026-09-30 反馈 #2）：它应当**和原版龙首一样是头颅模型**，而不是一个立方体。
 *
 * 做法是复刻原版 {@link SkullBlockRenderer}：
 * <ul>
 *   <li>模型直接用原版的 {@link ModelLayers#DRAGON_SKULL}（不必自己写模型/贴图布局）；</li>
 *   <li>底图换成我们自己的 {@code entity/dragon_head.png}——原版龙贴图的副本，**眼睛换成了指定颜色**；</li>
 *   <li>再叠一层只有眼睛的贴图并**用全亮光渲染**，眼睛就会"发亮"；</li>
 *   <li>方块状态 {@code active}（正在倒计时合成）时：下颚跟着动、眼睛换成炽热色——这样合成过程有明显变化。</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public class SyntheticDragonHeadRenderer
		implements BlockEntityRenderer<SyntheticDragonHeadBlockEntity, SyntheticDragonHeadRenderState> {
	/** 未充能：原版身体贴图（眼睛就是原版龙首的紫色）。 */
	private static final Identifier BODY_TEXTURE = texture("dragon_head");
	/** 充能：身体贴图的眼镜换成亮起的粉紫。 */
	private static final Identifier BODY_CHARGED_TEXTURE = texture("dragon_head_charged");
	/** 合成中：身体贴图的眼睛换成两色金。 */
	private static final Identifier BODY_GOLD_TEXTURE = texture("dragon_head_gold");
	/** 充能时叠加的自发光眼睛层（同样的两色粉紫，全亮）。 */
	private static final Identifier EYES_TEXTURE = texture("dragon_head_eyes");
	/** 合成时叠加的自发光眼睛层（两色金，全亮）。 */
	private static final Identifier EYES_GOLD_TEXTURE = texture("dragon_head_eyes_gold");

	private final SkullModelBase model;
	private final RenderType bodyType;
	private final RenderType bodyChargedType;
	private final RenderType bodyGoldType;
	private final RenderType eyesType;
	private final RenderType eyesGoldType;

	public SyntheticDragonHeadRenderer(final BlockEntityRendererProvider.Context context) {
		this.model = new DragonHeadModel(context.entityModelSet().bakeLayer(ModelLayers.DRAGON_SKULL));
		this.bodyType = SkullBlockRenderer.getSkullRenderType(SkullBlock.Types.DRAGON, BODY_TEXTURE);
		this.bodyChargedType = SkullBlockRenderer.getSkullRenderType(SkullBlock.Types.DRAGON, BODY_CHARGED_TEXTURE);
		this.bodyGoldType = SkullBlockRenderer.getSkullRenderType(SkullBlock.Types.DRAGON, BODY_GOLD_TEXTURE);
		this.eyesType = SkullBlockRenderer.getSkullRenderType(SkullBlock.Types.DRAGON, EYES_TEXTURE);
		this.eyesGoldType = SkullBlockRenderer.getSkullRenderType(SkullBlock.Types.DRAGON, EYES_GOLD_TEXTURE);
	}

	@Override
	public SyntheticDragonHeadRenderState createRenderState() {
		return new SyntheticDragonHeadRenderState();
	}

	@Override
	public void extractRenderState(
			final SyntheticDragonHeadBlockEntity blockEntity,
			final SyntheticDragonHeadRenderState state,
			final float partialTicks,
			final Vec3 cameraPosition,
			final ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress
	) {
		BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
		BlockState blockState = blockEntity.getBlockState();
		// 模型默认朝北（-Z），按方块朝向补一个 Y 旋转，让它对着放置时玩家的方向
		state.facingDegrees = blockState.getValue(SyntheticDragonHeadBlock.FACING).toYRot() - 180.0F;
		// 三档表现（2026-10-02 反馈）：
		// 未充能 → 只有身体贴图（眼睛就是原版龙首的样子，暗淡）；
		// 充能 → 叠一层提亮的眼睛（亮起）；正在合成 → 叠金色眼睛 + 下颚动。
		state.powered = blockState.getValue(SyntheticDragonHeadBlock.POWERED);
		// 金色档必须"充能 + 合成中"同时成立：放置瞬间 POWERED 一定是 false，
		// 因此不会再出现"刚放下闪一下金色"（2026-10-02 反馈）。
		state.crafting = state.powered && blockState.getValue(SyntheticDragonHeadBlock.ACTIVE);
		// 下颚动画：充能（机器运转）时一开一合；合成中更明显
		state.animationProgress = state.powered
				? (blockEntity.getLevel() != null ? blockEntity.getLevel().getGameTime() : 0L) + partialTicks
				: 0.0F;
	}

	@Override
	public void submit(
			final SyntheticDragonHeadRenderState state,
			final PoseStack poseStack,
			final SubmitNodeCollector submitNodeCollector,
			final CameraRenderState camera
	) {
		poseStack.pushPose();
		// 与原版头颅一致：先移到方块中心，再朝向，最后按模型的"Y 轴向下"坐标系翻转
		poseStack.translate(0.5, 0.0, 0.5);
		poseStack.mulPose(Axis.YP.rotationDegrees(state.facingDegrees));
		poseStack.scale(-1.0F, -1.0F, 1.0F);

		SkullBlockRenderer.submitSkull(
				state.animationProgress, poseStack, submitNodeCollector, state.lightCoords, this.model,
				state.crafting ? this.bodyGoldType : (state.powered ? this.bodyChargedType : this.bodyType),
				0, state.breakProgress);
		if (state.crafting || state.powered) {
			// 眼睛单独一层 + 全亮 → 自发光；略微放大一点点，避免与身体层同深度打架
			poseStack.pushPose();
			poseStack.scale(1.002F, 1.002F, 1.002F);
			SkullBlockRenderer.submitSkull(
					state.animationProgress, poseStack, submitNodeCollector, LightCoordsUtil.FULL_BRIGHT, this.model,
					state.crafting ? this.eyesGoldType : this.eyesType, 0, null);
			poseStack.popPose();
		}
		poseStack.popPose();
	}

	private static Identifier texture(final String path) {
		return Identifier.fromNamespaceAndPath("shenzhimod", "textures/entity/" + path + ".png");
	}
}
