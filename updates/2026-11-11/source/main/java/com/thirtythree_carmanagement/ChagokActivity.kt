package com.thirtythree_carmanagement

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Build
import android.os.Bundle
import android.text.*
import android.view.*
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.thirtythree_carmanagement.R
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textfield.TextInputEditText
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.concurrent.Executors

class ChagokActivity : AppCompatActivity() {
    private val green=Color.rgb(28,65,53)
    private val ink=Color.rgb(31,40,36)
    private val muted=Color.rgb(105,115,110)
    private val bg=Color.rgb(231,237,228)
    private val executor=Executors.newSingleThreadExecutor()
    private lateinit var store:CarStore
    private lateinit var ads:AdsController
    private lateinit var root:LinearLayout
    private lateinit var page:LinearLayout
    private lateinit var nav:BottomNavigationView
    private var data=Snapshot()
    private var settingsOpen=false
    private lateinit var settingsBack:OnBackPressedCallback
    private var tab=0
    private var month=YearMonth.now()
    private var busy=false
    private var ready=false
    private var query=""
    private var filter:Category?=null
    private var fromDate: LocalDate? = null
    private var untilDate: LocalDate? = null
    private var limit=100
    private val export=registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if(uri!=null) action({ contentResolver.openOutputStream(uri,"wt")?.use { it.write(store.exportJson().toByteArray(Charsets.UTF_8)) } ?: error(getString(R.string.unable_to_open_the_file)) },getString(R.string.backup_saved))
    }
    private val import=registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null&&!busy) {
            busy=true
            executor.execute {
                val result=runCatching {
                    contentResolver.openInputStream(uri)?.use { input ->
                        val output=java.io.ByteArrayOutputStream()
                        val buffer=ByteArray(8192)
                        while(true) { val n=input.read(buffer); if(n<0)break; require(output.size()+n<=10_000_000) { getString(R.string.choose_a_file_of_mb_or_less) }; output.write(buffer,0,n) }
                        store.parseBackup(output.toString("UTF-8"))
                    } ?: error(getString(R.string.unable_to_read_the_file))
                }
                runOnUiThread {
                    busy=false
                    if(isDestroyed)return@runOnUiThread
                    result.onSuccess { backup ->
                        MaterialAlertDialogBuilder(this).setTitle(getString(R.string.restore_this_backup))
                            .setMessage(getString(R.string.backup_preview, backup.vehicle?.name ?: "—", backup.expenses.size, backup.expenses.maxOfOrNull { it.date }?.toString() ?: "—") + "\n\n" + getString(R.string.this_replaces_your_current_vehicle_expenses_and))
                            .setNegativeButton(getString(R.string.cancel),null).setPositiveButton(getString(R.string.restore)) { _,_ -> action({ store.restoreBackup(backup) },getString(R.string.backup_restored)) }.show()
                    }.onFailure { notice(it.message?:getString(R.string.could_not_read_the_file)) }
                }
            }
        }
    }
    private val csv=registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if(uri!=null) action({
            fun cell(s:String):String { val safe=if(s.startsWith("=")||s.startsWith("+")||s.startsWith("-")||s.startsWith("@")) "'"+s else s; return "\""+safe.replace("\"","\"\"")+"\"" }
            val text=getString(R.string.ufeffdate_category_title_amount_krw_odometer_km)+store.snapshot().expenses.joinToString("\r\n") {
                listOf(it.date.toString(),it.category.display(this),it.title,it.amount.toString(),it.odometer?.toString()?:"",it.quantity?.toString()?:"",if(it.fullTank)getString(R.string.yes) else getString(R.string.no),it.place,it.memo).joinToString(",") { value -> cell(value) }
            }
            contentResolver.openOutputStream(uri,"wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: error(getString(R.string.unable_to_open_the_file))
        },getString(R.string.csv_saved))
    }
    private val permission=registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        getSharedPreferences("settings",MODE_PRIVATE).edit().putBoolean("notifications",granted).apply()
        if(granted)ReminderJob.schedule(this)
        notice(if(granted)getString(R.string.maintenance_reminders_enabled) else getString(R.string.notifications_are_off_you_can_still_view))
        render()
    }
    override fun onCreate(state:Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(state)
        val preferences = getSharedPreferences("settings", MODE_PRIVATE)
        if (!preferences.getBoolean("initialLanguageSet", false)) {
            if (AppCompatDelegate.getApplicationLocales().isEmpty) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ko"))
            }
            preferences.edit().putBoolean("initialLanguageSet", true).apply()
        }
        enableEdgeToEdge()
        val qaDatabase=if(com.example.thirtythree_carmanagement.BuildConfig.DEBUG)
            intent.getStringExtra("qaDatabaseName")?.takeIf { it.matches(Regex("qa-[a-zA-Z0-9-]+\\.db")) } else null
        store=CarStore(this,qaDatabase?:"chagok.db"); tab=state?.getInt("tab")?:0
        settingsOpen=state?.getBoolean("settingsOpen")?:false
        settingsBack=object:OnBackPressedCallback(settingsOpen) { override fun handleOnBackPressed() { settingsOpen=false; render() } }
        onBackPressedDispatcher.addCallback(this,settingsBack)
        fromDate=state?.getString("fromDate")?.let(LocalDate::parse)
        untilDate=state?.getString("untilDate")?.let(LocalDate::parse)
        month=state?.getString("month")?.let { YearMonth.parse(it) }?:YearMonth.now()
        query=state?.getString("query")?:""
        filter=state?.getString("filter")?.let { Category.valueOf(it) }
        root=col().apply { setBackgroundColor(bg); isFocusableInTouchMode=true }; setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v,insets ->
            val p=insets.getInsets(WindowInsetsCompat.Type.systemBars()); v.setPadding(p.left,p.top,p.right,0); if(::nav.isInitialized) { nav.setPadding(0,0,0,p.bottom); nav.layoutParams=nav.layoutParams.apply { height=dp(64)+p.bottom } }; WindowInsetsCompat.CONSUMED
        }
        val scroll=ScrollView(this).apply { isFillViewport=true }
        page=col().apply { setPadding(dp(22),dp(24),dp(22),dp(28)) }; scroll.addView(page)
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        val bannerHost=FrameLayout(this).apply { id=R.id.ads_banner_host; setPadding(dp(16),dp(8),dp(16),dp(6)); visibility=View.GONE }
        root.addView(bannerHost,LinearLayout.LayoutParams(-1,-2))
        ads=AdsController(this,bannerHost,com.example.thirtythree_carmanagement.BuildConfig.DEBUG && intent.getBooleanExtra("qaDisableAds",false))
        nav=BottomNavigationView(this).apply {
            minimumHeight=0; itemPaddingTop=dp(8); itemPaddingBottom=dp(6); setBackgroundColor(Color.WHITE); labelVisibilityMode=BottomNavigationView.LABEL_VISIBILITY_LABELED
            itemIconTintList=ContextCompat.getColorStateList(this@ChagokActivity,R.color.nav_colors)
            itemTextColor=itemIconTintList
            itemActiveIndicatorColor=android.content.res.ColorStateList.valueOf(Color.rgb(234,240,227))
            listOf(getString(R.string.home) to R.drawable.ic_home,getString(R.string.records) to R.drawable.ic_history,getString(R.string.stats) to R.drawable.ic_chart,getString(R.string.my_car) to R.drawable.ic_car).forEachIndexed { i,p -> menu.add(0,100+i,i,p.first).setIcon(p.second) }
            selectedItemId=100+tab
            setOnItemSelectedListener { val previous=tab; settingsOpen=false; tab=it.itemId-100; page.clearFocus(); render(); root.requestFocus(); scroll.post { scroll.scrollTo(0,0) }; if(tab==2 && previous!=2) ads.transition(AdPlacement.STATS_ENTERED); true }
        }
        nav.getChildAt(0).minimumHeight=dp(64)
        ViewCompat.setOnApplyWindowInsetsListener(nav) { _,insets -> insets }
        root.addView(nav,LinearLayout.LayoutParams(-1,dp(64))); ViewCompat.requestApplyInsets(root); render()
        executor.execute {
            val result=runCatching { store.snapshot() }
            runOnUiThread { if(!isDestroyed) { ready=true; result.onSuccess { data=it }.onFailure { notice(getString(R.string.could_not_load_your_records)) }; render() } }
        }
        ReminderJob.schedule(this)
        ads.start()
    }
    override fun onSaveInstanceState(outState:Bundle) { outState.putString("fromDate",fromDate?.toString()); outState.putString("untilDate",untilDate?.toString()); outState.putBoolean("settingsOpen",settingsOpen); outState.putInt("tab",tab); outState.putString("month",month.toString()); outState.putString("query",query); outState.putString("filter",filter?.name); super.onSaveInstanceState(outState) }
    override fun onResume() { super.onResume(); if(::ads.isInitialized)ads.resume() }
    override fun onPause() { if(::ads.isInitialized)ads.pause(); super.onPause() }
    override fun onDestroy() { if(::ads.isInitialized)ads.destroy(); if(::store.isInitialized)executor.execute { store.close() }; executor.shutdown(); super.onDestroy() }
    private fun action(work:()->Unit,message:String=getString(R.string.saved),after:()->Unit={}) {
        if(busy)return; busy=true
        executor.execute {
            val result=runCatching { work(); store.snapshot() }
            runOnUiThread {
                busy=false
                if(isDestroyed||isFinishing)return@runOnUiThread
                result.onSuccess { data=it; after(); render(); notice(message) }.onFailure { notice(it.message?:getString(R.string.could_not_save_please_try_again)) }
            }
        }
    }
    private fun render() {
        page.removeAllViews()
        val h=row(); h.addView(icon(R.drawable.brand_mark,green,32)); h.addView(View(this),LinearLayout.LayoutParams(dp(9),1)); h.addView(label(getString(R.string.chagok),25,true),LinearLayout.LayoutParams(0,-2,1f)); h.addView(ImageButton(this).apply {
            id=R.id.settings_open; contentDescription=getString(R.string.settings); setImageResource(R.drawable.ic_settings)
            imageTintList=android.content.res.ColorStateList.valueOf(green)
            background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.WHITE); cornerRadius=dp(14).toFloat(); setStroke(dp(1),Color.rgb(137,158,137)) }
            setPadding(dp(12),dp(12),dp(12),dp(12)); setOnClickListener { if(!busy) { settingsOpen=!settingsOpen; render() } }
        },LinearLayout.LayoutParams(dp(48),dp(48))); page.addView(h); gap(page,24)
        settingsBack.isEnabled=settingsOpen
        if(settingsOpen) { settings(); return }
        if(!ready) { page.addView(label(getString(R.string.loading_your_records),15)); return }
        if(data.vehicle==null) { welcome(); return }
        when(tab) { 0->home(); 1->records(); 2->stats(); 3->garage() }
    }
    private fun welcome() {
        val p=panel(); p.addView(icon(R.drawable.brand_mark,green,88)); gap(p,28)
        p.addView(label(getString(R.string.a_simple_habit_nfor_a_well_kept),30,true)); gap(p,14)
        p.addView(label(getString(R.string.fuel_stops_maintenance_and_monthly_costs_nkeep),15,color=muted)); gap(p,28)
        p.addView(button(getString(R.string.add_my_car),R.id.vehicle_add) { vehicleForm() }); page.addView(card(p)); gap(page,26)
        listOf(getString(R.string.quick_records) to getString(R.string.start_with_just_an_amount_and_a),getString(R.string.costs_at_a_glance) to getString(R.string.review_monthly_costs_and_category_totals),getString(R.string.upcoming_maintenance) to getString(R.string.plan_by_date_or_odometer_reading)).forEach {
            page.addView(label(it.first,16,true)); gap(page,6); page.addView(label(it.second,14,color=muted)); gap(page,22)
        }
        page.addView(button(getString(R.string.start_from_a_backup),outline=true) { import.launch(arrayOf("application/json","text/plain","application/octet-stream")) })
        gap(page,12); page.addView(label(getString(R.string.stored_on_your_device_without_an_account),12,color=muted))
    }
    private fun home() {
        val v=data.vehicle!!
        val vehicle=panel(); val vh=row()
        val identity=col(); identity.addView(label(v.name,22,true)); gap(identity,5)
        identity.addView(label(listOf(v.model,getString(R.string.value,v.year),fuelName(v.fuel)).filter { it.isNotBlank() }.joinToString(" · "),12,color=muted))
        vh.addView(identity,LinearLayout.LayoutParams(0,-2,1f)); vh.addView(icon(R.drawable.ic_car,green,36)); vehicle.addView(vh)
        gap(vehicle,16); divider(vehicle); gap(vehicle,8)
        val distance=row(); distance.addView(label(number(v.odometer)+" km",18,true),LinearLayout.LayoutParams(0,-2,1f))
        distance.addView(button(getString(R.string.update_km),R.id.odometer_edit,true) { odometerForm() }.apply { textSize=12f; minWidth=0 }); vehicle.addView(distance)
        page.addView(card(vehicle)); gap(page,20); monthSelector()
        val es=Metrics.monthly(data.expenses,month); val total=Metrics.total(es)
        val prev=Metrics.total(Metrics.monthly(data.expenses,month.minusMonths(1)))
        val p=panel(); p.addView(label(getString(R.string.monthly_expense),11,true,Color.rgb(207,227,173))); gap(p,12)
        p.addView(label(if(month==YearMonth.now())getString(R.string.this_month_s_expenses) else getString(R.string.expenses_month_value,month.monthValue),14,color=Color.rgb(220,232,224))); gap(p,8)
        p.addView(label(won(total),34,true,Color.WHITE).apply { id=R.id.monthly_total }); gap(p,12)
        val difference=total-prev
        p.addView(label(if(prev==0L)getString(R.string.records_this_month_value_every_entry_counts,es.size) else if(difference==0L)getString(R.string.same_spending_as_last_month) else getString(R.string.value_value_than_last_month,won(kotlin.math.abs(difference)),if(difference>0)getString(R.string.more) else getString(R.string.less)),12,color=Color.rgb(220,232,224)))
        if(v.budget>0) {
            gap(p,20); p.addView(progress(total,v.budget).apply { setIndicatorColor(Color.rgb(207,227,173)); trackColor=Color.rgb(65,93,78) }); gap(p,10)
            p.addView(label(if(total>v.budget)getString(R.string.value_over_budget,won(total-v.budget)) else getString(R.string.budget_value_value_left,won(v.budget),won(v.budget-total)),13,true,Color.WHITE))
        }
        page.addView(card(p,green)); gap(page,16)
        val allowance=Insights.dailyAllowance(v.budget,total,month)
        if(allowance!=null) {
            val a=panel(); a.setPadding(dp(18),dp(14),dp(18),dp(14))
            a.addView(label(getString(R.string.daily_budget_left_value,won(allowance)),15,true)); gap(a,5)
            a.addView(label(getString(R.string.today_to_month_end_based_on_recorded),12,color=muted)); page.addView(card(a,Color.rgb(240,245,226))); gap(page,18)
        }
        val quick=row(); quick.addView(button(getString(R.string.fuel_charge),R.id.fuel_add) { expenseForm(category=Category.FUEL) },LinearLayout.LayoutParams(0,-2,1f))
        quick.addView(View(this),LinearLayout.LayoutParams(dp(10),1)); quick.addView(button(getString(R.string.maintenance),R.id.service_add,true) { expenseForm(category=Category.SERVICE) },LinearLayout.LayoutParams(0,-2,1f)); page.addView(quick)
        gap(page,10); page.addView(button(getString(R.string.other_expense),R.id.expense_add,true) { expenseForm(category=Category.OTHER) }); gap(page,24)
        section(getString(R.string.coming_up),getString(R.string.add_reminder)) { reminderForm() }
        val upcoming=data.reminders.filter { Metrics.upcoming(it,v.odometer) }.sortedBy { it.date?:LocalDate.MAX }
        if(upcoming.isEmpty())empty(getString(R.string.nothing_due_soon),getString(R.string.add_insurance_renewals_or_maintenance_dates)) else upcoming.take(3).forEach { reminderRow(it) }
        gap(page,24); section(getString(R.string.recent_records),getString(R.string.view_all)) { nav.selectedItemId=101 }
        if(data.expenses.isEmpty())empty(getString(R.string.add_your_first_record),getString(R.string.your_recorded_costs_are_totaled_automatically)) else data.expenses.take(4).forEach { expenseRow(page,it) }
    }
    private fun odometerForm() {
        val v=data.vehicle?:return
        val (dialog,f)=sheet(getString(R.string.update_odometer))
        f.addView(label(getString(R.string.update_distance_without_adding_an_expense_nmileage),14,color=muted)); gap(f,20)
        val km=field(f,getString(R.string.current_odometer_km),v.odometer.toString(),R.id.odometer_input,numeric=true)
        val error=label("",13,color=Color.rgb(174,64,40)); f.addView(error)
        f.addView(button(getString(R.string.save_odometer),R.id.odometer_save) {
            val value=km.text.toString().toLongOrNull()
            if(value==null||value<v.odometer||value>9_999_999) { error.text=getString(R.string.enter_at_least_the_current_reading_up); return@button }
            action({store.saveVehicle(v.copy(odometer=value))},getString(R.string.odometer_updated)) { dialog.dismiss() }
        }); showSheet(dialog)
    }
    private fun records() {
        section(getString(R.string.vehicle_records),getString(R.string.add_record)) { expenseForm() }
        page.addView(label(getString(R.string.your_fuel_maintenance_and_expense_history),13,color=muted)); gap(page,16)
        val search=field(page,getString(R.string.search_title_place_or_note),query,R.id.record_search)
        val select=spinner(listOf(getString(R.string.all_categories))+Category.entries.map { it.display(this) },filter?.ordinal?.plus(1)?:0); page.addView(select); gap(page,18)
        page.addView(button(if(fromDate==null) getString(R.string.period) else "$fromDate ~ $untilDate",outline=true) {
            val choices = arrayOf(getString(R.string.all_dates),getString(R.string.this_month),getString(R.string.last_month),getString(R.string.custom_dates))
            fun applyDates(start:LocalDate?, end:LocalDate?) { fromDate=start; untilDate=end; limit=100; render() }
            MaterialAlertDialogBuilder(this).setTitle(getString(R.string.period)).setItems(choices) { _, index ->
                when(index) {
                    0 -> applyDates(null,null)
                    1,2 -> {
                        val chosen=YearMonth.now().minusMonths((index-1).toLong())
                        applyDates(chosen.atDay(1),chosen.atEndOfMonth())
                    }
                    else -> pickDay(fromDate ?: LocalDate.now(),getString(R.string.start_day)) { start ->
                        pickDay(maxOf(start,untilDate ?: LocalDate.now()),getString(R.string.end_day),start) { end -> applyDates(start,end) }
                    }
                }
            }.show()
        })
        val holder=col(); page.addView(holder)
        fun update() {
            holder.removeAllViews()
            val list=data.expenses.filter { (fromDate==null || !it.date.isBefore(fromDate)) && (untilDate==null || !it.date.isAfter(untilDate)) && (filter==null||it.category==filter)&&(query.isBlank()||(it.title+" "+it.place+" "+it.memo).contains(query,true)) }
            holder.addView(label(getString(R.string.records_value_value,list.size,won(Metrics.total(list))),13,true,muted)); gap(holder,12)
            if(list.isEmpty()) { val p=panel(); p.addView(label(getString(R.string.no_matching_records),15,true)); gap(p,10); p.addView(label(getString(R.string.try_another_search_or_category_or_add),13,color=muted)); holder.addView(card(p,Color.rgb(237,244,225))); return }
            var last=""
            list.take(limit).forEach { e ->
                val m=getString(R.string.value_value,e.date.year,e.date.monthValue)
                if(m!=last) { gap(holder,12); holder.addView(label(m,13,true,muted)); gap(holder,10); last=m }
                expenseRow(holder,e)
            }
            if(list.size>limit)holder.addView(button(getString(R.string.show_more),outline=true) { limit+=100; update() })
        }
        select.onItemSelectedListener=selection { i->filter=if(i==0)null else Category.entries[i-1]; limit=100; update() }
        search.addTextChangedListener(object:TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { query=s.toString(); limit=100; update() }
            override fun afterTextChanged(s:Editable?) {}
        }); update()
    }
    private fun stats() {
        section(getString(R.string.cost_overview)); monthSelector()
        val es=Metrics.monthly(data.expenses,month); val total=Metrics.total(es); val v=data.vehicle!!
        val p=panel(); p.addView(label(if(month==YearMonth.now())getString(R.string.this_month_s_expenses) else getString(R.string.expenses_month_value,month.monthValue),13,color=muted)); gap(p,8); p.addView(label(won(total),32,true))
        if(v.budget>0) {
            gap(p,18); p.addView(label(getString(R.string.monthly_budget_value,won(v.budget))+if(total>v.budget)won(total-v.budget)+getString(R.string.over_budget) else won(v.budget-total)+getString(R.string.left),13,color=muted))
            gap(p,10); p.addView(progress(total,v.budget))
        } else { gap(p,12); p.addView(label(getString(R.string.set_a_monthly_budget_in_my_car),12,color=muted)) }
        page.addView(card(p,Color.rgb(239,245,226))); gap(page,26); section(getString(R.string.by_category))
        if(es.isEmpty())empty(getString(R.string.no_records_this_month),getString(R.string.add_records_to_see_your_spending_breakdown))
        else { val breakdown=panel(); Category.entries.map { c->c to Metrics.total(es.filter { it.category==c }) }.filter { it.second>0 }.sortedByDescending { it.second }.forEach { (c,amount) ->
            val r=row(); r.addView(label(c.display(this),15),LinearLayout.LayoutParams(0,-2,1f)); r.addView(label(won(amount),15,true)); breakdown.addView(r)
            gap(breakdown,8); breakdown.addView(progress(amount,total)); gap(breakdown,18)
        }
        page.addView(card(breakdown)) }
        gap(page,14); section(getString(R.string.last_months))
        val months=(5 downTo 0).map { month.minusMonths(it.toLong()) }; val totals=months.map { Metrics.total(Metrics.monthly(data.expenses,it)) }
        val chart=panel(); chart.addView(BarChart(months,totals),LinearLayout.LayoutParams(-1,dp(170))); page.addView(card(chart)); gap(page,24)
        val fuel=es.filter { it.category==Category.FUEL&&it.quantity!=null }
        val unit=if(v.fuel==FuelValues.ELECTRIC)"kWh" else "L"
        if(fuel.isNotEmpty()) {
            val sum=fuel.sumOf { it.quantity!! }; val f=panel(); f.addView(label(getString(R.string.fuel_and_charging_overview),16,true)); gap(f,12)
            f.addView(label(getString(R.string.recorded_quantity)+String.format(resources.configuration.locales[0],"%.2f",sum)+" "+unit,15)); gap(f,8)
            f.addView(label(getString(R.string.average_unit_price_for_entries_with_quantity)+won((fuel.sumOf { it.amount }/sum).toLong())+"/"+unit,12,color=muted)); page.addView(card(f)); gap(page,18)
        }
        if(v.fuel!=FuelValues.ELECTRIC) {
            val efficiency=Metrics.fuelEfficiency(data.expenses)
            page.addView(label(if(efficiency==null)getString(R.string.fuel_efficiency_needs_at_least_two_full) else getString(R.string.efficiency_across_valid_intervals)+String.format(resources.configuration.locales[0],"%.1f km/L",efficiency),13,color=muted)); gap(page,14)
        }
        page.addView(label(getString(R.string.expenses_count_in_the_month_paid_including),12,color=muted))
    }
    private fun garage() {
        val v=data.vehicle!!; section(getString(R.string.my_car),getString(R.string.edit_info)) { vehicleForm(v) }
        val p=panel(); p.addView(label(getString(R.string.vehicle_profile),11,true,green)); gap(p,12); p.addView(label(v.name,24,true)); gap(p,18); divider(p); gap(p,18)
        listOf(getString(R.string.model) to v.model.ifBlank { getString(R.string.not_entered) },getString(R.string.year_fuel) to getString(R.string.value_value_97,v.year,fuelName(v.fuel)),getString(R.string.odometer) to (number(v.odometer)+" km"),getString(R.string.monthly_budget) to if(v.budget==0L)getString(R.string.not_set) else won(v.budget)).forEach { (k,value) ->
            val r=row(); r.addView(label(k,13,color=muted),LinearLayout.LayoutParams(0,-2,1f)); r.addView(label(value,14,true)); p.addView(r); gap(p,12)
        }
        page.addView(card(p,Color.rgb(248,242,227))); gap(page,26); section(getString(R.string.maintenance_reminders),getString(R.string.add_reminder)) { reminderForm() }
        if(data.reminders.none { !it.done })empty(getString(R.string.no_reminders_yet),getString(R.string.keep_track_of_maintenance_and_insurance_renewals))
        data.reminders.filter { !it.done }.forEach { reminderRow(it) }
        val done=data.reminders.filter { it.done }
        if(done.isNotEmpty()) { gap(page,12); page.addView(label(getString(R.string.completed_reminders),13,true,muted)); gap(page,10); page.addView(button(getString(R.string.history_all),outline=true) { showHistory(done) }); done.sortedByDescending { it.date }.take(3).forEach { reminderRow(it) } }
        gap(page,12); page.addView(label(getString(R.string.check_your_vehicle_manual_and_set_maintenance),12,color=muted))
    }
    private fun settings() {
        section(getString(R.string.settings),getString(R.string.back)) { settingsOpen=false; render() }
        val p=panel(); p.addView(label(getString(R.string.app_language),18,true)); gap(p,8)
        p.addView(label(getString(R.string.choose_the_language_for_screens_and_notifications),13,color=muted)); gap(p,16)
        val tags=listOf("ko","en","ja","zh-Hans","es")
        val names=listOf("한국어","English","日本語","简体中文","Español")
        val current=resources.configuration.locales[0].language
        val selected=tags.indexOfFirst { it.substringBefore('-')==current }.coerceAtLeast(0)
        p.addView(button(names[selected],R.id.language_select,true) {
            MaterialAlertDialogBuilder(this).setTitle(getString(R.string.app_language)).setSingleChoiceItems(names.toTypedArray(),selected) { dialog,index ->
                dialog.dismiss()
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tags[index]))
            }.setNegativeButton(getString(R.string.cancel),null).show()
        }); page.addView(card(p)); gap(page,16)
        val units=panel(); units.addView(label(getString(R.string.currency_and_distance),16,true)); gap(units,8)
        units.addView(label(getString(R.string.amounts_are_in_south_korean_won_krw),13,color=muted)); page.addView(card(units))
        gap(page,26); section(getString(R.string.your_data))
        page.addView(button(getString(R.string.save_backup),R.id.backup_export,true) { export.launch("Chagok-backup-"+LocalDate.now()+".json") }); gap(page,10)
        page.addView(button(getString(R.string.restore_backup),R.id.backup_import,true) { import.launch(arrayOf("application/json","text/plain","application/octet-stream")) }); gap(page,10)
        page.addView(button(getString(R.string.export_records_as_csv),outline=true) { csv.launch("Chagok-expenses-"+LocalDate.now()+".csv") }); gap(page,12)
        page.addView(label(getString(R.string.uninstalling_deletes_your_records_keep_backup_files),12,color=muted)); gap(page,26)
        section(getString(R.string.notifications))
        val prefs=getSharedPreferences("settings",MODE_PRIVATE)
        val enabled=prefs.getBoolean("notifications",false)&&(Build.VERSION.SDK_INT<33||ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)
        page.addView(button(if(enabled)getString(R.string.turn_off_maintenance_alerts) else getString(R.string.turn_on_maintenance_alerts),outline=true) {
            if(enabled) { prefs.edit().putBoolean("notifications",false).apply(); ReminderJob.cancel(this); render() }
            else if(Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else { prefs.edit().putBoolean("notifications",true).apply(); ReminderJob.schedule(this); render(); notice(getString(R.string.maintenance_reminders_enabled)) }
        })
        gap(page,10); page.addView(label(getString(R.string.alerts_start_days_or_km_before_a),12,color=muted))
        if(ads.privacyOptionsRequired) { gap(page,20); page.addView(button(getString(R.string.ad_privacy_choices),outline=true) { ads.privacyOptions() }) }
        gap(page,24); page.addView(label(getString(R.string.chagok_121)+com.example.thirtythree_carmanagement.BuildConfig.VERSION_NAME+getString(R.string.stored_on_this_device),12,color=muted))
    }
    private fun expenseRow(parent:LinearLayout,e:Expense) {
        val p=row().apply { setPadding(dp(15),dp(16),dp(15),dp(16)) }
        p.addView(icon(if(e.category==Category.FUEL)R.drawable.ic_fuel else if(e.category==Category.SERVICE)R.drawable.ic_service else R.drawable.ic_receipt,green,24))
        p.addView(View(this),LinearLayout.LayoutParams(dp(13),1))
        val info=col(); info.addView(label(e.title,15,true)); gap(info,6); info.addView(label("${e.date.monthValue}/${e.date.dayOfMonth} · "+e.category.display(this)+if(e.place.isBlank())"" else " · "+e.place,12,color=muted))
        p.addView(info,LinearLayout.LayoutParams(0,-2,1f)); p.addView(label(won(e.amount),15,true))
        val c=card(p).apply { isClickable=true; isFocusable=true; setOnClickListener { details(e) } }; parent.addView(c); gap(parent,9)
    }
    private fun details(e:Expense) {
        val text=listOfNotNull(e.category.display(this)+" · "+e.date,won(e.amount),e.odometer?.let { number(it)+" km" },e.quantity?.let { getString(R.string.quantity)+it+if(data.vehicle?.fuel==FuelValues.ELECTRIC)" kWh" else " L" },if(e.fullTank)getString(R.string.full_tank) else null,e.place.ifBlank { null },e.memo.ifBlank { null }).joinToString("\n\n")
        lateinit var detailDialog:androidx.appcompat.app.AlertDialog
        val detail=panel(); detail.addView(label(text,15)); gap(detail,18); detail.addView(button(getString(R.string.repeat_as_a_new_expense),R.id.expense_repeat,true) { detailDialog.dismiss(); expenseForm(template=e) })
        detailDialog=MaterialAlertDialogBuilder(this).setTitle(e.title).setView(ScrollView(this).apply { addView(detail) }).setPositiveButton(getString(R.string.edit)) { _,_->expenseForm(e) }.setNegativeButton(getString(R.string.close),null).setNeutralButton(getString(R.string.delete)) { _,_->
            confirm(getString(R.string.delete_this_record),getString(R.string.it_will_be_removed_from_cost_totals)) { action({store.deleteExpense(e.id)},getString(R.string.record_deleted)) }
        }.show()
    }
    private fun reminderRow(r:Reminder) {
        val km=data.vehicle!!.odometer; val p=panel(); val h=row()
        h.addView(label(r.title,16,true),LinearLayout.LayoutParams(0,-2,1f))
        h.addView(label(if(r.done)getString(R.string.done) else if(Metrics.overdue(r,km))getString(R.string.check_now) else if(Metrics.upcoming(r,km))getString(R.string.due_soon) else getString(R.string.planned),12,true,if(!r.done&&Metrics.overdue(r,km))Color.rgb(175,79,43) else muted)); p.addView(h); gap(p,10)
        p.addView(label(listOfNotNull(r.date?.let { val days=ChronoUnit.DAYS.between(LocalDate.now(),it); "$it · "+if(days>=0)getString(R.string.days_left_value,days) else getString(R.string.days_overdue_value,-days) },
            r.odometer?.let { number(it)+" km · "+if(it>km)number(it-km)+getString(R.string.km_left) else getString(R.string.target_reached) }).joinToString("\n"),13,color=muted))
        val c=card(p).apply { isClickable=true; isFocusable=true; setOnClickListener {
            val options=if(r.done)arrayOf(getString(R.string.edit_reminder),getString(R.string.mark_as_planned),getString(R.string.delete_reminder)) else arrayOf(getString(R.string.edit_reminder),getString(R.string.mark_as_done),getString(R.string.record_maintenance_cost),getString(R.string.delete_reminder))
            MaterialAlertDialogBuilder(this@ChagokActivity).setTitle(r.title).setItems(options) { _,i->when(options[i]) {
                getString(R.string.edit_reminder)->reminderForm(r)
                getString(R.string.mark_as_planned)->action({store.saveReminder(r.copy(done=false))},getString(R.string.marked_as_planned))
                getString(R.string.mark_as_done)->completeForm(r)
                getString(R.string.record_maintenance_cost)->expenseForm(category=Category.SERVICE,initialTitle=r.title)
                else->confirm(getString(R.string.delete_this_reminder),getString(R.string.only_the_selected_reminder_will_be_deleted)) { action({store.deleteReminder(r.id)},getString(R.string.reminder_deleted)) }
            } }.show()
        } }; page.addView(c); gap(page,10)
    }
    private fun pickDay(initial:LocalDate,title:String,minimum:LocalDate?=null, choose:(LocalDate)->Unit) {
        val picker=DatePickerDialog(this,{ _,y,m,d -> choose(LocalDate.of(y,m+1,d)) },initial.year,initial.monthValue-1,initial.dayOfMonth)
        minimum?.let { picker.datePicker.minDate=it.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() }
        picker.setTitle(title)
        picker.show()
    }

    private fun showHistory(done:List<Reminder>) {
        val body=col()
        val search=field(body,getString(R.string.maintenance_item))
        val list=ListView(this)
        body.addView(list,LinearLayout.LayoutParams(-1,dp(300)))
        fun update() {
            val rows=done.filter { it.title.contains(search.text.toString(),true) }.sortedByDescending { it.date }
            list.adapter=ArrayAdapter(this,android.R.layout.simple_list_item_1,rows.map {
                listOfNotNull(it.title,it.date?.toString(),it.odometer?.let { km -> number(km)+" km" }).joinToString("  ·  ")
            })
        }
        search.addTextChangedListener(object:TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { update() }
            override fun afterTextChanged(s:Editable?) {}
        })
        update()
        MaterialAlertDialogBuilder(this).setTitle(getString(R.string.history_all)).setView(body).setPositiveButton(getString(R.string.close),null).show()
    }

    private fun completeForm(reminder:Reminder) {
        val (dialog,form)=sheet(reminder.title)
        var day=LocalDate.now()
        val date=button(day.toString(),outline=true) {}
        date.setOnClickListener { pickDay(day,getString(R.string.date)) { day=it; date.text=it.toString() } }
        form.addView(date)
        val km=field(form,getString(R.string.current_odometer_km),data.vehicle?.odometer?.toString().orEmpty(),numeric=true)
        val amount=field(form,getString(R.string.cost_optional),numeric=true)
        val distance=field(form,getString(R.string.repeat_km),numeric=true)
        val months=field(form,getString(R.string.repeat_months),numeric=true)
        val error=label("",13,color=Color.RED)
        form.addView(error)
        form.addView(button(getString(R.string.mark_as_done)) {
            val reading=km.text.toString().toLongOrNull()
            fun optional(input:TextInputEditText)=if(input.text.isNullOrBlank()) 0L else input.text.toString().toLongOrNull() ?: -1L
            val cost=optional(amount); val interval=optional(distance); val period=optional(months)
            if(reading==null || reading !in 0..9_999_999L || cost !in 0..1_000_000_000L ||
                interval !in 0..(9_999_999L-reading) || period !in 0..1200 || day.isAfter(LocalDate.now()) || day.year<1900) {
                error.text=getString(R.string.check_completion); return@button
            }
            val completed=reminder.copy(date=day,odometer=reading,done=true)
            val next=if(period>0 || interval>0) Reminder(title=reminder.title,date=if(period>0) day.plusMonths(period) else null,odometer=if(interval>0) reading+interval else null) else null
            val expense=if(cost>0) Expense(category=Category.SERVICE,title=reminder.title,amount=cost,date=day,odometer=reading) else null
            action({ store.complete(completed,expense,next) },getString(R.string.saved)) { dialog.dismiss() }
        })
        showSheet(dialog)
    }

    private fun vehicleForm(existing:Vehicle?=data.vehicle) {
        val (dialog,f)=sheet(if(existing==null)getString(R.string.add_my_car_150) else getString(R.string.edit_vehicle))
        val name=field(f,getString(R.string.vehicle_name),existing?.name?:"",R.id.vehicle_name)
        val model=field(f,getString(R.string.model_optional),existing?.model?:"")
        val year=field(f,getString(R.string.year),existing?.year?.toString()?:LocalDate.now().year.toString(),numeric=true)
        f.addView(label(getString(R.string.fuel_type),13,true,muted)); gap(f,6)
        val fuels=FuelValues.all; val fuel=spinner(fuels.map { fuelName(it) },fuels.indexOf(existing?.fuel?:FuelValues.PETROL)); fuel.isEnabled=data.expenses.none { it.category==Category.FUEL }; f.addView(fuel); gap(f,16)
        val km=field(f,getString(R.string.current_odometer_km),existing?.odometer?.toString()?:"",R.id.vehicle_km,numeric=true)
        val budget=field(f,getString(R.string.monthly_budget_krw_optional),existing?.budget?.takeIf { it>0 }?.toString()?:"",numeric=true)
        val error=label("",13,color=Color.rgb(174,64,40)); f.addView(error)
        f.addView(button(getString(R.string.save_vehicle),R.id.vehicle_save) {
            val k=km.text.toString().toLongOrNull(); val y=year.text.toString().toIntOrNull(); val b=if(budget.text.isNullOrBlank())0L else budget.text.toString().toLongOrNull()
            if(k==null||y==null||b==null) { error.text=getString(R.string.check_the_numeric_year_odometer_and_budget); return@button }
            val v=Vehicle(name.text.toString().trim(),model.text.toString().trim(),y,fuels[fuel.selectedItemPosition],k,b)
            val issue=Rules.vehicle(v); if(issue!=null) { error.text=LocalizedErrors.message(this,issue); return@button }
            action({store.saveVehicle(v)},getString(R.string.vehicle_saved)) { dialog.dismiss() }
        }); showSheet(dialog)
    }
    private fun expenseForm(existing:Expense?=null,category:Category=Category.FUEL,initialTitle:String="",template:Expense?=null) {
        val (dialog,f)=sheet(if(existing==null)getString(R.string.record_expense) else getString(R.string.edit_record))
        if(template!=null) { f.addView(label(getString(R.string.previous_title_amount_and_place_copied_check),13,color=muted)); gap(f,16) }
        f.addView(label(getString(R.string.category),13,true,muted)); val select=spinner(Category.entries.map { it.display(this) },(existing?.category?:template?.category?:category).ordinal); f.addView(select); gap(f,16)
        val title=field(f,getString(R.string.record_title),existing?.title?:template?.title?:initialTitle,R.id.expense_title)
        val money=field(f,getString(R.string.amount_paid_krw),existing?.amount?.toString()?:template?.amount?.toString()?:"",R.id.expense_amount,numeric=true)
        var date=existing?.date?:LocalDate.now()
        val dateButton=button(getString(R.string.date)+date,R.id.expense_date,true) {
            DatePickerDialog(this,{ _,y,m,d->date=LocalDate.of(y,m+1,d); f.findViewById<MaterialButton>(R.id.expense_date).text=getString(R.string.date)+date },date.year,date.monthValue-1,date.dayOfMonth).apply { datePicker.maxDate=System.currentTimeMillis() }.show()
        }; f.addView(dateButton); gap(f,16)
        val km=field(f,getString(R.string.odometer_km_optional),existing?.odometer?.toString()?:"",R.id.expense_km,numeric=true)
        val fuelFields=col(); f.addView(fuelFields); val unit=if(data.vehicle?.fuel==FuelValues.ELECTRIC)"kWh" else "L"
        val qty=field(fuelFields,getString(R.string.fuel_charge_quantity_value_optional,unit),existing?.quantity?.toString()?:"",R.id.expense_quantity,numeric=true,decimal=true)
        val full=CheckBox(this).apply { text=getString(R.string.filled_to_a_full_tank); isChecked=existing?.fullTank?:false; visibility=if(unit=="kWh")View.GONE else View.VISIBLE }; fuelFields.addView(full); gap(fuelFields,12)
        val place=field(f,getString(R.string.station_workshop_or_place_optional),existing?.place?:template?.place?:"")
        val memo=field(f,getString(R.string.note_optional),existing?.memo?:"",single=false)
        select.onItemSelectedListener=selection { i->
            fuelFields.visibility=if(Category.entries[i]==Category.FUEL)View.VISIBLE else View.GONE
            if(existing==null&&title.text.isNullOrBlank())title.setText(Category.entries[i].display(this))
        }
        val error=label("",13,color=Color.rgb(174,64,40)); f.addView(error); gap(f,10)
        f.addView(button(getString(R.string.save_record),R.id.expense_save) {
            val c=Category.entries[select.selectedItemPosition]; val amount=money.text.toString().toLongOrNull()
            val k=if(km.text.isNullOrBlank())null else km.text.toString().toLongOrNull()
            val q=if(c!=Category.FUEL||qty.text.isNullOrBlank())null else qty.text.toString().replace(',','.').toDoubleOrNull()
            if(amount==null||(!km.text.isNullOrBlank()&&k==null)||(c==Category.FUEL&&!qty.text.isNullOrBlank()&&q==null)) { error.text=getString(R.string.check_the_numeric_amount_odometer_and_quantity); return@button }
            val e=Expense(existing?.id?:0,c,title.text.toString().trim(),amount,date,k,q,c==Category.FUEL&&unit!="kWh"&&full.isChecked,place.text.toString().trim(),memo.text.toString().trim())
            val issue=Rules.expense(e); if(issue!=null) { error.text=LocalizedErrors.message(this,issue); return@button }
            action({store.saveExpense(e)},getString(R.string.expense_saved)) { dialog.dismiss(); if(existing==null)root.post { ads.transition(AdPlacement.RECORD_SAVED) } }
        }); showSheet(dialog)
    }
    private fun reminderForm(existing:Reminder?=null) {
        val (dialog,f)=sheet(if(existing==null)getString(R.string.add_maintenance_reminder) else getString(R.string.edit_maintenance_reminder))
        val title=field(f,getString(R.string.maintenance_item),existing?.title?:"",R.id.reminder_title); var date=existing?.date
        val dateButton=button(if(date==null)getString(R.string.choose_due_date_optional) else getString(R.string.due_date)+date,R.id.reminder_date,true) {
            val initial=date?:LocalDate.now().plusMonths(1)
            DatePickerDialog(this,{ _,y,m,d->date=LocalDate.of(y,m+1,d); f.findViewById<MaterialButton>(R.id.reminder_date).text=getString(R.string.due_date)+date },initial.year,initial.monthValue-1,initial.dayOfMonth).show()
        }; f.addView(dateButton); f.addView(button(getString(R.string.clear_due_date),outline=true) { date=null; dateButton.text=getString(R.string.choose_due_date_optional) }); gap(f,16)
        val km=field(f,getString(R.string.target_odometer_km_optional),existing?.odometer?.toString()?:"",R.id.reminder_km,numeric=true)
        f.addView(label(getString(R.string.set_a_date_a_distance_or_both),13,color=muted)); gap(f,16)
        val error=label("",13,color=Color.rgb(174,64,40)); f.addView(error)
        f.addView(button(getString(R.string.save_reminder),R.id.reminder_save) {
            val k=if(km.text.isNullOrBlank())null else km.text.toString().toLongOrNull()
            if(!km.text.isNullOrBlank()&&k==null) { error.text=getString(R.string.enter_a_numeric_target_odometer_reading); return@button }
            val r=Reminder(existing?.id?:0,title.text.toString().trim(),date,k,existing?.done?:false)
            val issue=Rules.reminder(r); if(issue!=null) { error.text=LocalizedErrors.message(this,issue); return@button }
            action({store.saveReminder(r)},getString(R.string.maintenance_reminder_saved)) { dialog.dismiss() }
        }); showSheet(dialog)
    }
    private fun sheet(title:String):Pair<BottomSheetDialog,LinearLayout> {
        val dialog=BottomSheetDialog(this); val f=col().apply { setBackgroundColor(Color.WHITE); setPadding(dp(24),dp(24),dp(24),dp(32)) }
        val h=row(); h.addView(label(title,24,true),LinearLayout.LayoutParams(0,-2,1f)); h.addView(button(getString(R.string.close),outline=true) { dialog.dismiss() }.apply { minWidth=0; setSingleLine(true); setPadding(dp(8),0,dp(8),0) },LinearLayout.LayoutParams(-2,-2)); f.addView(h); gap(f,22)
        dialog.setContentView(ScrollView(this).apply { isFillViewport=true; addView(f) })
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        return dialog to f
    }
    private fun showSheet(dialog:BottomSheetDialog) {
        dialog.show(); val v=dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)!!
        v.layoutParams.height=(resources.displayMetrics.heightPixels*0.90).toInt()
        com.google.android.material.bottomsheet.BottomSheetBehavior.from(v).apply { state=com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED; skipCollapsed=true }
    }
    private fun monthSelector() {
        val r=row(); r.addView(button("",outline=true) { month=month.minusMonths(1); render() }.apply { minWidth=0; contentDescription=getString(R.string.previous_month); icon=ContextCompat.getDrawable(this@ChagokActivity,R.drawable.ic_prev); iconTint=android.content.res.ColorStateList.valueOf(green); iconGravity=MaterialButton.ICON_GRAVITY_TEXT_START; iconSize=dp(18); iconPadding=0; setPadding(dp(12),0,dp(12),0) },LinearLayout.LayoutParams(dp(48),dp(48)))
        r.addView(label(getString(R.string.value_value,month.year,month.monthValue),17,true).apply { gravity=Gravity.CENTER },LinearLayout.LayoutParams(0,-2,1f))
        r.addView(button("",outline=true) { month=month.plusMonths(1); render() }.apply { minWidth=0; contentDescription=getString(R.string.next_month); icon=ContextCompat.getDrawable(this@ChagokActivity,R.drawable.ic_next); iconTint=android.content.res.ColorStateList.valueOf(green); iconGravity=MaterialButton.ICON_GRAVITY_TEXT_START; iconSize=dp(18); iconPadding=0; setPadding(dp(12),0,dp(12),0) },LinearLayout.LayoutParams(dp(48),dp(48))); page.addView(r); gap(page,18)
    }
    private fun section(title:String,action:String?=null,click:()->Unit={}) {
        val r=row(); r.addView(label(title,19,true),LinearLayout.LayoutParams(0,-2,1f))
        if(action!=null)r.addView(button(action,if(action==getString(R.string.add_record))R.id.record_add else View.NO_ID,true,click).apply { textSize=12f; minWidth=0 })
        page.addView(r); gap(page,14)
    }
    private fun empty(title:String,desc:String) { val p=panel(); val top=row(); top.addView(icon(R.drawable.ic_receipt,green,22)); top.addView(View(this),LinearLayout.LayoutParams(dp(10),1)); top.addView(label(title,15,true),LinearLayout.LayoutParams(0,-2,1f)); p.addView(top); gap(p,12); divider(p); gap(p,12); p.addView(label(desc,13,color=muted)); page.addView(card(p,Color.rgb(237,244,225))) }
    private fun field(parent:LinearLayout,hint:String,value:String="",viewId:Int=View.NO_ID,numeric:Boolean=false,decimal:Boolean=false,single:Boolean=true):TextInputEditText {
        val edit=TextInputEditText(this).apply {
            id=viewId; textSize=16f; setTextColor(ink); setText(value); isSingleLine=single
            inputType=if(numeric)InputType.TYPE_CLASS_NUMBER or (if(decimal)InputType.TYPE_NUMBER_FLAG_DECIMAL else 0) else InputType.TYPE_CLASS_TEXT or (if(single)InputType.TYPE_TEXT_FLAG_CAP_SENTENCES else InputType.TYPE_TEXT_FLAG_MULTI_LINE)
            if(numeric&&decimal)keyListener=android.text.method.DigitsKeyListener.getInstance("0123456789.,")
            if(!single)minLines=3
        }
        val layout=TextInputLayout(this).apply { this.hint=hint; boxBackgroundMode=TextInputLayout.BOX_BACKGROUND_OUTLINE; setBoxCornerRadii(dp(12).toFloat(),dp(12).toFloat(),dp(12).toFloat(),dp(12).toFloat()); addView(edit,LinearLayout.LayoutParams(-1,-2)) }
        parent.addView(layout); gap(parent,16); return edit
    }
    private fun spinner(options:List<String>,selected:Int)=Spinner(this).apply { adapter=ArrayAdapter(this@ChagokActivity,android.R.layout.simple_spinner_dropdown_item,options); setSelection(selected.coerceAtLeast(0)); minimumHeight=dp(52); setPadding(dp(12),0,dp(12),0); background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.WHITE); cornerRadius=dp(14).toFloat(); setStroke(dp(1).coerceAtLeast(2),Color.rgb(187,200,185)) } }
    private fun selection(block:(Int)->Unit)=object:AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent:AdapterView<*>?,view:View?,position:Int,id:Long) { block(position) }
        override fun onNothingSelected(parent:AdapterView<*>?) {}
    }
    private fun fuelName(value:String):String = getString(when(value) {
        FuelValues.PETROL->R.string.fuel_petrol; FuelValues.DIESEL->R.string.fuel_diesel
        FuelValues.HYBRID->R.string.fuel_hybrid; FuelValues.ELECTRIC->R.string.fuel_electric
        else->R.string.fuel_lpg
    })
    private fun number(value:Long)=java.text.NumberFormat.getIntegerInstance(resources.configuration.locales[0]).format(value)
    private fun won(value:Long)=getString(R.string.money_krw,number(value))
    private fun col()=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
    private fun row()=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
    private fun panel()=col().apply { setPadding(dp(20),dp(20),dp(20),dp(20)) }
    private fun label(value:String,size:Int,bold:Boolean=false,color:Int=ink)=TextView(this).apply { text=value; textSize=size.toFloat(); setTextColor(color); includeFontPadding=false; setLineSpacing(dp(3).toFloat(),1f); typeface=Typeface.create("sans-serif",if(bold)Typeface.BOLD else Typeface.NORMAL) }
    private fun card(view:View,color:Int=Color.rgb(250,252,246))=MaterialCardView(this).apply { radius=dp(22).toFloat(); cardElevation=dp(1).toFloat(); strokeWidth=dp(2); strokeColor=if(color==green)Color.rgb(71,109,79) else Color.rgb(113,142,117); setCardBackgroundColor(color); addView(view,ViewGroup.LayoutParams(-1,-2)) }
    private fun button(text:String,viewId:Int=View.NO_ID,outline:Boolean=false,click:()->Unit)=MaterialButton(this,null,if(outline)com.google.android.material.R.attr.materialButtonOutlinedStyle else com.google.android.material.R.attr.materialButtonStyle).apply {
        this.text=text; id=viewId; textSize=14f; isAllCaps=false; cornerRadius=dp(16); minHeight=dp(52); insetTop=0; insetBottom=0
        if(outline) { setTextColor(green); backgroundTintList=android.content.res.ColorStateList.valueOf(Color.WHITE); strokeWidth=dp(1).coerceAtLeast(2); strokeColor=android.content.res.ColorStateList.valueOf(Color.rgb(137,158,137)) }
        else { backgroundTintList=android.content.res.ColorStateList.valueOf(green); setTextColor(Color.WHITE) }
        setOnClickListener { if(!busy)click() }
    }
    private fun icon(id:Int,color:Int,size:Int)=ImageView(this).apply { setImageResource(id); if(id!=R.drawable.brand_mark)setColorFilter(color); layoutParams=LinearLayout.LayoutParams(dp(size),dp(size)); importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO }
    private fun progress(value:Long,total:Long)=LinearProgressIndicator(this).apply { max=100; progress=if(total<=0)0 else (value.toDouble()/total*100).toInt().coerceIn(0,100); setIndicatorColor(green); trackColor=Color.rgb(230,235,223) }
    private fun divider(parent:LinearLayout) { parent.addView(View(this).apply { setBackgroundColor(Color.rgb(213,222,207)) },LinearLayout.LayoutParams(-1,dp(1))) }
    private fun gap(parent:LinearLayout,n:Int) { parent.addView(View(this),LinearLayout.LayoutParams(1,dp(n))) }
    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    private fun notice(text:String) { Snackbar.make(root,LocalizedErrors.message(this,text),Snackbar.LENGTH_LONG).setAnchorView(nav).show() }
    private fun confirm(title:String,message:String,work:()->Unit) { MaterialAlertDialogBuilder(this).setTitle(title).setMessage(message).setNegativeButton(getString(R.string.cancel),null).setPositiveButton(getString(R.string.delete)) { _,_->work() }.show() }
    private inner class BarChart(private val months:List<YearMonth>,private val values:List<Long>):View(this@ChagokActivity) {
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        private val compact=android.icu.text.CompactDecimalFormat.getInstance(resources.configuration.locales[0],android.icu.text.CompactDecimalFormat.CompactStyle.SHORT)
        init { contentDescription=months.zip(values).joinToString(", ") { getString(R.string.month_value_value,it.first.monthValue,won(it.second)) }; importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES }
        override fun onDraw(c:Canvas) {
            super.onDraw(c); val column=width/6f; val base=height-dp(26).toFloat(); val maximum=values.maxOrNull()?.coerceAtLeast(1)?:1
            values.forEachIndexed { i,value->
                val top=base-(value.toDouble()/maximum*(base-dp(30))).toFloat()
                paint.color=if(i==5)green else Color.rgb(205,215,190)
                c.drawRoundRect(column*i+column*0.22f,top,column*i+column*0.78f,base,dp(5).toFloat(),dp(5).toFloat(),paint)
                paint.color=muted; paint.textSize=dp(11).toFloat(); paint.textAlign=Paint.Align.CENTER
                c.drawText(getString(R.string.value_189,months[i].monthValue),column*i+column/2,height-dp(6).toFloat(),paint)
                c.drawText(compact.format(value),column*i+column/2,(top-dp(8)).coerceAtLeast(dp(12).toFloat()),paint)
            }
        }
    }
}



