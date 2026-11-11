package com.thirtythree_carmanagement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class CarStoreTest {
    private lateinit var store:CarStore
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun before() { context.deleteDatabase("test-car.db"); store=CarStore(context,"test-car.db"); store.saveVehicle(Vehicle("테스트 차","아반떼",2024,"휘발유",10000,300000)) }
    @After fun after() { store.close(); context.deleteDatabase("test-car.db") }
    private fun record()=Expense(category=Category.FUEL,title="주유 테스트",amount=70000,date=LocalDate.now(),odometer=10500,quantity=40.0,fullTank=true,place="주유소",memo="한글 메모")
    @Test fun completionRecordsCostNextServiceAndBlocksDoubleSave() {
        val planned=Reminder(title="Oil",odometer=12000)
        val id=store.saveReminder(planned)
        val completed=planned.copy(id=id,date=LocalDate.now(),odometer=12100,done=true)
        val cost=Expense(category=Category.SERVICE,title="Oil",amount=45000,date=LocalDate.now(),odometer=12100)
        val next=planned.copy(odometer=22100)
        store.complete(completed,cost,next)
        val result=store.snapshot()
        assertEquals(12100L,result.vehicle!!.odometer)
        assertEquals(1,result.expenses.size)
        assertEquals(2,result.reminders.size)
        assertTrue(runCatching { store.complete(completed,cost,next) }.isFailure)
        assertEquals(result,store.snapshot())
        assertEquals(1,store.parseBackup(store.exportJson()).expenses.size)
        assertEquals(result,store.snapshot())
    }
    @Test fun createEditDeleteAndReopenKeepTotalsCorrect() {
        val id=store.saveExpense(record()); assertEquals(70000L,Metrics.total(store.snapshot().expenses))
        assertEquals(10500L,store.snapshot().vehicle!!.odometer)
        store.saveExpense(record().copy(id=id,amount=80000)); assertEquals(1,store.snapshot().expenses.size); assertEquals(80000L,Metrics.total(store.snapshot().expenses))
        store.close(); store=CarStore(context,"test-car.db"); assertEquals(80000L,Metrics.total(store.snapshot().expenses))
        store.deleteExpense(id); assertEquals(0,store.snapshot().expenses.size); assertEquals(10500L,store.snapshot().vehicle!!.odometer)
    }
    @Test fun rejectedRecordDoesNotChangeExistingData() {
        store.saveExpense(record())
        try { store.saveExpense(record().copy(amount=0)); fail("must reject") } catch(_:IllegalArgumentException) {}
        assertEquals(1,store.snapshot().expenses.size); assertEquals(70000L,Metrics.total(store.snapshot().expenses))
    }
    @Test fun currentMileageCannotGoBelowRecordedMileage() {
        store.saveExpense(record())
        try { store.saveVehicle(store.snapshot().vehicle!!.copy(odometer=100)); fail("must reject") } catch(_:IllegalArgumentException) {}
        assertEquals(10500L,store.snapshot().vehicle!!.odometer)
    }
    @Test fun backupRestoresAllRecordsAndReminders() {
        store.saveExpense(record()); store.saveReminder(Reminder(title="엔진오일",odometer=15000)); store.saveReminder(Reminder(title="보험",date=LocalDate.now().plusMonths(1),done=true))
        val backup=store.exportJson(); val expected=store.snapshot()
        store.saveExpense(record().copy(amount=50000)); store.restoreJson(backup); val actual=store.snapshot()
        assertEquals(expected.vehicle,actual.vehicle); assertEquals(1,actual.expenses.size)
        assertEquals(expected.expenses[0].copy(id=0),actual.expenses[0].copy(id=0)); assertEquals(2,actual.reminders.size)
        assertEquals(1,actual.reminders.count { it.done })
    }
    @Test fun invalidBackupNeverPartiallyReplacesData() {
        store.saveExpense(record()); val before=store.exportJson()
        try { store.restoreJson(before.replace("\"amount\": 70000","\"amount\": -1")); fail("must reject") } catch(_:IllegalArgumentException) {}
        assertEquals(before,store.exportJson())
        try { store.restoreJson("{invalid json"); fail("must reject") } catch(_:Exception) {}
        assertEquals(before,store.exportJson())
    }
    @Test fun completingAndReopeningReminderKeepsData() {
        val id=store.saveReminder(Reminder(title="정비",odometer=10500))
        store.saveReminder(store.snapshot().reminders.first().copy(done=true)); assertTrue(store.snapshot().reminders.first().done)
        store.saveReminder(store.snapshot().reminders.first().copy(done=false)); assertFalse(store.snapshot().reminders.first().done)
        store.deleteReminder(id); assertTrue(store.snapshot().reminders.isEmpty())
    }
    @Test fun backupPreservesOrderOnSameDate() {
        store.saveExpense(record().copy(title="첫 기록")); store.saveExpense(record().copy(title="두번째 기록"))
        val before=store.snapshot().expenses.map { it.title }; val backup=store.exportJson()
        store.restoreJson(backup); assertEquals(before,store.snapshot().expenses.map { it.title })
    }
    @Test fun fuelTypeCannotRewriteHistoricalUnits() {
        store.saveExpense(record())
        try { store.saveVehicle(store.snapshot().vehicle!!.copy(fuel="전기")); fail("must reject") } catch(_:IllegalArgumentException) {}
        assertEquals("휘발유",store.snapshot().vehicle!!.fuel)
    }
}
