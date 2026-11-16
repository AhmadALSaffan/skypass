package com.skypass.core.designsystem.component

import androidx.annotation.DrawableRes
import com.skypass.core.designsystem.R

/** The bundled picture for a car's `image_path` ("cars/kia-cerato.png"). */
@DrawableRes
fun carImageRes(path: String): Int = when (path.substringAfterLast('/').substringBefore('.')) {
    "nissan-note" -> R.drawable.car_nissan_note
    "jeep-wrangler" -> R.drawable.car_jeep_wrangler
    "nissan-juke" -> R.drawable.car_nissan_juke
    "tesla-model-3" -> R.drawable.car_tesla_model_3
    "tesla-model-s" -> R.drawable.car_tesla_model_s
    "ford-mustang" -> R.drawable.car_ford_mustang
    "chevrolet-camaro" -> R.drawable.car_chevrolet_camaro
    else -> R.drawable.car_kia_cerato
}
