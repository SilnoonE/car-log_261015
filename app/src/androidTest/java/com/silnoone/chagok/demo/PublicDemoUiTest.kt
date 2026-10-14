package com.silnoone.chagok.demo

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PublicDemoUiTest {
    @Test fun demoStartsWithSampleDataAndOpensAllFourTabs() {
        ActivityScenario.launch(ChagokActivity::class.java).use {
            onView(withId(101)).perform(click())
            onView(withId(R.id.record_search)).check(matches(isDisplayed()))
            onView(withText("엔진오일 교체")).check(matches(isDisplayed()))
            onView(withId(102)).perform(click())
            onView(withText("유지비 통계")).check(matches(isDisplayed()))
            onView(withId(103)).perform(click())
            onView(withText("정보 수정")).check(matches(isDisplayed()))
            onView(withId(100)).perform(click())
            onView(withId(R.id.monthly_total)).check(matches(withText("160,000원")))
        }
    }
}
