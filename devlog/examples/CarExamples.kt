package showcase.chagok

import java.time.LocalDate
import java.time.YearMonth

// 제품의 전체 모델·계산을 포함하지 않는 일반화된 예제입니다.
data class Payment(val amount: Long, val day: LocalDate)
data class Care(val day: LocalDate?, val targetKm: Long?, val completed: Boolean = false)

fun monthlySpent(payments: List<Payment>, month: YearMonth): Long =
    payments.filter { YearMonth.from(it.day) == month }.sumOf { it.amount }

fun dailyBudget(budget: Long, spent: Long, month: YearMonth, today: LocalDate): Long? {
    require(budget >= 0 && spent >= 0)
    if (budget == 0L || month != YearMonth.from(today)) return null
    val daysIncludingToday = month.lengthOfMonth() - today.dayOfMonth + 1
    return (budget - spent).coerceAtLeast(0) / daysIncludingToday
}

fun dueSoon(care: Care, currentKm: Long, today: LocalDate): Boolean {
    require(currentKm >= 0)
    require(care.day != null || care.targetKm != null)
    require(care.targetKm == null || care.targetKm >= 0)
    if (care.completed) return false
    val dateNear = care.day?.let { !it.isAfter(today.plusDays(7)) } ?: false
    val distanceNear = care.targetKm?.let { it <= currentKm || it - currentKm <= 500 } ?: false
    return dateNear || distanceNear
}

fun main() {
    val today = LocalDate.of(2026, 10, 20)
    val month = YearMonth.from(today)
    val payments = listOf(
        Payment(60_000, today),
        Payment(40_000, today.minusDays(1)),
        Payment(900_000, LocalDate.of(2025, 10, 20)),
        Payment(30_000, LocalDate.of(2026, 9, 20))
    )
    check(monthlySpent(payments, month) == 100_000L)
    check(monthlySpent(emptyList(), month) == 0L)
    // Long 합산: Int 범위를 넘는 지출 합계도 유지합니다.
    check(monthlySpent(listOf(Payment(2_000_000_000, today), Payment(2_000_000_000, today)), month) == 4_000_000_000L)

    check(dailyBudget(220_000, 100_000, month, today) == 10_000L) // 남은 12일
    check(dailyBudget(50_000, 100_000, month, today) == 0L)
    check(dailyBudget(0, 0, month, today) == null)
    check(dailyBudget(220_000, 100_000, month.minusMonths(1), today) == null)
    check(dailyBudget(20_000, 5_000, month, LocalDate.of(2026, 10, 31)) == 15_000L)
    val leapDay = LocalDate.of(2024, 2, 29)
    check(dailyBudget(10_000, 0, YearMonth.from(leapDay), leapDay) == 10_000L)

    check(dueSoon(Care(today.plusDays(7), null), 10_000, today))
    check(!dueSoon(Care(today.plusDays(8), null), 10_000, today))
    check(dueSoon(Care(null, 10_500), 10_000, today))
    check(!dueSoon(Care(null, 10_501), 10_000, today))
    check(dueSoon(Care(today.plusDays(30), 10_100), 10_000, today))
    check(!dueSoon(Care(today, 10_000, completed = true), 10_000, today))
    check(dueSoon(Care(today.minusDays(1), null), 10_000, today))
    check(runCatching { dueSoon(Care(null, null), 10_000, today) }.isFailure)
    println("PASS: monthly totals, inclusive daily budget, and date/distance reminders")
}
