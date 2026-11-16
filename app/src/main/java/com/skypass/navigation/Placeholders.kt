package com.skypass.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.skypass.core.designsystem.component.Avatar
import com.skypass.core.designsystem.component.SkypassTextButton
import com.skypass.core.designsystem.component.SkypassTopBar
import com.skypass.core.designsystem.theme.SkypassTheme
import com.skypass.core.domain.usecase.ObserveSessionUseCase
import com.skypass.core.domain.usecase.SignOutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/*
 * Stand-ins for screens built in later phases: Booking (5), Explore (8), Saved (11), Profile (11).
 * Each says which phase replaces it.
 */

@Composable
fun ComingSoon(title: String, phase: Int, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().background(SkypassTheme.colors.bg).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = SkypassTheme.typography.headline, color = SkypassTheme.colors.ink)
        Text("Arrives in Phase $phase", style = SkypassTheme.typography.body, color = SkypassTheme.colors.text2)
    }
}


@HiltViewModel
class ProfilePlaceholderViewModel @Inject constructor(
    observeSession: ObserveSessionUseCase,
    private val signOut: SignOutUseCase,
) : ViewModel() {
    val session = observeSession().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun signOut(done: () -> Unit) {
        viewModelScope.launch {
            signOut()
            done()
        }
    }
}

@Composable
fun ProfilePlaceholder(onSignedOut: () -> Unit, onMyTrips: () -> Unit, viewModel: ProfilePlaceholderViewModel = hiltViewModel()) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().background(SkypassTheme.colors.bg).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        session?.let { user ->
            Avatar(user.initials, size = 64.dp)
            Text(user.fullName ?: "Guest traveller", style = SkypassTheme.typography.titleLg, color = SkypassTheme.colors.ink)
            user.email?.let { Text(it, style = SkypassTheme.typography.bodySm, color = SkypassTheme.colors.text2) }
        }
        Text("Full profile arrives in Phase 11", style = SkypassTheme.typography.body, color = SkypassTheme.colors.text2)
        SkypassTextButton("My trips", onMyTrips, color = SkypassTheme.colors.brand)
        SkypassTextButton("Sign out", { viewModel.signOut(onSignedOut) }, color = SkypassTheme.colors.danger)
    }
}
