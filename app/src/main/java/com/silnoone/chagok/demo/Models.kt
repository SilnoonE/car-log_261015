package com.silnoone.chagok.demo

import java.time.LocalDate
import java.time.YearMonth
import java.text.NumberFormat
import java.util.Locale

enum class Category(val label: String) {
    FUEL("주유·충전"), SERVICE("정비"), INSURANCE("보험"), TAX("세금"),
    PARKING("주차"), TOLL("통행료"), WASH("세차"), OTHER("기타")
}
data class Vehicle(val name: String, val model: String, val year: Int, val fuel: String, val odometer: Long, val budget: Long)
data class Expense(val id: Long = 0, val category: Category, val title: String, val amount: Long,
    val date: LocalDate, val odometer: Long? = null, val quantity: Double? = null,
    val fullTank: Boolean = false, val place: String = "", val memo: String = "")
data class Reminder(val id: Long = 0, val title: String, val date: LocalDate? = null,
    val odometer: Long? = null, val done: Boolean = false)
data class Snapshot(val vehicle: Vehicle? = null, val expenses: List<Expense> = emptyList(), val reminders: List<Reminder> = emptyList())

object Rules {
    fun vehicle(v: Vehicle, today: LocalDate = LocalDate.now()): String? = when {
        v.name.isBlank() || v.name.length > 40 -> "차량 이름을 1~40자로 입력해 주세요."
        v.model.length > 60 -> "차종은 60자까지 입력할 수 있어요."
        v.year !in 1900..today.year + 1 -> "연식을 확인해 주세요."
        v.fuel !in listOf("휘발유", "경유", "LPG", "하이브리드", "전기") -> "연료를 선택해 주세요."
        v.odometer !in 0..9_999_999L -> "주행거리는 0~9,999,999km로 입력해 주세요."
        v.budget !in 0..1_000_000_000L -> "월 예산을 확인해 주세요."
        else -> null
    }
    fun expense(e: Expense, today: LocalDate = LocalDate.now()): String? = when {
        e.title.isBlank() || e.title.length > 60 -> "기록 이름을 1~60자로 입력해 주세요."
        e.amount !in 1..1_000_000_000L -> "금액은 1~1,000,000,000원으로 입력해 주세요."
        e.date.isAfter(today) || e.date.year < 1900 -> "지출 날짜는 오늘 또는 이전 날짜로 입력해 주세요."
        e.odometer != null && e.odometer !in 0..9_999_999L -> "주행거리를 확인해 주세요."
        e.quantity != null && (!e.quantity.isFinite() || e.quantity <= 0 || e.quantity > 1000) -> "주유·충전량은 0 초과, 1,000 이하로 입력해 주세요."
        e.category != Category.FUEL && (e.quantity != null || e.fullTank) -> "주유·충전 기록에서만 수량을 입력할 수 있어요."
        e.fullTank && (e.quantity == null || e.odometer == null) -> "가득 주유는 주유량과 주행거리를 함께 입력해 주세요."
        e.place.length > 80 || e.memo.length > 1000 -> "장소는 80자, 메모는 1,000자까지 입력할 수 있어요."
        else -> null
    }
    fun reminder(r: Reminder): String? = when {
        r.title.isBlank() || r.title.length > 60 -> "관리 항목을 1~60자로 입력해 주세요."
        r.date == null && r.odometer == null -> "예정일 또는 목표 주행거리 중 하나를 입력해 주세요."
        r.date != null && r.date.year !in 1900..2200 -> "예정일을 확인해 주세요."
        r.odometer != null && r.odometer !in 0..9_999_999L -> "목표 주행거리를 확인해 주세요."
        else -> null
    }
}
object Metrics {
    fun monthly(expenses: List<Expense>, month: YearMonth) = expenses.filter { YearMonth.from(it.date) == month }
    fun total(expenses: List<Expense>) = expenses.sumOf { it.amount }
    fun upcoming(r: Reminder, km: Long, today: LocalDate = LocalDate.now()) = !r.done &&
        ((r.date != null && !r.date.isAfter(today.plusDays(7))) || (r.odometer != null && r.odometer <= km + 500))
    fun overdue(r: Reminder, km: Long, today: LocalDate = LocalDate.now()) = !r.done &&
        ((r.date != null && !r.date.isAfter(today)) || (r.odometer != null && r.odometer <= km))
    // The production full-to-full fuel-efficiency algorithm is not published.
}
fun number(n: Long): String = NumberFormat.getIntegerInstance(Locale.KOREA).format(n)
fun won(n: Long) = number(n) + "원"
