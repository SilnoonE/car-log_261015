package com.thirtythree_carmanagement

import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
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
class LocalizationUiTest {
    private val inst=InstrumentationRegistry.getInstrumentation()
    private val ctx=inst.targetContext
    private fun launch(db:String)=ActivityScenario.launch<ChagokActivity>(Intent(ctx,ChagokActivity::class.java)
        .putExtra("qaDisableAds",true).putExtra("qaDatabaseName",db))
    private fun awaitView(id:Int) {
        val end=System.currentTimeMillis()+10000
        while(true) {
            try { onView(withId(id)).check(matches(withEffectiveVisibility(Visibility.VISIBLE))); return }
            catch(e:Throwable) { if(System.currentTimeMillis()>end)throw e; Thread.sleep(100) }
        }
    }
    private fun capture(name:String) {
        inst.waitForIdleSync()
        val dir=java.io.File(ctx.externalMediaDirs.first(),"additional_test_output").apply { mkdirs() }
        inst.uiAutomation.takeScreenshot()?.let { b ->
            java.io.File(dir,"locale-$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG,100,it) }; b.recycle()
        }
    }
    private fun texts(v:View):List<String> = (if(v is TextView)listOf(v.text.toString()) else emptyList())+
        (if(v is ViewGroup)(0 until v.childCount).flatMap { texts(v.getChildAt(it)) } else emptyList())
    private fun checkNoPolicy(s:ActivityScenario<ChagokActivity>) {
        s.onActivity { a -> assertFalse(texts(a.window.decorView).any { it.contains("개인정보처리방침") }) }
    }
    @Test fun fiveLanguagePickerPersistsAndRecordsRemainEditable() {
        val db="qa-localization.db"
        ctx.deleteDatabase(db)
        CarStore(ctx,db).use { store ->
            store.saveVehicle(Vehicle("QA Car","Sedan",2024,FuelValues.PETROL,12000,300000))
            store.saveExpense(Expense(category=Category.FUEL,title="Original record",amount=50000,date=LocalDate.now(),odometer=12000,quantity=30.0,fullTank=true))
            store.saveReminder(Reminder(title="Oil change",date=LocalDate.now().plusDays(3),odometer=12500))
        }
        val locales=listOf("ko" to "한국어","en" to "English","ja" to "日本語","zh" to "简体中文","es" to "Español")
        launch(db).use { s ->
            awaitView(R.id.settings_open)
            var original=LocaleListCompat.getEmptyLocaleList()
            s.onActivity { original=AppCompatDelegate.getApplicationLocales() }
            try {
                for((tag,name) in locales) {
                    val before=CarStore(ctx,db).use { it.exportJson() }
                    onView(withId(R.id.settings_open)).perform(scrollTo(),click())
                    awaitView(R.id.language_select)
                    onView(withId(R.id.language_select)).perform(click())
                    onView(withText(name)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click())
                    awaitView(R.id.language_select)
                    s.onActivity { a ->
                        assertEquals(tag,a.resources.configuration.locales[0].language)
                        assertEquals(name,a.findViewById<TextView>(R.id.language_select).text.toString())
                        assertTrue(texts(a.window.decorView).contains(a.getString(R.string.app_language)))
                    }
                    s.recreate(); awaitView(R.id.language_select)
                    assertEquals("Language must never rewrite records",before,CarStore(ctx,db).use { it.exportJson() })
                    checkNoPolicy(s); capture("$tag-settings")
                    onView(withId(100)).perform(click()); awaitView(R.id.fuel_add)
                    for(tab in 100..103) {
                        onView(withId(tab)).perform(click()); inst.waitForIdleSync()
                        checkNoPolicy(s)
                        s.onActivity { a ->
                            if(tag!="ko")assertFalse("Untranslated screen text: ${texts(a.window.decorView)}",texts(a.window.decorView).any { Regex("[가-힣]").containsMatchIn(it) })
                        }
                        capture("$tag-tab$tab")
                    }
                    onView(withId(100)).perform(click()); awaitView(R.id.fuel_add)
                    onView(withId(R.id.fuel_add)).perform(scrollTo(),click())
                    awaitView(R.id.expense_title)
                    onView(withId(R.id.expense_title)).perform(replaceText("QA $tag"),closeSoftKeyboard())
                    onView(withId(R.id.expense_amount)).perform(scrollTo(),replaceText("0"),closeSoftKeyboard())
                    onView(withId(R.id.expense_save)).perform(scrollTo(),click())
                    var validation=""
                    s.onActivity { validation=it.getString(R.string.enter_an_amount_from_to_krw) }
                    onView(withText(validation)).check(matches(isDisplayed()))
                    capture("$tag-validation")
                    onView(withId(R.id.expense_amount)).perform(scrollTo(),replaceText("1200"),closeSoftKeyboard())
                    onView(withId(R.id.expense_quantity)).perform(scrollTo(),replaceText(if(tag=="es")"12,5" else "12.5"),closeSoftKeyboard())
                    onView(withId(R.id.expense_save)).perform(scrollTo(),click())
                    awaitView(R.id.fuel_add)
                    CarStore(ctx,db).use { store ->
                        val result=store.snapshot()
                        assertEquals(FuelValues.PETROL,result.vehicle!!.fuel)
                        assertTrue(result.expenses.any { it.title=="QA $tag" && it.amount==1200L && it.quantity==12.5 && it.category==Category.FUEL })
                        assertEquals(50000L,result.expenses.single { it.title=="Original record" }.amount)
                        assertEquals("Oil change",result.reminders.single().title)
                        val roundTrip=store.exportJson(); store.restoreJson(roundTrip)
                        assertEquals(roundTrip,store.exportJson())
                    }
                }
            } finally { s.onActivity { AppCompatDelegate.setApplicationLocales(original) } }
        }
        ctx.deleteDatabase(db)
    }
    @Test fun settingsWorksBeforeVehicleRegistration() {
        val db="qa-localization-empty.db"; ctx.deleteDatabase(db)
        launch(db).use { s ->
            awaitView(R.id.vehicle_add); checkNoPolicy(s)
            onView(withId(R.id.settings_open)).perform(click()); awaitView(R.id.language_select)
            checkNoPolicy(s)
            androidx.test.espresso.Espresso.pressBack()
            awaitView(R.id.vehicle_add)
        }
        ctx.deleteDatabase(db)
    }
}
