package com.erdman.erdstream.ui.theme

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.DelegatableNode
import com.mudita.mmd.ThemeMMD
import com.mudita.mmd.eInkColorScheme
import com.mudita.mmd.eInkTypography

// Standard Material ripple is an animated pulse -- visible motion that
// ghosts on e-ink. MMD's own components (ButtonMMD, etc.) are already
// ripple-free, but it has no IconButtonMMD, and this app's own
// .combinedClickable call sites don't set indication = null explicitly, so
// this no-op Indication is still provided app-wide below to cover those.
private object NoRippleIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode {
        return object : Modifier.Node() {}
    }

    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = System.identityHashCode(this)
}

@Composable
fun ErdStreamTheme(content: @Composable () -> Unit) {
    ThemeMMD(
        colorScheme = eInkColorScheme,
        typography = eInkTypography,
    ) {
        CompositionLocalProvider(LocalIndication provides NoRippleIndication) {
            content()
        }
    }
}
