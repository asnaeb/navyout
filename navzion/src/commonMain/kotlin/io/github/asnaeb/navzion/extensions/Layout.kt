package io.github.asnaeb.navzion.extensions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import io.github.asnaeb.navzion.Layout
import io.github.asnaeb.navzion.getCurrentLayout
import io.github.asnaeb.navzion.getRouter
import kotlin.reflect.KClass

@Composable
fun <T : Layout> KClass<T>.isLoading(): Boolean {
    return getRouter().safeAccess(this).loading.collectAsState().value
}

@Composable
@Suppress("UNCHECKED_CAST")
fun <T : Layout> KClass<T>.getOrNull(): T? {
    val router = getRouter()
    val currentLayout = getCurrentLayout()

    if (router.safeAccess(currentLayout).withParents.any { it.type == this }) {
        return router.safeAccess(this).arg.value as T?
    }

    return router.safeAccess(this).arg.collectAsState().value as T?
}

@Composable
fun <T : Layout> KClass<T>.get(): T {
    return this.getOrNull() ?: error("Layout ${this.simpleName} not active")
}

@Composable
fun <T : Layout> KClass<T>.isActive(): Boolean {
    val router = getRouter()
    val activeRouteParentType = router.safeAccess(router.getActiveRoute()::class).parentType

    return router.safeAccess(activeRouteParentType).withParents.any { it.type == this }
}