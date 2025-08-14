package io.github.asnaeb.navzion

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.reflect.KClass

@NodeMarker
abstract class NodeBuilder<NodeType : Any, Arg : NodeType, Data>(
    val router: Router,
    @PublishedApi
    internal val type: KClass<out Arg>,
    internal open val parentType: KClass<out Layout>?
) {
    @PublishedApi
    internal val key = type.qualifiedName ?: error("Cannot use anonymous objects or local classes")

    @PublishedApi
    internal var data: Data? = null

    @PublishedApi
    internal var loaderFn: (suspend (NodeType?) -> Unit)? = null

    internal var pendingComposable: (@Composable () -> Unit)? = null

    internal val loaded = MutableStateFlow(false)

    @PublishedApi
    internal val loading = MutableStateFlow(false)

    protected var defaultEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (parentType != null) {
            router.safeAccess(parentType!!).childrenEnterTransition(this)
        }
        else {
            fadeIn()
        }
    }

    protected var defaultExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (parentType != null) {
            router.safeAccess(parentType!!).childrenExitTransition(this)
        }
        else {
            fadeOut()
        }
    }

    protected var defaultSizeTransform: AnimatedContentTransitionScope<NavBackStackEntry>.() -> SizeTransform? = {
        if (parentType != null) {
            router.safeAccess(parentType!!).childrenSizeTransform(this)
        }
        else {
            null
        }
    }

    protected var enterTransition = defaultEnterTransition
    protected var exitTransition = defaultExitTransition
    protected var sizeTransform = defaultSizeTransform

    fun enterTransition(fn: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition) {
        enterTransition = fn
    }

    fun exitTransition(fn: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition) {
        exitTransition = fn
    }

    fun sizeTransform(fn: AnimatedContentTransitionScope<NavBackStackEntry>.() -> SizeTransform) {
        sizeTransform = fn
    }

    fun pending(fn: @Composable () -> Unit) {
        pendingComposable = fn
    }

    fun loader(fn: suspend () -> Data) {
        loaderFn = {
            data = fn()
            loaded.value = true
        }
    }

    protected open fun loader(fn: suspend (Arg?) -> Data) {
        @Suppress("UNCHECKED_CAST")
        loaderFn = {
            data = fn(it as Arg?)
            loaded.value = true
        }
    }

    internal abstract fun render(builder: NavGraphBuilder)
}