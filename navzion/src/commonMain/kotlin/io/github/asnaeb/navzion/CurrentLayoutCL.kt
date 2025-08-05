package io.github.asnaeb.navzion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.reflect.KClass

private val currentLayoutCL = staticCompositionLocalOf<KClass<out Layout>> { error("CurrentLayout not provided") }

@Composable
internal fun CurrentLayoutProvider(currentLayout: KClass<out Layout>, content: @Composable () -> Unit) {
    CompositionLocalProvider(currentLayoutCL provides currentLayout, content)
}

@Composable
fun getCurrentLayout() = currentLayoutCL.current