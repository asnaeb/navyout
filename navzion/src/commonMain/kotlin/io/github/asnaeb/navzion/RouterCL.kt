package io.github.asnaeb.navzion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

private val routerCL = staticCompositionLocalOf<Router> { error("Router not provided") }

@Composable
internal fun RouterProvider(router: Router, content: @Composable () -> Unit) {
    CompositionLocalProvider(routerCL provides router, content)
}

@Composable
internal fun getRouter() = routerCL.current