package com.thirtythree_carmanagement
import android.content.Intent
import android.graphics.Bitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class BottomLayoutTest {
 @Test fun navigationFillsBottomAndReservesSystemInsetOnlyOnce() {
  val inst=InstrumentationRegistry.getInstrumentation(); val ctx=inst.targetContext
  ActivityScenario.launch<ChagokActivity>(Intent(ctx,ChagokActivity::class.java).putExtra("qaDisableAds",true)).use { scenario ->
   Thread.sleep(700)
   for (tab in 100..103) {
    scenario.onActivity { a ->
     val nav=a.javaClass.getDeclaredField("nav").apply{isAccessible=true}.get(a) as BottomNavigationView
     nav.selectedItemId=tab
    }
    inst.waitForIdleSync()
    scenario.onActivity { a ->
     val nav=a.javaClass.getDeclaredField("nav").apply{isAccessible=true}.get(a) as BottomNavigationView
     val loc=IntArray(2); nav.getLocationOnScreen(loc)
     val decor=a.window.decorView; val origin=IntArray(2); decor.getLocationOnScreen(origin)
     assertEquals("No unfilled app area below navigation",origin[1]+decor.height,loc[1]+nav.height)
     val density=a.resources.displayMetrics.density
     assertEquals("Only 64dp content plus the system gesture/button inset",(64*density).toInt()+nav.paddingBottom,nav.height)
     assertTrue("Touch targets stay at least 48dp",nav.findViewById<android.view.View>(tab).height>=48*density)
     assertEquals("Root must not duplicate bottom inset",0,(nav.parent as android.view.View).paddingBottom)
    }
   }
   val dir=java.io.File(ctx.externalMediaDirs.first(),"additional_test_output").apply{mkdirs()}
   inst.uiAutomation.takeScreenshot()?.let { bitmap -> java.io.File(dir,"bottom-layout-v4.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}; bitmap.recycle() }
  }
 }
}
