package io.github.asnaeb.navzion

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.reflect.KClass

@NodeMarker
class RouteBuilder<Arg : Route, Data>(
    internal val type: KClass<Arg>,
    internal val parentType: KClass<out Layout>,
    val router: Router
) {
    @PublishedApi
    internal var data: Data? = null

    @PublishedApi
    internal var contentComposable: (@Composable (Data) -> Unit)? = null

    internal var pendingComposable: (@Composable () -> Unit)? = null

    @PublishedApi
    internal var loaderFn: (suspend (Any) -> Unit)? = null

    internal var loaderRan = false

    @PublishedApi
    internal val loading = MutableStateFlow(false)

    internal val parents by lazy {
        router.getParents(type)
    }

    internal var enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        router.safeAccess(parentType).childrenEnterTransition(this)
    }

    internal var exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        router.safeAccess(parentType).childrenExitTransition(this)
    }

    internal var sizeTransform: AnimatedContentTransitionScope<NavBackStackEntry>.() -> SizeTransform = {
        router.safeAccess(parentType).childrenSizeTransform(this)
    }

    internal fun render(builder: NavGraphBuilder) {
        builder.composable(
            type,
            enterTransition = enterTransition,
            exitTransition = exitTransition,
            sizeTransform = sizeTransform,
        ) { entry ->
            val arg: Arg = entry.toRoute(type)
            var loaded by remember { mutableStateOf(loaderRan || loaderFn == null) }

            if (loaded) {
                val rememberedData = remember { data }

                @Suppress("UNCHECKED_CAST")
                contentComposable?.invoke(rememberedData as Data)

                return@composable
            }

            LaunchedEffect(Unit) {
                loaderFn!!(arg)
                loaderRan = false
                loaded = true
            }

            pendingComposable?.invoke()
        }
    }

    fun enterTransition(fn: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition) {
        enterTransition = fn
    }

    fun exitTransition(fn: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition) {
        exitTransition = fn
    }

    fun sizeTransform(fn: AnimatedContentTransitionScope<NavBackStackEntry>.() -> SizeTransform) {
        sizeTransform = fn
    }

    inline fun content(crossinline fn: @Composable () -> Unit) {
        contentComposable = { _ -> fn() }
    }

    fun content(fn: @Composable (Data) -> Unit) {
        contentComposable = fn
    }

    fun pending(fn: @Composable () -> Unit) {
        pendingComposable = fn
    }

    inline fun loader(crossinline fn: suspend () -> Data) {
        loaderFn = loaderFn@ {
            if (loading.value) {
                return@loaderFn
            }

            loading.value = true
            @Suppress("UNCHECKED_CAST")
            data = fn()
            loading.value = false
        }
    }

    inline fun loader(crossinline fn: suspend (Arg) -> Data) {
        loaderFn = loaderFn@ {
            if (loading.value) {
                return@loaderFn
            }

            loading.value = true
            @Suppress("UNCHECKED_CAST")
            data = fn(it as Arg)
            loading.value = false
        }
    }
}