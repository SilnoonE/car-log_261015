package com.silnoone.chagok.demo

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class PublicRulesTest {
    private val day=LocalDate.of(2026,10,14)
    @Test fun monthlyReportKeepsYearsSeparate() {
        val current=Expense(category=Category.SERVICE,title="정비",amount=80000,date=day)
        val old=current.copy(date=day.minusYears(1),amount=900000)
        assertEquals(80000L,Metrics.total(Metrics.monthly(listOf(current,old),YearMonth.of(2026,10))))
    }
    @Test fun expenseRejectsInvalidPaymentAndFutureDate() {
        val valid=Expense(category=Category.FUEL,title="주유",amount=68000,date=day)
        assertNull(Rules.expense(valid,day))
        assertNotNull(Rules.expense(valid.copy(amount=0),day))
        assertNotNull(Rules.expense(valid.copy(date=day.plusDays(1)),day))
    }
    @Test fun completedReminderStaysOutOfUpcomingList() {
        val reminder=Reminder(title="보험",date=day.plusDays(3))
        assertTrue(Metrics.upcoming(reminder,0,day))
        assertFalse(Metrics.upcoming(reminder.copy(done=true),0,day))
    }
}
