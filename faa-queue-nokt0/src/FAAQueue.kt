import kotlinx.atomicfu.*

// Очередь будет состоять из сегментов, каждую ячейку мы используем только 1 раз, после использования ломаем
// Когда сегмент будет заполнен мы создаем следующий сегмент и меняем указатель на tail
class FAAQueue<T> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue

    init {
        val firstNode = Segment()
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(x: T) {
        while (true) {
            val tailLocal = tail.value
            val enqIdx = tailLocal.enqIdx.getAndIncrement()
            // Если индекс больше чем размер сегмента, то создаем новый сегмент
            if (enqIdx >= SEGMENT_SIZE) {
                val newTail = Segment(x)
                // Если next был null значит никто не успел добавить новый сегмент
                if (tailLocal.next.compareAndSet(null, newTail)) {
                    tail.compareAndSet(tailLocal, newTail)
                    return
                }
                // next != null, нужно просто подвинуть tail
                this.tail.compareAndSet(tailLocal, tailLocal.next.value!!)
                continue
            }
            // Просто записываем элемент, если в ячейке null
            if (tailLocal.elements[enqIdx].compareAndSet(null, x)) {
                return
            }
        }
    }

    /**
     * Retrieves the first element from the queue
     * and returns it; returns `null` if the queue
     * is empty.
     */
    fun dequeue(): T? {
        while (true) {
            val headLocal = this.head.value
            val deqIdx = headLocal.deqIdx.getAndIncrement()

            // Если вышли за сегмент и head.next != null то передвигаем head
            if (deqIdx >= SEGMENT_SIZE) {
                val headNext = headLocal.next.value ?: return null
                head.compareAndSet(headLocal, headNext)
                continue
            }

            // Ломаем ячейку, если было значение то возвращаем
            val res = headLocal.elements[deqIdx].getAndSet(DONE) ?: continue
            return res as T?
        }
    }

    /**
     * Returns `true` if this queue is empty;
     * `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            while (true) {
                val headLocal: Segment = head.value
                if (headLocal.isEmpty) {
                    //Если нету следующего, то очередь пустая
                    val headNext: Segment = headLocal.next.value ?: return true
                    // Если есть следующий, двигаем head
                    this.head.compareAndSet(headLocal, headNext)
                    continue
                }
                return false
            }
        }
}

private class Segment {
    val next: AtomicRef<Segment?> = atomic(null)
    val enqIdx = atomic(0) // index for the next enqueue operation
    val deqIdx = atomic(0) // index for the next dequeue operation
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    constructor() // for the first segment creation

    constructor(x: Any?) { // each next new segment should be constructed with an element
        enqIdx.value = 1
        elements[0].value = x
    }

    val isEmpty: Boolean get() = deqIdx.value >= enqIdx.value || deqIdx.value >= SEGMENT_SIZE

}

private val DONE = Any() // Marker for the "DONE" slot state; to avoid memory leaks
const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

