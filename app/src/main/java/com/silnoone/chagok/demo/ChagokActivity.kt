package com.silnoone.chagok.demo

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
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.silnoone.chagok.demo.R
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
    private val bg=Color.rgb(246,247,244)
    private val executor=Executors.newSingleThreadExecutor()
    private lateinit var store:CarStore
    private lateinit var ads:AdsController
    private lateinit var root:LinearLayout
    private lateinit var page:LinearLayout
    private lateinit var nav:BottomNavigationView
    private var data=Snapshot()
    private var tab=0
    private var month=YearMonth.now()
    private var busy=false
    private var ready=false
    private var query=""
    private var filter:Category?=null
    private var limit=100
    // Public showcase: production file I/O and notification scheduling are withheld.
    private inner class DemoFileAction {
        fun launch(value: Any) { notice("공개 데모에는 파일 백업·복원이 포함되지 않아요.") }
    }
    private val export=DemoFileAction()
    private val import=DemoFileAction()
    private val csv=DemoFileAction()
    override fun onCreate(state:Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(state); enableEdgeToEdge()
        store=CarStore(this); tab=state?.getInt("tab")?:0
        month=state?.getString("month")?.let { YearMonth.parse(it) }?:YearMonth.now()
        query=state?.getString("query")?:""
        filter=state?.getString("filter")?.let { Category.valueOf(it) }
        root=col().apply { setBackgroundColor(bg) }; setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v,insets ->
            val p=insets.getInsets(WindowInsetsCompat.Type.systemBars()); v.setPadding(p.left,p.top,p.right,p.bottom); WindowInsetsCompat.CONSUMED
        }
        val scroll=ScrollView(this).apply { isFillViewport=true }
        page=col().apply { setPadding(dp(22),dp(24),dp(22),dp(28)) }; scroll.addView(page)
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        val bannerHost=FrameLayout(this).apply { id=R.id.ads_banner_host; setPadding(dp(16),dp(12),dp(16),dp(12)); visibility=View.GONE }
        root.addView(bannerHost,LinearLayout.LayoutParams(-1,-2))
        ads=AdsController(this,bannerHost,com.silnoone.chagok.demo.BuildConfig.DEBUG && intent.getBooleanExtra("qaDisableAds",false))
        nav=BottomNavigationView(this).apply {
            setBackgroundColor(Color.WHITE); labelVisibilityMode=BottomNavigationView.LABEL_VISIBILITY_LABELED
            itemIconTintList=ContextCompat.getColorStateList(this@ChagokActivity,R.color.nav_colors)
            itemTextColor=itemIconTintList
            itemActiveIndicatorColor=android.content.res.ColorStateList.valueOf(Color.rgb(234,240,227))
            listOf("홈" to R.drawable.ic_home,"기록" to R.drawable.ic_history,"통계" to R.drawable.ic_chart,"내 차" to R.drawable.ic_car).forEachIndexed { i,p -> menu.add(0,100+i,i,p.first).setIcon(p.second) }
            selectedItemId=100+tab
            setOnItemSelectedListener { val previous=tab; tab=it.itemId-100; scroll.scrollTo(0,0); render(); if(tab==2 && previous!=2) ads.transition(AdPlacement.STATS_ENTERED); true }
        }
        root.addView(nav,LinearLayout.LayoutParams(-1,dp(80))); render()
        executor.execute {
            val result=runCatching { store.snapshot() }
            runOnUiThread { if(!isDestroyed) { ready=true; result.onSuccess { data=it }.onFailure { notice("기록을 읽지 못했어요.") }; render() } }
        }
        ReminderJob.schedule(this)
        ads.start()
    }
    override fun onSaveInstanceState(outState:Bundle) { outState.putInt("tab",tab); outState.putString("month",month.toString()); outState.putString("query",query); outState.putString("filter",filter?.name); super.onSaveInstanceState(outState) }
    override fun onResume() { super.onResume(); if(::ads.isInitialized)ads.resume() }
    override fun onPause() { if(::ads.isInitialized)ads.pause(); super.onPause() }
    override fun onDestroy() { if(::ads.isInitialized)ads.destroy(); executor.execute { store.close() }; executor.shutdown(); super.onDestroy() }
    private fun action(work:()->Unit,message:String="저장했어요.",after:()->Unit={}) {
        if(busy)return; busy=true
        executor.execute {
            val result=runCatching { work(); store.snapshot() }
            runOnUiThread {
                busy=false
                if(isDestroyed||isFinishing)return@runOnUiThread
                result.onSuccess { data=it; after(); render(); notice(message) }.onFailure { notice(it.message?:"저장하지 못했어요. 다시 시도해 주세요.") }
            }
        }
    }
    private fun render() {
        page.removeAllViews()
        val h=row(); h.addView(label("차곡",25,true),LinearLayout.LayoutParams(0,-2,1f)); h.addView(label("주유부터 정비까지",12,color=muted)); page.addView(h); gap(page,24)
        if(!ready) { page.addView(label("기록을 불러오는 중이에요.",15)); return }
        if(data.vehicle==null) { welcome(); return }
        when(tab) { 0->home(); 1->records(); 2->stats(); 3->garage() }
    }
    private fun welcome() {
        val p=panel(); p.addView(icon(R.drawable.ic_car,green,48)); gap(p,28)
        p.addView(label("내 차를 챙기는\n가장 간단한 습관",30,true)); gap(p,14)
        p.addView(label("주유한 날, 정비한 부품, 매달 드는 비용.\n흩어진 기록을 한곳에 모아 보세요.",15,color=muted)); gap(p,28)
        p.addView(button("내 차 등록하기",R.id.vehicle_add) { vehicleForm() }); page.addView(card(p)); gap(page,26)
        listOf("빠른 기록" to "금액과 날짜만으로 지출을 남길 수 있어요.","한눈에 보는 유지비" to "월별 비용과 항목별 지출을 확인해요.","다음 관리 일정" to "날짜와 주행거리로 내 차를 챙겨요.").forEach {
            page.addView(label(it.first,16,true)); gap(page,6); page.addView(label(it.second,14,color=muted)); gap(page,22)
        }
        page.addView(button("백업 파일로 시작하기",outline=true) { import.launch(arrayOf("application/json","text/plain","application/octet-stream")) })
        gap(page,12); page.addView(label("공개 데모의 예시 기록입니다. 앱 프로세스가 종료되면 입력한 내용이 초기화돼요.",12,color=muted))
        gap(page,18); page.addView(button("개인정보처리방침",outline=true) { openPolicy() })
    }
    private fun home() {
        val v=data.vehicle!!
        val p=panel().apply { setBackgroundColor(green) }
        val h=row(); h.addView(label("MY CAR",11,true,Color.rgb(209,233,170)),LinearLayout.LayoutParams(0,-2,1f)); h.addView(icon(R.drawable.ic_car,Color.rgb(209,233,170),34)); p.addView(h)
        gap(p,18); p.addView(label(v.name,27,true,Color.WHITE)); gap(p,8)
        p.addView(label(listOf(v.model,"${v.year}년",v.fuel).filter { it.isNotBlank() }.joinToString(" · "),13,color=Color.rgb(190,208,198)))
        gap(p,24); p.addView(label("현재 주행거리",12,color=Color.rgb(190,208,198))); gap(p,6); p.addView(label(number(v.odometer)+" km",23,true,Color.WHITE))
        page.addView(card(p,green)); gap(page,22); monthSelector()
        val es=Metrics.monthly(data.expenses,month); val total=Metrics.total(es); val prev=Metrics.total(Metrics.monthly(data.expenses,month.minusMonths(1)))
        page.addView(label("차량에 쓴 돈",13,color=muted)); gap(page,8); page.addView(label(won(total),32,true).apply { id=R.id.monthly_total }); gap(page,8)
        val difference=total-prev
        page.addView(label(if(prev==0L)"이달 기록 ${es.size}건" else if(difference==0L)"지난달과 같은 금액이에요." else "지난달보다 ${won(kotlin.math.abs(difference))} ${if(difference>0)"더 썼어요" else "덜 썼어요"}",13,color=muted)); gap(page,22)
        val quick=row(); quick.addView(button("주유·충전 기록",R.id.fuel_add) { expenseForm(category=Category.FUEL) },LinearLayout.LayoutParams(0,dp(52),1f))
        quick.addView(View(this),LinearLayout.LayoutParams(dp(10),1)); quick.addView(button("정비 기록",R.id.service_add,true) { expenseForm(category=Category.SERVICE) },LinearLayout.LayoutParams(0,dp(52),1f)); page.addView(quick)
        gap(page,12); page.addView(button("다른 지출 기록",R.id.expense_add,true) { expenseForm(category=Category.OTHER) }); gap(page,28)
        section("다가오는 관리","일정 추가") { reminderForm() }
        val upcoming=data.reminders.filter { Metrics.upcoming(it,v.odometer) }.sortedBy { it.date?:LocalDate.MAX }
        if(upcoming.isEmpty())empty("임박한 일정이 없어요.","보험 만기나 정비 예정일을 등록해 보세요.") else upcoming.take(3).forEach { reminderRow(it) }
        gap(page,26); section("최근 기록","전체 보기") { nav.selectedItemId=101 }
        if(data.expenses.isEmpty())empty("첫 기록을 남겨 보세요.","기록을 남기면 유지비가 자동으로 정리돼요.") else data.expenses.take(4).forEach { expenseRow(page,it) }
    }
    private fun records() {
        section("차량 기록","기록 추가") { expenseForm() }
        page.addView(label("주유부터 정비까지, 차곡차곡 쌓인 이력",13,color=muted)); gap(page,16)
        val search=field(page,"이름·장소·메모 검색",query,R.id.record_search)
        val select=spinner(listOf("전체 항목")+Category.entries.map { it.label },filter?.ordinal?.plus(1)?:0); page.addView(select); gap(page,18)
        val holder=col(); page.addView(holder)
        fun update() {
            holder.removeAllViews()
            val list=data.expenses.filter { (filter==null||it.category==filter)&&(query.isBlank()||(it.title+" "+it.place+" "+it.memo).contains(query,true)) }
            holder.addView(label("${list.size}건 · ${won(Metrics.total(list))}",13,true,muted)); gap(holder,12)
            if(list.isEmpty()) { holder.addView(label("조건에 맞는 기록이 없어요.",15,color=muted)); return }
            var last=""
            list.take(limit).forEach { e ->
                val m="${e.date.year}년 ${e.date.monthValue}월"
                if(m!=last) { gap(holder,12); holder.addView(label(m,13,true,muted)); gap(holder,10); last=m }
                expenseRow(holder,e)
            }
            if(list.size>limit)holder.addView(button("100건 더 보기",outline=true) { limit+=100; update() })
        }
        select.onItemSelectedListener=selection { i->filter=if(i==0)null else Category.entries[i-1]; limit=100; update() }
        search.addTextChangedListener(object:TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { query=s.toString(); limit=100; update() }
            override fun afterTextChanged(s:Editable?) {}
        }); update()
    }
    private fun stats() {
        section("유지비 통계"); monthSelector()
        val es=Metrics.monthly(data.expenses,month); val total=Metrics.total(es); val v=data.vehicle!!
        val p=panel(); p.addView(label("이번 달 총지출",13,color=muted)); gap(p,8); p.addView(label(won(total),32,true))
        if(v.budget>0) {
            gap(p,18); p.addView(label("월 예산 ${won(v.budget)} · "+if(total>v.budget)won(total-v.budget)+" 초과" else won(v.budget-total)+" 남음",13,color=muted))
            gap(p,10); p.addView(progress(total,v.budget))
        } else { gap(p,12); p.addView(label("내 차에서 월 예산을 설정할 수 있어요.",12,color=muted)) }
        page.addView(card(p)); gap(page,26); section("항목별 지출")
        if(es.isEmpty())empty("이달 기록이 없어요.","기록이 쌓이면 지출 비중을 보여드려요.")
        else Category.entries.map { c->c to Metrics.total(es.filter { it.category==c }) }.filter { it.second>0 }.sortedByDescending { it.second }.forEach { (c,amount) ->
            val r=row(); r.addView(label(c.label,15),LinearLayout.LayoutParams(0,-2,1f)); r.addView(label(won(amount),15,true)); page.addView(r)
            gap(page,8); page.addView(progress(amount,total)); gap(page,18)
        }
        gap(page,14); section("최근 6개월")
        val months=(5 downTo 0).map { month.minusMonths(it.toLong()) }; val totals=months.map { Metrics.total(Metrics.monthly(data.expenses,it)) }
        page.addView(BarChart(months,totals),LinearLayout.LayoutParams(-1,dp(170))); gap(page,24)
        val fuel=es.filter { it.category==Category.FUEL&&it.quantity!=null }
        val unit=if(v.fuel=="전기")"kWh" else "L"
        if(fuel.isNotEmpty()) {
            val sum=fuel.sumOf { it.quantity!! }; val f=panel(); f.addView(label("주유·충전 한눈에 보기",16,true)); gap(f,12)
            f.addView(label("기록된 수량  "+String.format(Locale.KOREA,"%.2f",sum)+" "+unit,15)); gap(f,8)
            f.addView(label("수량이 있는 기록의 평균 단가  "+won((fuel.sumOf { it.amount }/sum).toLong())+"/"+unit,12,color=muted)); page.addView(card(f)); gap(page,18)
        }
        page.addView(label("연비 계산은 실제 앱에서 제공되며 공개 데모에는 포함되지 않아요.",13,color=muted)); gap(page,14)
        page.addView(label("실제 결제한 달에 합산합니다. 보험·세금도 결제한 달에 포함되며, 기록한 금액만 계산해요.",12,color=muted))
    }
    private fun garage() {
        val v=data.vehicle!!; section("내 차","정보 수정") { vehicleForm(v) }
        val p=panel(); p.addView(label(v.name,24,true)); gap(p,18)
        listOf("차종" to v.model.ifBlank { "미입력" },"연식·연료" to "${v.year}년 · ${v.fuel}","현재 주행거리" to (number(v.odometer)+" km"),"월 예산" to if(v.budget==0L)"미설정" else won(v.budget)).forEach { (k,value) ->
            val r=row(); r.addView(label(k,13,color=muted),LinearLayout.LayoutParams(0,-2,1f)); r.addView(label(value,14,true)); p.addView(r); gap(p,12)
        }
        page.addView(card(p)); gap(page,26); section("관리 일정","일정 추가") { reminderForm() }
        if(data.reminders.none { !it.done })empty("등록한 일정이 없어요.","정비 예정일과 보험 만기 등을 챙겨 보세요.")
        data.reminders.filter { !it.done }.forEach { reminderRow(it) }
        val done=data.reminders.filter { it.done }
        if(done.isNotEmpty()) { gap(page,12); page.addView(label("완료한 일정",13,true,muted)); gap(page,10); done.take(10).forEach { reminderRow(it) } }
        gap(page,12); page.addView(label("정비 주기는 차량 설명서를 확인하고 직접 설정해 주세요. 주행거리 알림은 마지막으로 입력한 거리를 기준으로 해요.",12,color=muted))
        gap(page,26); section("기록 보관")
        page.addView(button("백업 파일 저장",R.id.backup_export,true) { export.launch("차곡-백업-"+LocalDate.now()+".json") }); gap(page,10)
        page.addView(button("백업 파일 복원",R.id.backup_import,true) { import.launch(arrayOf("application/json","text/plain","application/octet-stream")) }); gap(page,10)
        page.addView(button("기록 CSV 내보내기",outline=true) { csv.launch("차곡-지출-"+LocalDate.now()+".csv") }); gap(page,12)
        page.addView(label("공개 데모는 메모리의 예시 데이터로 동작하며 파일을 내보내지 않습니다.",12,color=muted)); gap(page,26)
        page.addView(label("공개 데모 · 영구 저장, 백업, 알림, 광고, 연비 계산은 포함되지 않아요.",12,color=muted))
        if(ads.privacyOptionsRequired) { gap(page,20); page.addView(button("광고 개인정보 설정",outline=true) { ads.privacyOptions() }) }
        gap(page,18); page.addView(button("개인정보처리방침",outline=true) { openPolicy() })
        gap(page,24); page.addView(label("차곡 "+com.silnoone.chagok.demo.BuildConfig.VERSION_NAME+" · 공개 데모",12,color=muted))
    }
    private fun openPolicy() {
        try {
            startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://rudazio.blogspot.com/p/blog-page.html")))
        } catch (_: android.content.ActivityNotFoundException) { notice("링크를 열 수 있는 브라우저가 없어요.") }
    }
    private fun expenseRow(parent:LinearLayout,e:Expense) {
        val p=row().apply { setPadding(dp(15),dp(16),dp(15),dp(16)) }
        p.addView(icon(if(e.category==Category.FUEL)R.drawable.ic_fuel else if(e.category==Category.SERVICE)R.drawable.ic_service else R.drawable.ic_receipt,green,24))
        p.addView(View(this),LinearLayout.LayoutParams(dp(13),1))
        val info=col(); info.addView(label(e.title,15,true)); gap(info,6); info.addView(label("${e.date.monthValue}/${e.date.dayOfMonth} · "+e.category.label+if(e.place.isBlank())"" else " · "+e.place,12,color=muted))
        p.addView(info,LinearLayout.LayoutParams(0,-2,1f)); p.addView(label(won(e.amount),15,true))
        val c=card(p).apply { isClickable=true; isFocusable=true; setOnClickListener { details(e) } }; parent.addView(c); gap(parent,9)
    }
    private fun details(e:Expense) {
        val text=listOfNotNull(e.category.label+" · "+e.date,won(e.amount),e.odometer?.let { number(it)+" km" },e.quantity?.let { "수량 "+it+if(data.vehicle?.fuel=="전기")" kWh" else " L" },if(e.fullTank)"가득 주유" else null,e.place.ifBlank { null },e.memo.ifBlank { null }).joinToString("\n\n")
        MaterialAlertDialogBuilder(this).setTitle(e.title).setMessage(text).setPositiveButton("수정") { _,_->expenseForm(e) }.setNegativeButton("닫기",null).setNeutralButton("삭제") { _,_->
            confirm("이 기록을 삭제할까요?","유지비 통계에서도 제외돼요. 현재 주행거리는 유지됩니다.") { action({store.deleteExpense(e.id)},"기록을 삭제했어요.") }
        }.show()
    }
    private fun reminderRow(r:Reminder) {
        val km=data.vehicle!!.odometer; val p=panel(); val h=row()
        h.addView(label(r.title,16,true),LinearLayout.LayoutParams(0,-2,1f))
        h.addView(label(if(r.done)"완료" else if(Metrics.overdue(r,km))"확인 필요" else if(Metrics.upcoming(r,km))"곧 예정" else "예정",12,true,if(!r.done&&Metrics.overdue(r,km))Color.rgb(175,79,43) else muted)); p.addView(h); gap(p,10)
        p.addView(label(listOfNotNull(r.date?.let { val days=ChronoUnit.DAYS.between(LocalDate.now(),it); "$it · "+if(days>=0)"${days}일 남음" else "${-days}일 지남" },
            r.odometer?.let { number(it)+" km · "+if(it>km)number(it-km)+" km 남음" else "기준 도달" }).joinToString("\n"),13,color=muted))
        val c=card(p).apply { isClickable=true; isFocusable=true; setOnClickListener {
            val options=if(r.done)arrayOf("일정 수정","다시 예정으로","일정 삭제") else arrayOf("일정 수정","완료 처리","정비 지출 기록","일정 삭제")
            MaterialAlertDialogBuilder(this@ChagokActivity).setTitle(r.title).setItems(options) { _,i->when(options[i]) {
                "일정 수정"->reminderForm(r)
                "다시 예정으로"->action({store.saveReminder(r.copy(done=false))},"예정으로 변경했어요.")
                "완료 처리"->action({store.saveReminder(r.copy(done=true))},"일정을 완료했어요. 지출은 별도로 기록해 주세요.")
                "정비 지출 기록"->expenseForm(category=Category.SERVICE,initialTitle=r.title)
                else->confirm("이 일정을 삭제할까요?","선택한 관리 일정만 삭제됩니다.") { action({store.deleteReminder(r.id)},"일정을 삭제했어요.") }
            } }.show()
        } }; page.addView(c); gap(page,10)
    }
    private fun vehicleForm(existing:Vehicle?=data.vehicle) {
        val (dialog,f)=sheet(if(existing==null)"내 차 등록" else "차량 정보 수정")
        val name=field(f,"차량 이름",existing?.name?:"",R.id.vehicle_name)
        val model=field(f,"차종 (선택)",existing?.model?:"")
        val year=field(f,"연식",existing?.year?.toString()?:LocalDate.now().year.toString(),numeric=true)
        f.addView(label("연료 종류",13,true,muted)); gap(f,6)
        val fuels=listOf("휘발유","경유","LPG","하이브리드","전기"); val fuel=spinner(fuels,fuels.indexOf(existing?.fuel?:"휘발유")); fuel.isEnabled=data.expenses.none { it.category==Category.FUEL }; f.addView(fuel); gap(f,16)
        val km=field(f,"현재 주행거리 (km)",existing?.odometer?.toString()?:"",R.id.vehicle_km,numeric=true)
        val budget=field(f,"월 유지비 예산 (원, 선택)",existing?.budget?.takeIf { it>0 }?.toString()?:"",numeric=true)
        val error=label("",13,color=Color.rgb(174,64,40)); f.addView(error)
        f.addView(button("차량 저장",R.id.vehicle_save) {
            val k=km.text.toString().toLongOrNull(); val y=year.text.toString().toIntOrNull(); val b=if(budget.text.isNullOrBlank())0L else budget.text.toString().toLongOrNull()
            if(k==null||y==null||b==null) { error.text="연식·주행거리·예산을 숫자로 확인해 주세요."; return@button }
            val v=Vehicle(name.text.toString().trim(),model.text.toString().trim(),y,fuels[fuel.selectedItemPosition],k,b)
            val issue=Rules.vehicle(v); if(issue!=null) { error.text=issue; return@button }
            action({store.saveVehicle(v)},"내 차를 저장했어요.") { dialog.dismiss() }
        }); showSheet(dialog)
    }
    private fun expenseForm(existing:Expense?=null,category:Category=Category.FUEL,initialTitle:String="") {
        val (dialog,f)=sheet(if(existing==null)"지출 기록" else "기록 수정")
        f.addView(label("항목",13,true,muted)); val select=spinner(Category.entries.map { it.label },(existing?.category?:category).ordinal); f.addView(select); gap(f,16)
        val title=field(f,"기록 이름",existing?.title?:initialTitle,R.id.expense_title)
        val money=field(f,"결제 금액 (원)",existing?.amount?.toString()?:"",R.id.expense_amount,numeric=true)
        var date=existing?.date?:LocalDate.now()
        val dateButton=button("날짜  "+date,R.id.expense_date,true) {
            DatePickerDialog(this,{ _,y,m,d->date=LocalDate.of(y,m+1,d); f.findViewById<MaterialButton>(R.id.expense_date).text="날짜  "+date },date.year,date.monthValue-1,date.dayOfMonth).apply { datePicker.maxDate=System.currentTimeMillis() }.show()
        }; f.addView(dateButton); gap(f,16)
        val km=field(f,"주행거리 (km, 선택)",existing?.odometer?.toString()?:"",R.id.expense_km,numeric=true)
        val fuelFields=col(); f.addView(fuelFields); val unit=if(data.vehicle?.fuel=="전기")"kWh" else "L"
        val qty=field(fuelFields,"주유·충전량 (${unit}, 선택)",existing?.quantity?.toString()?:"",R.id.expense_quantity,numeric=true,decimal=true)
        val full=CheckBox(this).apply { text="가득 주유했어요"; isChecked=existing?.fullTank?:false; visibility=if(unit=="kWh")View.GONE else View.VISIBLE }; fuelFields.addView(full); gap(fuelFields,12)
        val place=field(f,"주유소·정비소·장소 (선택)",existing?.place?:"")
        val memo=field(f,"메모 (선택)",existing?.memo?:"",single=false)
        select.onItemSelectedListener=selection { i->
            fuelFields.visibility=if(Category.entries[i]==Category.FUEL)View.VISIBLE else View.GONE
            if(existing==null&&title.text.isNullOrBlank())title.setText(Category.entries[i].label)
        }
        val error=label("",13,color=Color.rgb(174,64,40)); f.addView(error); gap(f,10)
        f.addView(button("기록 저장",R.id.expense_save) {
            val c=Category.entries[select.selectedItemPosition]; val amount=money.text.toString().toLongOrNull()
            val k=if(km.text.isNullOrBlank())null else km.text.toString().toLongOrNull()
            val q=if(c!=Category.FUEL||qty.text.isNullOrBlank())null else qty.text.toString().toDoubleOrNull()
            if(amount==null||(!km.text.isNullOrBlank()&&k==null)||(c==Category.FUEL&&!qty.text.isNullOrBlank()&&q==null)) { error.text="금액·주행거리·수량을 숫자로 확인해 주세요."; return@button }
            val e=Expense(existing?.id?:0,c,title.text.toString().trim(),amount,date,k,q,c==Category.FUEL&&unit!="kWh"&&full.isChecked,place.text.toString().trim(),memo.text.toString().trim())
            val issue=Rules.expense(e); if(issue!=null) { error.text=issue; return@button }
            action({store.saveExpense(e)},"지출을 저장했어요.") { dialog.dismiss(); if(existing==null)root.post { ads.transition(AdPlacement.RECORD_SAVED) } }
        }); showSheet(dialog)
    }
    private fun reminderForm(existing:Reminder?=null) {
        val (dialog,f)=sheet(if(existing==null)"관리 일정 추가" else "관리 일정 수정")
        val title=field(f,"관리 항목",existing?.title?:"",R.id.reminder_title); var date=existing?.date
        val dateButton=button(if(date==null)"예정일 선택 (선택)" else "예정일  "+date,R.id.reminder_date,true) {
            val initial=date?:LocalDate.now().plusMonths(1)
            DatePickerDialog(this,{ _,y,m,d->date=LocalDate.of(y,m+1,d); f.findViewById<MaterialButton>(R.id.reminder_date).text="예정일  "+date },initial.year,initial.monthValue-1,initial.dayOfMonth).show()
        }; f.addView(dateButton); f.addView(button("예정일 지우기",outline=true) { date=null; dateButton.text="예정일 선택 (선택)" }); gap(f,16)
        val km=field(f,"목표 주행거리 (km, 선택)",existing?.odometer?.toString()?:"",R.id.reminder_km,numeric=true)
        f.addView(label("날짜와 주행거리 중 하나만 설정해도 돼요. 둘 다 설정하면 먼저 도달하는 기준으로 알려드려요.",13,color=muted)); gap(f,16)
        val error=label("",13,color=Color.rgb(174,64,40)); f.addView(error)
        f.addView(button("일정 저장",R.id.reminder_save) {
            val k=if(km.text.isNullOrBlank())null else km.text.toString().toLongOrNull()
            if(!km.text.isNullOrBlank()&&k==null) { error.text="목표 주행거리를 숫자로 입력해 주세요."; return@button }
            val r=Reminder(existing?.id?:0,title.text.toString().trim(),date,k,existing?.done?:false)
            val issue=Rules.reminder(r); if(issue!=null) { error.text=issue; return@button }
            action({store.saveReminder(r)},"관리 일정을 저장했어요.") { dialog.dismiss() }
        }); showSheet(dialog)
    }
    private fun sheet(title:String):Pair<BottomSheetDialog,LinearLayout> {
        val dialog=BottomSheetDialog(this); val f=col().apply { setBackgroundColor(Color.WHITE); setPadding(dp(24),dp(24),dp(24),dp(32)) }
        val h=row(); h.addView(label(title,24,true),LinearLayout.LayoutParams(0,-2,1f)); h.addView(button("닫기",outline=true) { dialog.dismiss() }.apply { minWidth=0; setSingleLine(true); setPadding(dp(8),0,dp(8),0) },LinearLayout.LayoutParams(dp(72),dp(48))); f.addView(h); gap(f,22)
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
        val r=row(); r.addView(button("",outline=true) { month=month.minusMonths(1); render() }.apply { minWidth=0; contentDescription="이전 달"; icon=ContextCompat.getDrawable(this@ChagokActivity,R.drawable.ic_prev); iconTint=android.content.res.ColorStateList.valueOf(green); iconGravity=MaterialButton.ICON_GRAVITY_TEXT_START; iconSize=dp(18); iconPadding=0; setPadding(dp(12),0,dp(12),0) },LinearLayout.LayoutParams(dp(48),dp(48)))
        r.addView(label("${month.year}년 ${month.monthValue}월",17,true).apply { gravity=Gravity.CENTER },LinearLayout.LayoutParams(0,-2,1f))
        r.addView(button("",outline=true) { month=month.plusMonths(1); render() }.apply { minWidth=0; contentDescription="다음 달"; icon=ContextCompat.getDrawable(this@ChagokActivity,R.drawable.ic_next); iconTint=android.content.res.ColorStateList.valueOf(green); iconGravity=MaterialButton.ICON_GRAVITY_TEXT_START; iconSize=dp(18); iconPadding=0; setPadding(dp(12),0,dp(12),0) },LinearLayout.LayoutParams(dp(48),dp(48))); page.addView(r); gap(page,18)
    }
    private fun section(title:String,action:String?=null,click:()->Unit={}) {
        val r=row(); r.addView(label(title,19,true),LinearLayout.LayoutParams(0,-2,1f))
        if(action!=null)r.addView(button(action,if(action=="기록 추가")R.id.record_add else View.NO_ID,true,click).apply { textSize=12f; minWidth=0 })
        page.addView(r); gap(page,14)
    }
    private fun empty(title:String,desc:String) { val p=panel(); p.addView(label(title,15,true)); gap(p,8); p.addView(label(desc,13,color=muted)); page.addView(card(p)) }
    private fun field(parent:LinearLayout,hint:String,value:String="",viewId:Int=View.NO_ID,numeric:Boolean=false,decimal:Boolean=false,single:Boolean=true):TextInputEditText {
        val edit=TextInputEditText(this).apply {
            id=viewId; textSize=16f; setTextColor(ink); setText(value); isSingleLine=single
            inputType=if(numeric)InputType.TYPE_CLASS_NUMBER or (if(decimal)InputType.TYPE_NUMBER_FLAG_DECIMAL else 0) else InputType.TYPE_CLASS_TEXT or (if(single)InputType.TYPE_TEXT_FLAG_CAP_SENTENCES else InputType.TYPE_TEXT_FLAG_MULTI_LINE)
            if(!single)minLines=3
        }
        val layout=TextInputLayout(this).apply { this.hint=hint; boxBackgroundMode=TextInputLayout.BOX_BACKGROUND_OUTLINE; setBoxCornerRadii(dp(12).toFloat(),dp(12).toFloat(),dp(12).toFloat(),dp(12).toFloat()); addView(edit,LinearLayout.LayoutParams(-1,-2)) }
        parent.addView(layout); gap(parent,16); return edit
    }
    private fun spinner(options:List<String>,selected:Int)=Spinner(this).apply { adapter=ArrayAdapter(this@ChagokActivity,android.R.layout.simple_spinner_dropdown_item,options); setSelection(selected.coerceAtLeast(0)); minimumHeight=dp(48); setBackgroundColor(Color.rgb(239,242,235)) }
    private fun selection(block:(Int)->Unit)=object:AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent:AdapterView<*>?,view:View?,position:Int,id:Long) { block(position) }
        override fun onNothingSelected(parent:AdapterView<*>?) {}
    }
    private fun col()=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
    private fun row()=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
    private fun panel()=col().apply { setPadding(dp(20),dp(20),dp(20),dp(20)) }
    private fun label(value:String,size:Int,bold:Boolean=false,color:Int=ink)=TextView(this).apply { text=value; textSize=size.toFloat(); setTextColor(color); includeFontPadding=false; setLineSpacing(dp(3).toFloat(),1f); typeface=Typeface.create("sans-serif",if(bold)Typeface.BOLD else Typeface.NORMAL) }
    private fun card(view:View,color:Int=Color.WHITE)=MaterialCardView(this).apply { radius=dp(16).toFloat(); cardElevation=0f; strokeWidth=if(color==Color.WHITE)dp(1) else 0; strokeColor=Color.rgb(229,233,223); setCardBackgroundColor(color); addView(view,ViewGroup.LayoutParams(-1,-2)) }
    private fun button(text:String,viewId:Int=View.NO_ID,outline:Boolean=false,click:()->Unit)=MaterialButton(this,null,if(outline)com.google.android.material.R.attr.materialButtonOutlinedStyle else com.google.android.material.R.attr.materialButtonStyle).apply {
        this.text=text; id=viewId; textSize=14f; isAllCaps=false; cornerRadius=dp(12); minHeight=dp(52); insetTop=0; insetBottom=0
        if(outline) { setTextColor(green); strokeColor=android.content.res.ColorStateList.valueOf(Color.rgb(215,222,209)) }
        else { backgroundTintList=android.content.res.ColorStateList.valueOf(green); setTextColor(Color.WHITE) }
        setOnClickListener { if(!busy)click() }
    }
    private fun icon(id:Int,color:Int,size:Int)=ImageView(this).apply { setImageResource(id); setColorFilter(color); layoutParams=LinearLayout.LayoutParams(dp(size),dp(size)); importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO }
    private fun progress(value:Long,total:Long)=LinearProgressIndicator(this).apply { max=100; progress=if(total<=0)0 else (value.toDouble()/total*100).toInt().coerceIn(0,100); setIndicatorColor(green); trackColor=Color.rgb(230,235,223) }
    private fun gap(parent:LinearLayout,n:Int) { parent.addView(View(this),LinearLayout.LayoutParams(1,dp(n))) }
    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    private fun notice(text:String) { Snackbar.make(root,text,Snackbar.LENGTH_LONG).setAnchorView(nav).show() }
    private fun confirm(title:String,message:String,work:()->Unit) { MaterialAlertDialogBuilder(this).setTitle(title).setMessage(message).setNegativeButton("취소",null).setPositiveButton("삭제") { _,_->work() }.show() }
    private inner class BarChart(private val months:List<YearMonth>,private val values:List<Long>):View(this@ChagokActivity) {
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        init { contentDescription=months.zip(values).joinToString(", ") { "${it.first.monthValue}월 ${won(it.second)}" }; importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES }
        override fun onDraw(c:Canvas) {
            super.onDraw(c); val column=width/6f; val base=height-dp(26).toFloat(); val maximum=values.maxOrNull()?.coerceAtLeast(1)?:1
            values.forEachIndexed { i,value->
                val top=base-(value.toDouble()/maximum*(base-dp(30))).toFloat()
                paint.color=if(i==5)green else Color.rgb(205,215,190)
                c.drawRoundRect(column*i+column*0.22f,top,column*i+column*0.78f,base,dp(5).toFloat(),dp(5).toFloat(),paint)
                paint.color=muted; paint.textSize=dp(11).toFloat(); paint.textAlign=Paint.Align.CENTER
                c.drawText("${months[i].monthValue}월",column*i+column/2,height-dp(6).toFloat(),paint)
                c.drawText(if(value>=10000)String.format(Locale.KOREA,"%.1f만",value/10000.0) else number(value),column*i+column/2,(top-dp(8)).coerceAtLeast(dp(12).toFloat()),paint)
            }
        }
    }
}
