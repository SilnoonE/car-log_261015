package com.thirtythree_carmanagement

import android.content.Context
import com.example.thirtythree_carmanagement.R

/** Maps stable domain validation messages without changing backup or database values. */
object LocalizedErrors {
    fun message(context:Context,value:String):String = when(value) {
        "차량 이름을 1~40자로 입력해 주세요." -> context.getString(R.string.enter_a_vehicle_name_of_characters)
        "차종은 60자까지 입력할 수 있어요." -> context.getString(R.string.the_model_can_contain_up_to_characters)
        "연식을 확인해 주세요." -> context.getString(R.string.check_the_vehicle_year)
        "연료를 선택해 주세요." -> context.getString(R.string.choose_a_fuel_type)
        "주행거리는 0~9,999,999km로 입력해 주세요." -> context.getString(R.string.enter_an_odometer_reading_from_to_km)
        "월 예산을 확인해 주세요." -> context.getString(R.string.check_your_monthly_budget)
        "기록 이름을 1~60자로 입력해 주세요." -> context.getString(R.string.enter_a_record_title_of_characters)
        "금액은 1~1,000,000,000원으로 입력해 주세요." -> context.getString(R.string.enter_an_amount_from_to_krw)
        "지출 날짜는 오늘 또는 이전 날짜로 입력해 주세요." -> context.getString(R.string.use_today_s_date_or_an_earlier)
        "주행거리를 확인해 주세요." -> context.getString(R.string.check_the_odometer_reading)
        "주유·충전량은 0 초과, 1,000 이하로 입력해 주세요." -> context.getString(R.string.fuel_charge_quantity_must_be_greater_than)
        "주유·충전 기록에서만 수량을 입력할 수 있어요." -> context.getString(R.string.quantity_is_only_available_for_fuel_charging)
        "가득 주유는 주유량과 주행거리를 함께 입력해 주세요." -> context.getString(R.string.full_tank_entries_need_both_quantity_and)
        "장소는 80자, 메모는 1,000자까지 입력할 수 있어요." -> context.getString(R.string.place_up_to_characters_note_up_to)
        "관리 항목을 1~60자로 입력해 주세요." -> context.getString(R.string.enter_a_maintenance_item_of_characters)
        "예정일 또는 목표 주행거리 중 하나를 입력해 주세요." -> context.getString(R.string.enter_a_due_date_or_target_odometer)
        "예정일을 확인해 주세요." -> context.getString(R.string.check_the_due_date)
        "목표 주행거리를 확인해 주세요." -> context.getString(R.string.check_the_target_odometer_reading)
        "원" -> context.getString(R.string.krw)
        "지원하지 않는 데이터 버전입니다." -> context.getString(R.string.unsupported_data_version)
        "주유·충전 기록이 있는 차량의 연료 종류는 변경할 수 없어요." -> context.getString(R.string.fuel_type_cannot_be_changed_while_fuel)
        "기록된 주행거리보다 현재 주행거리를 작게 설정할 수 없어요." -> context.getString(R.string.the_current_odometer_cannot_be_lower_than)
        "먼저 내 차를 등록해 주세요." -> context.getString(R.string.add_your_car_first)
        "수정할 기록을 찾을 수 없어요." -> context.getString(R.string.could_not_find_the_record_to_edit)
        "백업 파일은 10MB 이하만 지원해요." -> context.getString(R.string.backup_files_must_be_mb_or_smaller)
        "차곡 백업 파일이 아니거나 지원하지 않는 버전이에요." -> context.getString(R.string.this_is_not_a_chagok_backup_or)
        "차량 정보가 없는 백업이에요." -> context.getString(R.string.the_backup_contains_no_vehicle_information)
        else -> value
    }
}
