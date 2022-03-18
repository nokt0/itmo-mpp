/**
 * @author Snimshchikov Vladislav
 */
class Solution : AtomicCounter {
    private val root = Node(0, Consensus())

    private val last = ThreadLocal.withInitial {
        root
    }

    override fun getAndAdd(x: Int): Int {
        while (true) {
            val previous = last.get()
            val result = Node(previous.value + x, Consensus())
            // Записываем решение консенсуса в last, все потоки получают из консенсуса одно и то же значение
            // Прибавление совершает только первый поток который успел вызвать консенсус, все остальные вызывают
            // Консенсус и получают обновленное значение, после чего пробуют совершить прибавление еще раз уже к новому значению
            val decide = last.get().next.decide(result)
            last.set(decide)

            // Возвращаем значение до прибавления
            if (last.get() == result) {
                return previous.value
            }
        }
    }

    private class Node(val value: Int, val next: Consensus<Node>)
}