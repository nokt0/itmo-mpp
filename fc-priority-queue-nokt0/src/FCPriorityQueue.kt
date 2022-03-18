import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom

typealias OperationFunc<E> = () -> E?
private val ARRAY_SIZE = 2 * Thread.activeCount()

class FCPriorityQueue<E : Comparable<E>> {
    private val queue = PriorityQueue<E>()
    private val isLocked = atomic(false)
    private val operations = atomicArrayOfNulls<Operation<E>>(ARRAY_SIZE)
    private val index = ThreadLocalRandom.current().nextInt(ARRAY_SIZE)

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return executeOperation { queue.poll() }
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return executeOperation { queue.peek() }
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        executeOperation {
            queue.add(element)
            // add возвращает boolean, но функция void и не может возвращать значение, поэтому нужно возвращать null в конце
            null
        }
    }

    //
    private fun executeOperation(operation: OperationFunc<E>): E? {
        val newOperation = Operation(operation)

        while (true) {
            // Вход в критическую секцию. Ждем, если нельзя взять блокировку, либо если по индексу уже есть какая-то операция
            // Вход разрешается только одному(комбайн) потоку
            if (!operations[index].compareAndSet(null, newOperation) || !tryLock()) continue

            for (j in 0 until operations.size) {
                // Выполняем все операции в массиве, если это возможно
                if (operations[j].value != null) {
                    operations[j].value?.execute()
                }
            }

            // Выход из критической секции(комбайна)
            unlock()

            operations[index].value = null
            return newOperation.result.value
        }

    }

    private fun tryLock(): Boolean {
        return isLocked.compareAndSet(expect = false, update = true)
    }

    private fun unlock() {
        isLocked.compareAndSet(expect = true, update = false)
    }
}

open class Operation<E>(val operationFunc: OperationFunc<E>) {
    val result: AtomicRef<E?> = atomic(null)

    fun execute() {
        result.compareAndSet(null, operationFunc())
    }
}



