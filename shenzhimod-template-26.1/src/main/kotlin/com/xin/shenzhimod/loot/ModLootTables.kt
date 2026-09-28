package com.xin.shenzhimod.loot

import com.xin.shenzhimod.ShenzhiMod
import net.fabricmc.fabric.api.loot.v3.FabricLootTableBuilder
import net.fabricmc.fabric.api.loot.v3.LootTableEvents
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.Items
import net.minecraft.world.level.storage.loot.LootPool
import net.minecraft.world.level.storage.loot.LootTable
import net.minecraft.world.level.storage.loot.entries.LootItem
import net.minecraft.world.level.storage.loot.predicates.LootItemKilledByPlayerCondition
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceWithEnchantedBonusCondition
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue

/**
 * 战利品表改动（模块 J）。
 *
 * 需求（§6.8.5 做法 2，推荐）：**不覆盖**原版 `piglin_brute` 掉落表，
 * 而是用 Fabric Loot API 追加一个池——这样不会与其他数据包抢同一个文件。
 *
 * 掉落规则（§6.7 / §8.1.2，与李芒果空岛一致）：
 * - 必须玩家击杀（`killed_by_player`）；
 * - 基础 5%，抢夺每级 +1%（I 级 6%、II 级 7%、III 级 8%）；
 * - 数值全部来自配置，服务器可自行调整。
 */
object ModLootTables {
	private val PIGLIN_BRUTE_TABLE: ResourceKey<LootTable> =
		ResourceKey.create(Registries.LOOT_TABLE, Identifier.withDefaultNamespace("entities/piglin_brute"))

	fun register() {
		LootTableEvents.MODIFY.register { key, tableBuilder, _, registries ->
			if (key != PIGLIN_BRUTE_TABLE) return@register

			val config = ShenzhiMod.config.piglinBrute
			if (!config.spawningInBastions) return@register

			val entry = LootItem.lootTableItem(Items.ANCIENT_DEBRIS)
			if (config.ancientDebrisRequiresPlayerKill) {
				entry.`when`(LootItemKilledByPlayerCondition.killedByPlayer())
			}
			entry.`when`(
				LootItemRandomChanceWithEnchantedBonusCondition.randomChanceAndLootingBoost(
					registries,
					config.ancientDebrisBaseChance.toFloat(),
					config.ancientDebrisChancePerLootingLevel.toFloat(),
				),
			)

			val pool = LootPool.lootPool()
				.setRolls(ConstantValue.exactly(1.0f))
				.add(entry)
				.build()

			(tableBuilder as FabricLootTableBuilder).pool(pool)
		}
	}
}
