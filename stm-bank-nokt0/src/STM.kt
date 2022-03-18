import kotlinx.atomicfu.*

/**
 * Atomic block.
 */
// Позволяет обернуть код в atomic{} и выполнить его как единую транзакцию
fun <T> atomic(block: TxScope.() -> T): T {
    while (true) {
        val transaction = Transaction()
        try {
            val result = block(transaction)
            if (transaction.commit()) return result
            transaction.abort()
        } catch (e: AbortException) {
            transaction.abort()
        }
    }
}

/**
 * Transactional operations are performed in this scope.
 */
abstract class TxScope {
    abstract fun <T> TxVar<T>.read(): T
    abstract fun <T> TxVar<T>.write(x: T): T
}

/**
 * Transactional variable.
 */
class TxVar<T>(initial: T)  {
    private val loc = atomic(Loc(initial, initial, rootTx))

    /**
     * Opens this transactional variable in the specified transaction [tx] and applies
     * updating function [update] to it. Returns the updated value.
     */
    fun openIn(tx: Transaction, update: (T) -> T): T {
        // метод из лекции: Открываем переменную при операции
        while (true) {
            val currentLoc = loc.value
            // Забираем транзакцию у владельца
            val currentValue = currentLoc.valueIn(tx) {
                    owner -> owner.abort()
            }
            if (currentValue === TxStatus.ACTIVE) continue
            // Применяем update к значению транзакции
            val updValue = update(currentValue as T)
            val updLoc = Loc(currentValue, updValue, tx)
            // Обновляем состояние транзакции
            if (loc.compareAndSet(currentLoc, updLoc)) {
                if (tx.status == TxStatus.ABORTED) throw AbortException
                return updValue
            }
        }
    }
}

/**
 * State of transactional value
 */
private class Loc<T>(
    val oldValue: T,
    val newValue: T,
    val owner: Transaction
) {
    // метод из лекции: Узнаем текущее значение, откатываем если отменено
    fun valueIn(tx: Transaction, onActive: (Transaction) -> Unit): Any? =
        // Проверка что мы овнер транзакции не изменился
        if (owner === tx) newValue else
            when (owner.status) {
                TxStatus.ABORTED -> oldValue
                TxStatus.COMMITTED -> newValue
                TxStatus.ACTIVE -> {
                    onActive(owner)
                    TxStatus.ACTIVE
                }
            }
}

private val rootTx = Transaction().apply { commit() }

/**
 * Transaction status.
 */
enum class TxStatus { ACTIVE, COMMITTED, ABORTED }

/**
 * Transaction implementation.
 */
class Transaction : TxScope() {
    private val _status = atomic(TxStatus.ACTIVE)
    val status: TxStatus get() = _status.value

    fun commit(): Boolean =
        _status.compareAndSet(TxStatus.ACTIVE, TxStatus.COMMITTED)

    fun abort() {
        _status.compareAndSet(TxStatus.ACTIVE, TxStatus.ABORTED)
    }

    override fun <T> TxVar<T>.read(): T = openIn(this@Transaction) { it }
    override fun <T> TxVar<T>.write(x: T) = openIn(this@Transaction) { x }
}

/**
 * This exception is thrown when transaction is aborted.
 */
private object AbortException : Exception() {
    override fun fillInStackTrace(): Throwable = this
}