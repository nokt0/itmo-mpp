package dijkstra

import java.util.*
import java.util.concurrent.Phaser
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.Comparator
import kotlin.concurrent.thread
import kotlin.random.Random

private val NODE_DISTANCE_COMPARATOR = Comparator<Node> { o1, o2 -> o1!!.distance.compareTo(o2!!.distance) }

// Returns `Integer.MAX_VALUE` if a path has not been found.
fun shortestPathParallel(start: Node) {
    val workers = Runtime.getRuntime().availableProcessors()
    start.distance = 0
    val queue = PriorityMultiQueue(workers, NODE_DISTANCE_COMPARATOR)
    queue.push(start)
    val onFinish = Phaser(workers + 1) // `arrive()` should be invoked at the end by each worker
    val activeNodes = AtomicInteger()
    activeNodes.incrementAndGet()
    repeat(workers) {
        thread {
            while (activeNodes.get() > 0) {
                val current: Node = queue.pop() ?: continue
                current.outgoingEdges.forEach {
                    while (true) {
                        val toDistance = it.to.distance
                        val newDistance = current.distance + it.weight

                        if (toDistance <= newDistance) {
                            break
                        }

                        if (!it.to.casDistance(toDistance, newDistance)) {
                            continue
                        }

                        queue.push(it.to)
                        activeNodes.incrementAndGet()
                        break
                    }
                }
                activeNodes.decrementAndGet()
            }
            onFinish.arrive()
        }
    }
    onFinish.arriveAndAwaitAdvance()
}

class PriorityQueueWithLock<T>(comparator: Comparator<T>) {
    val priorityQueue: PriorityQueue<T> = PriorityQueue(comparator)
    val lock = ReentrantLock()
}

// Используем несколько очередей, для того, чтобы параллельно могло обрабатываться большее число вершин
// так как мы блокируем очередь когда берем элемент
class PriorityMultiQueue<T>(countOfThreads: Int, private val comparator: Comparator<T>) {
    private val numberOfQueues: Int = 2 * countOfThreads
    private val queues = Array(numberOfQueues) { PriorityQueueWithLock(comparator) }

    fun push(item: T) {
        // В цикле пытаемся взять блокировку и добавить в рандомную очередь ноду
        while (true) {
            val queue = getRandomQueue()
            if (!queue.lock.tryLock()) {
                continue
            }

            try {
                queue.priorityQueue.add(item)
                break
            } finally {
                queue.lock.unlock()
            }
        }
    }

    // Берем 2 рандомные очереди и пытаемся взять блокировку на обе
    fun pop(): T? {
        val firstQueue = getRandomQueue()
        val secondQueue = getRandomQueue()

        if (!firstQueue.lock.tryLock()) {
            return null
        }

        try {
            // Если получилось взять лок только на одну, то берем элемент из нее
            if (!secondQueue.lock.tryLock()) {
                return firstQueue.priorityQueue.poll()
            }
            try {
                val firstItem = firstQueue.priorityQueue.peek()
                val secondItem = secondQueue.priorityQueue.peek()
                // Смотрим первый элемент в каждой из очередей и сравниваем, берем тот у которого расстояние меньше
                return when {
                    firstItem == null && secondItem == null -> null
                    firstItem != null && secondItem == null -> firstQueue.priorityQueue.poll()
                    firstItem == null && secondItem != null -> secondQueue.priorityQueue.poll()
                    else -> {
                        if (comparator.compare(firstItem, secondItem) > 0) {
                            return secondQueue.priorityQueue.poll()
                        }
                        return firstQueue.priorityQueue.poll()
                    }
                }
                // Всегда делаем анлок после всех операций
            } finally {
                secondQueue.lock.unlock()
            }
        } finally {
            firstQueue.lock.unlock()
        }
    }

    private fun getRandomQueue(): PriorityQueueWithLock<T> {
        val index = Random.nextInt(numberOfQueues)
        return queues[index]
    }
}