package io.github.asnaeb.navzion.extensions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import io.github.asnaeb.navzion.Route
import io.github.asnaeb.navzion.getRouter
import kotlin.reflect.KClass

@Composable
fun <T : Route> KClass<T>.isLoading(): Boolean {
    return getRouter().safeAccess(this).loading.collectAsState().value
}

@Composable
fun <T : Route> KClass<T>.get(): T {
    val router = getRouter()

    try {
        @Suppress("UNCHECKED_CAST")
        return router.backStack.value.last { it::class == this } as T
    }
    catch (_: NoSuchElementException) {
        error("Active route is not of type ${this.qualifiedName}")
    }
}

@Composable
fun Route.isActive(): Boolean {
    return getRouter().activeRoute == this
}