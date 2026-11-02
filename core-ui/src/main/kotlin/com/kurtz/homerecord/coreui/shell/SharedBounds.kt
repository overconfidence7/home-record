package com.kurtz.homerecord.coreui.shell

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.navigation3.ui.LocalNavAnimatedContentScope

/**
 * The shell's [SharedTransitionScope]; [HomeRecordApp] provides it around its NavDisplay, so it is set only
 * inside a screen the shell is showing. Previews, screenshot tests and the catalog host leave it null.
 */
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** Keys for the elements two screens share across a push and its pop (ui-standards motion). */
object SharedKeys {
    /** The room page's "View in 3D" button and the 3D room view it opens into (AC-C22). */
    fun room3D(roomId: String): String = "room-3d:$roomId"
}

/**
 * Container transform between two screens of one back stack: the element tagged with [key] on the screen
 * being left grows into (or shrinks back from) the element with the same key on the screen arriving.
 * Outside the shell (previews, screenshot tests) it changes nothing.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedScreenBounds(key: String): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val animated = LocalNavAnimatedContentScope.current
    // The theme's MotionScheme.standard() spatial spring (rule 29): settles in well under 400 ms.
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Rect>()
    return with(shared) {
        this@sharedScreenBounds.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = animated,
            // Each frame lays the content out at the animated size (a button's label does not get stretched
            // into a full screen; the scene's surface is sized once it settles).
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            boundsTransform = BoundsTransform { _, _ -> spec },
        )
    }
}
