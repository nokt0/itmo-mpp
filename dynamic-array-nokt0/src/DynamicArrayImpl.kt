import kotlinx.atomicfu.*

// Нужно гарантировать что при перемещении элемента не произойдет запись этого элемента
class DynamicArrayImpl<E> : DynamicArray<E> {
    private val core = atomic(Core<ArrayItem<E>>(INITIAL_CAPACITY))
    private val isCoreTransferring = atomic(false)

    override fun get(index: Int): E {
        verifyIndex(index)
        return getItem(index).value
    }

    override fun put(index: Int, element: E) {
        while (true) {
            verifyIndex(index)
            val value = getItem(index)
            // Если элемент в данный момент перемещается, то мы ожидаем перемещения
            if (value is MovableItem<E>) continue

            // Если удалось добавить элемент завершаем цикл
            if (core.value.casElement(index, value, ArrayItem(element))) {
                break
            }
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val coreValue = core.value
            val localSize = coreValue.size.value
            val capacity = coreValue.capacity

            // Если места еще хватает и получилось добавить новый элемент в массив, заканчиваем операцию
            if (localSize < capacity) {
                if (coreValue.casElement(localSize, null, ArrayItem(element))) {
                    coreValue.size.incrementAndGet()
                    return
                }
                continue
            }

            // Критическая секция для перемещения элементов
            if (!isCoreTransferring.compareAndSet(expect = false, update = true)) {
                continue
            }

            // Перемещаем все элементы в новый core
            val newCore = Core<ArrayItem<E>>(capacity * 2)
            for (i in 0 until localSize) {
                transferItems(i, coreValue, newCore)
            }

            // Обновляем core на новый
            newCore.size.compareAndSet(0, size)
            core.compareAndSet(coreValue, newCore)
            isCoreTransferring.compareAndSet(expect = true, update = false)
        }
    }

    private fun transferItems(index: Int, oldCore: Core<ArrayItem<E>>, newCore: Core<ArrayItem<E>>) {
        while (true) {
            val item = oldCore.getValue(index) ?: continue
            // Обертка для перемещаемого элемента
            val movableItem = MovableItem(item.value)
            // Записываем в текущий core элемент в обертке, чтобы знать что он перемещается
            if (oldCore.casElement(index, item, movableItem)) {
                newCore.casElement(index, null, item)
                break
            }
        }
    }

    override val size: Int
        get() {
            return core.value.size.value
        }

    private fun verifyIndex(index: Int) {
        if (size <= index) throw IllegalArgumentException()
    }

    private fun getItem(index: Int): ArrayItem<E> {
        return core.value.getValue(index) ?: throw IllegalArgumentException()
    }

}

private class Core<E>(
    val capacity: Int,
) {
    private val array = atomicArrayOfNulls<E>(capacity)
    val size = atomic(0)

    fun getValue(index: Int): E? {
        return array[index].value
    }

    fun casElement(index: Int, expected: E?, update: E?): Boolean {
        return array[index].compareAndSet(expected, update)
    }
}

private open class ArrayItem<E>(val value: E)
private class MovableItem<E>(value: E) : ArrayItem<E>(value)
private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME