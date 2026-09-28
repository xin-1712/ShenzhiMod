package com.xin.shenzhimod.machine

/**
 * 合成龙首的内部账本（模块 B，纯逻辑）。
 *
 * 需求要点（§4.2）：
 * - 只存"压缩态物品"，按**种类**占格，容量默认 14（§8-G15）；
 * - 容量满则不再吸收；
 * - 非充能时把账本全部吐出；
 * - 方块被破坏时账本必须随方块一起掉落，不允许丢失。
 *
 * 这个类只做数量账，不碰世界实体——"先扣实体再写账本"的顺序由适配层保证，
 * 任何异常路径都不允许"扣了没产出"或"产出了没扣"。
 */
class DragonHeadLedger(private val capacity: Int) {
	private val stored: LinkedHashMap<String, Int> = LinkedHashMap()

	val usedSlots: Int get() = stored.size

	val isFull: Boolean get() = usedSlots >= capacity

	fun amountOf(itemId: String): Int = stored[itemId] ?: 0

	/** 账本快照（只读）。 */
	fun snapshot(): Map<String, Int> = LinkedHashMap(stored)

	fun isEmpty(): Boolean = stored.isEmpty()

	/**
	 * 是否还能吸收该物品：已在账本内的物品种类总是可以继续叠加；新种类需要有空位。
	 */
	fun canAccept(itemId: String): Boolean = stored.containsKey(itemId) || !isFull

	/**
	 * 吸收。返回实际吸收数量（0 表示拒绝）。
	 * 调用方必须在确认返回值 > 0 之后再销毁世界里的掉落物。
	 */
	fun absorb(itemId: String, amount: Int): Int {
		if (amount <= 0) return 0
		if (!canAccept(itemId)) return 0
		stored[itemId] = amountOf(itemId) + amount
		return amount
	}

	/** 清空并返回全部内容（非充能吐出 / 方块被破坏）。 */
	fun drainAll(): Map<String, Int> {
		val copy = LinkedHashMap(stored)
		stored.clear()
		return copy
	}

	/** 用账本内容支付一份配方；不足时不做任何修改并返回 false。 */
	fun tryConsume(requirements: List<CoreRequirement>): Boolean {
		if (requirements.isEmpty()) return false
		for (requirement in requirements) {
			if (amountOf(requirement.ledgerKey) < requirement.count) return false
		}
		for (requirement in requirements) {
			val left = amountOf(requirement.ledgerKey) - requirement.count
			if (left <= 0) stored.remove(requirement.ledgerKey) else stored[requirement.ledgerKey] = left
		}
		return true
	}

	/** 持久化载入（BlockEntity NBT）。超出容量的历史数据会被截断并返回 false 提示调用方。 */
	fun restore(values: Map<String, Int>): Boolean {
		stored.clear()
		var ok = true
		for ((itemId, amount) in values) {
			if (amount <= 0) continue
			if (stored.size >= capacity) {
				ok = false
				continue
			}
			stored[itemId] = amount
		}
		return ok
	}
}
