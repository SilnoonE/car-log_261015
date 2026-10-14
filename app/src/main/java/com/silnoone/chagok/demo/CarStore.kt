package com.silnoone.chagok.demo

import android.content.Context
import java.time.LocalDate
import java.io.Closeable

/** In-memory showcase adapter. The production SQLite and backup code is withheld. */
class CarStore(@Suppress("UNUSED_PARAMETER") context: Context) : Closeable {
    companion object {
        private val today = LocalDate.now()
        // Keep showcase records in the current month, including its first days.
        private fun sampleDay(daysAgo: Int) = today.withDayOfMonth((today.dayOfMonth-daysAgo).coerceAtLeast(1))
        private var data = Snapshot(
            vehicle = Vehicle("우리집 아반떼", "현대 아반떼", 2022, "휘발유", 24580, 350000),
            expenses = listOf(
                Expense(1, Category.FUEL, "가득 주유", 68000, sampleDay(2), 24380, 40.0, true, "동네 주유소"),
                Expense(2, Category.SERVICE, "엔진오일 교체", 75000, sampleDay(5), 24100, place="단골 정비소", memo="오일 필터 함께 교체"),
                Expense(3, Category.WASH, "주말 세차", 12000, sampleDay(1)),
                Expense(4, Category.PARKING, "주차", 5000, today)
            ),
            reminders = listOf(
                Reminder(1, "타이어 공기압 점검", today.plusDays(3), 25000),
                Reminder(2, "자동차 보험 갱신", today.plusDays(25))
            )
        )
        private var nextExpense = 5L
        private var nextReminder = 3L
    }

    fun snapshot(): Snapshot = synchronized(CarStore::class.java) { data }
    fun saveVehicle(vehicle: Vehicle) = synchronized(CarStore::class.java) {
        require(Rules.vehicle(vehicle) == null) { Rules.vehicle(vehicle) ?: "" }
        data = data.copy(vehicle=vehicle)
    }
    fun saveExpense(expense: Expense): Long = synchronized(CarStore::class.java) {
        require(Rules.expense(expense) == null) { Rules.expense(expense) ?: "" }
        val id = if (expense.id == 0L) nextExpense++ else expense.id
        val records = (data.expenses.filterNot { it.id == id } + expense.copy(id=id))
            .sortedWith(compareByDescending<Expense> { it.date }.thenByDescending { it.id })
        val vehicle = data.vehicle?.let { it.copy(odometer=maxOf(it.odometer,expense.odometer ?: 0)) }
        data = data.copy(vehicle=vehicle, expenses=records)
        id
    }
    fun deleteExpense(id: Long) = synchronized(CarStore::class.java) {
        data = data.copy(expenses=data.expenses.filterNot { it.id == id })
    }
    fun saveReminder(reminder: Reminder): Long = synchronized(CarStore::class.java) {
        require(Rules.reminder(reminder) == null) { Rules.reminder(reminder) ?: "" }
        val id = if (reminder.id == 0L) nextReminder++ else reminder.id
        data = data.copy(reminders=data.reminders.filterNot { it.id == id } + reminder.copy(id=id))
        id
    }
    fun deleteReminder(id: Long) = synchronized(CarStore::class.java) {
        data = data.copy(reminders=data.reminders.filterNot { it.id == id })
    }
    override fun close() = Unit
}
