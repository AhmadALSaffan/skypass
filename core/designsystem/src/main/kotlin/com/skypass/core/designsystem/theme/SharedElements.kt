@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.skypass.core.designsystem.theme

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/** The app's SharedTransitionLayout, provided once around the NavHost. */
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** The AnimatedContentScope of the current navigation destination. */
val LocalNavAnimatedScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * A container that morphs into its counterpart on the next screen (a results ticket into the
 * booking summary). Does nothing in previews, tests, or screens outside the shared layout.
 */
@Composable
fun Modifier.sharedContainer(key: String): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val animated = LocalNavAnimatedScope.current ?: return this
    return with(shared) {
        this@sharedContainer.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = animated,
            boundsTransform = { _, _ -> tween(Motion.SCREEN + 120, easing = Motion.EmphasizedDecelerate) },
        )
    }
}

/** One element (an airline logo) that flies to the same element on the next screen. */
@Composable
fun Modifier.sharedItem(key: String): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val animated = LocalNavAnimatedScope.current ?: return this
    return with(shared) {
        this@sharedItem.sharedElement(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = animated,
            boundsTransform = { _, _ -> tween(Motion.SCREEN + 120, easing = Motion.EmphasizedDecelerate) },
        )
    }
}
