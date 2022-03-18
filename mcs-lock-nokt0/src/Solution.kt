import java.util.concurrent.atomic.*

/*
Алгоритм позволяет избежать проблем с временем доступа к памяти на системах где память физически разделена по нескольким кристаллам(NUMA)
Каждый поток ждет на своей памяти
Каждый узел имеет ссылку на следующий
При блокировке мы записываем в tail новый узел, который содержит ссылку на предыдущий tail и прописываем next
Возникает неатомарность из-за двух операций, нам нужно решить эту проблему
 */

class Solution(val env: Environment) : Lock<Solution.Node> {
    private val tail = AtomicReference<Node>(null)

    // Позволяет создать критическую секцию при вызове через которую пройдет только один поток
    // все остальные будут ждать до вызова unlock с ним
    override fun lock(): Node {
        val my = Node()
        my.locked.value = true
        val pred = tail.getAndSet(my)
        // если есть предыдущий, то проставляем ему в next свой узел
        // паркуем поток в цикле, пока наш узел не разблокирует кто-нибудь
        if (pred != null) {
            pred.next.value = my
            while (my.locked.get()) {
                env.park()
            }
        }
        return my // вернули узел
    }

    override fun unlock(node: Node) {
        // если не удалось сделать cas на null, значит кто-то не успел проставить next -> ожидаем
        while (node.next.get() == null) {
            // очищаем tail, если он является узлом который нам нужно разблокировать
            if (tail.compareAndSet(node, null)) return
        }

        val next = node.next.get()
        next.locked.value = false
        env.unpark(next.thread)
    }

    class Node {
        val thread: Thread = Thread.currentThread() // запоминаем поток, который создал узел
        val locked = AtomicReference(false)
        val next = AtomicReference<Node>(null)
    }
}
