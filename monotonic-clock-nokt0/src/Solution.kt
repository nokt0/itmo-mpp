import kotlin.math.max

/**
 * В теле класса решения разрешено использовать только переменные делегированные в класс RegularInt.
 * Нельзя volatile, нельзя другие типы, нельзя блокировки, нельзя лазить в глобальные переменные.
 * @author: Snimshchikov Vladislav
 */

class Solution : MonotonicClock {
    private var counter11 by RegularInt(0)
    private var counter12 by RegularInt(0)
    private var counter13 by RegularInt(0)
    private var counter21 by RegularInt(0)
    private var counter22 by RegularInt(0)
    private var counter23 by RegularInt(0)

    // записываем сначала часы
    override fun write(time: Time) {
        counter21 = time.d1
        counter22 = time.d2
        counter23 = time.d3

        counter13 = counter23
        counter12 = counter22
        counter11 = counter21
    }

    override fun read(): Time {
        val res11 = counter11
        val res12 = counter12
        val res13 = counter13

        val res23 = counter23
        val res22 = counter22
        val res21 = counter21

        // читаем в обратном порядке, чтобы обновить сначала большее значение
        return if (res11 == res21 && res12 == res22 && res13 == res23) {
            Time(res11, res12, res13)
        } else {
            when {
                res11 != res21 -> {
                    Time(max(res11, res21), 0, 0)
                }
                res12 != res22 -> {
                    Time(res11, max(res12, res22), 0)
                }
                else -> {
                    Time(res11, res12, max(res13, res23))
                }
            }
        }

    }

}