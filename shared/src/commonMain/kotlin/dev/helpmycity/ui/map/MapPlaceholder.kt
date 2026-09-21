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
 * On the web a map re-renders into the app's canvas every frame it is drawn,
 * which a screen transition turns into every frame of the animation. Right after
 * a page load, with the style and tiles still arriving, that stalls the
 * transition and blacks the map out, so maps sit out transitions entirely.
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
