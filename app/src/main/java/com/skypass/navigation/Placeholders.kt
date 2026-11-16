package com.skypass.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skypass.core.designsystem.component.OutlinedButton
import com.skypass.core.designsystem.component.SkypassTopBar
import com.skypass.core.designsystem.theme.SkypassTheme
import com.skypass.core.domain.usecase.SignOutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject

/* Stand-in for Settings until Phase 12 builds it: for now it only signs out. */

@Serializable
data object SettingsDestination

@HiltViewModel
class SettingsPlaceholderViewModel @Inject constructor(
    private val signOut: SignOutUseCase,
) : ViewModel() {
    fun signOut(done: () -> Unit) {
        viewModelScope.launch {
            signOut()
            done()
        }
    }
}

@Composable
fun SettingsPlaceholder(onBack: () -> Unit, onSignedOut: () -> Unit, viewModel: SettingsPlaceholderViewModel = hiltViewModel()) {
    Column(Modifier.fillMaxSize().background(SkypassTheme.colors.bg).statusBarsPadding()) {
        SkypassTopBar("Settings", onBack = onBack)
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Notifications, preferences and security arrive in Phase 12.", style = SkypassTheme.typography.body, color = SkypassTheme.colors.text2)
            OutlinedButton(
                "Sign out",
                onClick = { viewModel.signOut(onSignedOut) },
                contentColor = SkypassTheme.colors.danger,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
