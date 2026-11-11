package com.thirtythree_carmanagement

import android.content.Context
import com.example.thirtythree_carmanagement.R

/** Persisted values stay compatible with existing databases and JSON backups. */
object FuelValues {
    const val PETROL="휘발유"
    const val DIESEL="경유"
    const val HYBRID="하이브리드"
    const val ELECTRIC="전기"
    val all=listOf(PETROL,DIESEL,"LPG",HYBRID,ELECTRIC)
}
fun Category.display(context:Context)=context.getString(when(this) {
    Category.FUEL->R.string.category_fuel
    Category.SERVICE->R.string.category_service
    Category.INSURANCE->R.string.category_insurance
    Category.TAX->R.string.category_tax
    Category.PARKING->R.string.category_parking
    Category.TOLL->R.string.category_toll
    Category.WASH->R.string.category_wash
    Category.OTHER->R.string.category_other
})
