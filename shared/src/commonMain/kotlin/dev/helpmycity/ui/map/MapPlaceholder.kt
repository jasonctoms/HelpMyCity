package dev.helpmycity.ui.map

import androidx.compose.animation.EnterExitState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.ui.LocalNavAnimatedContentScope

/**
 * Whether this screen has finished sliding in and has not started sliding out.
 *
 * On the web, a map re-renders into the app's canvas on every frame it gets
 * drawn, and a screen transition turns that into every frame of the animation.
 * Right after a page load, while the style and tiles are still coming in, that
 * stalls the transition and blacks out the map. So maps just sit out transitions
 * completely.
 */
@Composable
internal fun isScreenSettled(): Boolean {
    val transition = LocalNavAnimatedContentScope.current.transition
    return transition.currentState == EnterExitState.Visible &&
        transition.targetState == EnterExitState.Visible
}

/** What stands in for a map while [isScreenSettled] is false. */
@Composable
internal fun MapPlaceholder(modifier: Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainer))
}
