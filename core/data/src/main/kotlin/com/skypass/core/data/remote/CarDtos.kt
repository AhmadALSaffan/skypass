package com.skypass.core.data.remote

import com.skypass.core.model.Car
import com.skypass.core.model.CarReservation
import com.skypass.core.model.CarType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
internal data class CarDto(
    val id: String,
    @SerialName("location_code") val locationCode: String,
    val name: String,
    val type: String,
    val seats: Int,
    val bags: Int,
    val transmission: String = "Auto",
    @SerialName("price_per_day_usd") val pricePerDayUsd: Double,
    val supplier: String,
    @SerialName("free_cancel") val freeCancel: Boolean = true,
    @SerialName("stage_color") val stageColor: String,
    @SerialName("image_path") val imagePath: String,
) {
    fun toModel() = Car(
        id = id,
        locationCode = locationCode.trim(),
        name = name,
        type = CarType.fromApi(type),
        seats = seats,
        bags = bags,
        transmission = transmission,
        pricePerDayUsd = pricePerDayUsd.roundToInt(),
        supplier = supplier,
        freeCancellation = freeCancel,
        stageColor = parseHex(stageColor),
        imagePath = imagePath,
    )
}

/** Row of `reserve_car(...)`. */
@Serializable
internal data class CarReservationDto(
    val reference: String,
    @SerialName("total_usd") val totalUsd: Double,
    val days: Int,
    @SerialName("one_way_fee_usd") val oneWayFeeUsd: Double,
    @SerialName("extras_usd") val extrasUsd: Double = 0.0,
    @SerialName("young_driver_fee_usd") val youngDriverFeeUsd: Double = 0.0,
) {
    fun toModel() = CarReservation(
        reference = reference,
        totalUsd = totalUsd.roundToInt(),
        days = days,
        oneWayFeeUsd = oneWayFeeUsd.roundToInt(),
        extrasUsd = extrasUsd.roundToInt(),
        youngDriverFeeUsd = youngDriverFeeUsd.roundToInt(),
    )
}
