package com.xin.shenzhimod.registry;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * 方块实体类型工厂（Java 侧）。
 *
 * 存在的唯一原因：Fabric 的 builder 位于 `net.fabricmc.fabric.api.object.builder.v1...`，
 * 其中 `object` 是 Kotlin 关键字，Kotlin 源码里无法直接 `import` 那个包。
 * 用 Java 写一个薄封装，比在 Kotlin 里给包名加反引号可读得多。
 */
public final class BlockEntityTypes {
	private BlockEntityTypes() {
	}

	@SafeVarargs
	public static <T extends BlockEntity> BlockEntityType<T> create(
			FabricBlockEntityTypeBuilder.Factory<T> factory,
			Block... blocks) {
		return FabricBlockEntityTypeBuilder.create(factory, blocks).build();
	}
}
