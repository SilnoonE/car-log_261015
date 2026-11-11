package com.thirtythree_carmanagement
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.thirtythree_carmanagement.R
import org.junit.Test
import org.junit.runner.RunWith
import android.view.View
import org.hamcrest.Matcher

@RunWith(AndroidJUnit4::class)
class CarUiTest {
    private fun capture(name:String) {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync(); Thread.sleep(250)
        val dir=java.io.File(instrumentation.targetContext.externalMediaDirs.first(),"additional_test_output").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            java.io.File(dir,name+".png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
        }
    }
    private fun hideKeyboard() = object : androidx.test.espresso.ViewAction {
        override fun getConstraints():Matcher<View> = isDisplayed()
        override fun getDescription() = "Hide IME without dispatching Back to the sheet"
        override fun perform(controller:androidx.test.espresso.UiController,view:View) {
            val manager=view.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            manager.hideSoftInputFromWindow(view.windowToken,0)
            controller.loopMainThreadForAtLeast(300)
        }
    }
    private fun waitFor(matcher:Matcher<View>) {
        val start=System.currentTimeMillis()
        while(true) {
            try { onView(matcher).check(matches(isDisplayed())); return } catch(e:Throwable) { if(System.currentTimeMillis()-start>8000)throw e; Thread.sleep(100) }
        }
    }
    @Test fun realInputValidationEditNavigationAndRestart() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val backup=java.io.File(context.filesDir,"chagok-ui-original.json")
        val original=if(backup.exists())backup.readText() else CarStore(context).use { it.exportJson() }.also { backup.writeText(it) }
        CarStore(context).use { it.restoreJson("""{"format":"chagok","version":1,"vehicle":null,"expenses":[],"reminders":[]}""") }
        try {
        ActivityScenario.launch<ChagokActivity>(android.content.Intent(context,ChagokActivity::class.java).putExtra("qaDisableAds",true)).use { scenario ->
            waitFor(withId(R.id.vehicle_add)); onView(withId(R.id.vehicle_add)).perform(click())
            capture("차곡-화이트-차량등록")
            onView(isAssignableFrom(android.widget.Spinner::class.java)).perform(click())
            capture("차곡-화이트-연료선택")
            androidx.test.espresso.Espresso.onData(org.hamcrest.Matchers.equalTo("휘발유")).inRoot(androidx.test.espresso.matcher.RootMatchers.isPlatformPopup()).perform(click())
            onView(withId(R.id.vehicle_name)).perform(replaceText("테스트 아반떼"),hideKeyboard())
            onView(withId(R.id.vehicle_km)).perform(scrollTo(),replaceText("12000"),hideKeyboard())
            onView(withId(R.id.vehicle_save)).perform(scrollTo(),click())
            waitFor(withId(R.id.fuel_add)); onView(withId(R.id.fuel_add)).perform(click())
            capture("차곡-화이트-지출입력")
            onView(withId(R.id.expense_date)).perform(scrollTo(),click())
            capture("차곡-화이트-날짜선택")
            onView(withId(android.R.id.button1)).perform(click())
            onView(withId(R.id.expense_title)).perform(replaceText("주유 테스트"),hideKeyboard())
            onView(withId(R.id.expense_amount)).perform(scrollTo(),replaceText("0"),hideKeyboard())
            onView(withId(R.id.expense_save)).perform(scrollTo(),click())
            onView(withText("금액은 1~1,000,000,000원으로 입력해 주세요.")).check(matches(isDisplayed()))
            onView(withId(R.id.expense_amount)).perform(scrollTo(),replaceText("70000"),hideKeyboard())
            onView(withId(R.id.expense_km)).perform(scrollTo(),replaceText("12500"),hideKeyboard())
            onView(withId(R.id.expense_save)).perform(scrollTo(),click())
            waitFor(withId(R.id.fuel_add)); onView(withId(R.id.monthly_total)).check(matches(withText("70,000원")))
            scenario.recreate(); waitFor(withId(R.id.fuel_add))
            onView(withId(R.id.monthly_total)).check(matches(withText("70,000원")))
            onView(withId(101)).perform(click())
            waitFor(withId(R.id.record_search))
            onView(withText("주유 테스트")).perform(scrollTo(),click())
            capture("차곡-화이트-기록상세")
            onView(withText("수정")).perform(click())
            onView(withId(R.id.expense_amount)).perform(scrollTo(),replaceText("80000"),hideKeyboard())
            onView(withId(R.id.expense_save)).perform(scrollTo(),click())
            waitFor(withId(R.id.record_search))
            onView(withText("80,000원")).check(matches(isDisplayed()))
            onView(withId(102)).perform(click())
            waitFor(withText("유지비 통계"))
            onView(withId(103)).perform(click())
            waitFor(withText("정보 수정"))
            onView(withText("일정 추가")).perform(scrollTo(),click())
            capture("차곡-화이트-일정입력")
            onView(withId(R.id.reminder_title)).perform(replaceText("엔진오일 테스트"),hideKeyboard())
            onView(withId(R.id.reminder_km)).perform(scrollTo(),replaceText("13000"),hideKeyboard())
            onView(withId(R.id.reminder_save)).perform(scrollTo(),click())
            waitFor(withText("엔진오일 테스트"))
            onView(withText("엔진오일 테스트")).perform(scrollTo(),click())
            onView(withText("완료 처리")).perform(click())
            waitFor(withText("완료한 일정"))
            onView(withId(100)).perform(click())
            waitFor(withId(R.id.fuel_add))
        }
        } finally { CarStore(context).use { it.restoreJson(original) }; backup.delete() }
    }
}
