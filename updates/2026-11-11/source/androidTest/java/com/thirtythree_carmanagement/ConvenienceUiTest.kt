package com.thirtythree_carmanagement
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.*
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.thirtythree_carmanagement.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
@RunWith(AndroidJUnit4::class)
class ConvenienceUiTest {
 @Test fun mileagePersistsAndRepeatCreatesIndependentRecordWithFreshFuelFields() {
  val ctx=InstrumentationRegistry.getInstrumentation().targetContext
  val backup=java.io.File(ctx.filesDir,"convenience-original.json")
  val original=if(backup.exists())backup.readText() else CarStore(ctx).use{it.exportJson()}.also{backup.writeText(it)}
  try {
   CarStore(ctx).use {
    it.restoreJson("""{"format":"chagok","version":1,"vehicle":null,"expenses":[],"reminders":[]}""")
    it.saveVehicle(Vehicle("기능 테스트","아반떼",2022,"휘발유",12000,350000))
    it.saveExpense(Expense(category=Category.FUEL,title="단골 주유",amount=70000,date=LocalDate.now().minusDays(1),odometer=12000,quantity=40.0,fullTank=true,place="우리 주유소",memo="이전 메모"))
   }
   ActivityScenario.launch<ChagokActivity>(Intent(ctx,ChagokActivity::class.java).putExtra("qaDisableAds",true)).use { scenario ->
    Thread.sleep(1200)
    onView(withId(R.id.odometer_edit)).perform(scrollTo(),click())
    onView(withId(R.id.odometer_input)).perform(replaceText("11999"))
    onView(withId(R.id.odometer_save)).perform(scrollTo(),click())
    onView(withText("현재 거리 이상, 9,999,999km 이하로 입력해 주세요.")).check(matches(isDisplayed()))
    onView(withId(R.id.odometer_input)).perform(scrollTo(),replaceText("12700"))
    onView(withId(R.id.odometer_save)).perform(scrollTo(),click()); Thread.sleep(700)
    scenario.recreate(); Thread.sleep(700)
    onView(withText("12,700 km")).check(matches(isDisplayed()))
    onView(withId(101)).perform(click())
    onView(withText("단골 주유")).perform(scrollTo(),click())
    onView(withId(R.id.expense_repeat)).perform(click())
    onView(withId(R.id.expense_title)).check(matches(withText("단골 주유")))
    onView(withId(R.id.expense_amount)).check(matches(withText("70000")))
    onView(withId(R.id.expense_km)).check(matches(withText("")))
    onView(withId(R.id.expense_quantity)).check(matches(withText("")))
    onView(withText("가득 주유했어요")).check(matches(isNotChecked()))
    onView(withId(R.id.expense_save)).perform(scrollTo(),click()); Thread.sleep(700)
    CarStore(ctx).use { db ->
     val data=db.snapshot(); assertEquals(12700L,data.vehicle!!.odometer)
     assertEquals(2,data.expenses.size)
     val fresh=data.expenses.first{it.date==LocalDate.now()}
     assertNull(fresh.odometer); assertNull(fresh.quantity); assertFalse(fresh.fullTank)
     assertEquals("",fresh.memo); assertEquals("우리 주유소",fresh.place)
     assertTrue(data.expenses.any{it.fullTank&&it.quantity==40.0})
    }
   }
  } finally {CarStore(ctx).use{it.restoreJson(original)}; backup.delete()}
 }
}
