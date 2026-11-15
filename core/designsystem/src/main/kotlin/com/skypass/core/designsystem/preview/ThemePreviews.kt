package com.skypass.core.designsystem.preview

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

/** Light + dark phone previews at the board's 375×812 frame. */
@Preview(name = "Light", device = "spec:width=375dp,height=812dp", showBackground = true)
@Preview(
    name = "Dark",
    device = "spec:width=375dp,height=812dp",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
annotation class ThemePreviews
