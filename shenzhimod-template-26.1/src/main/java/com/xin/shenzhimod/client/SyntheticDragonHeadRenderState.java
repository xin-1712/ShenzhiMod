package com.xin.shenzhimod.client;

import net.minecraft.client.renderer.blockentity.state.SkullBlockRenderState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * 合成龙首的渲染状态：在原版头颅渲染状态上多两个字段（朝向角、是否正在合成）。
 *
 * 这个文件（以及渲染器）用 Java 写，是因为 26.1 的头颅模型位于 `net.minecraft.client.model.**object**.skull`，
 * 而 `object` 是 Kotlin 关键字，Kotlin 无法 import 这个包名。
 */
@Environment(EnvType.CLIENT)
public class SyntheticDragonHeadRenderState extends SkullBlockRenderState {
	public float facingDegrees;
	/** 是否已充能：眼睛亮起。 */
	public boolean powered;
	/** 是否正在合成：眼睛变金色 + 下颚动起来。 */
	public boolean crafting;
}
