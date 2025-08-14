package io.github.asnaeb.navzion

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.jvm.JvmName
import kotlin.reflect.KClass

@NodeMarker
class LayoutBuilder<Arg : Layout, Data>(
    router: Router,
    type: KClass<Arg>,
    parentType: KClass<out Layout>?,
    initialArg: Arg? = null,
) : NodeBuilder<Layout, Arg, Data>(router, type, parentType) {
    @PublishedApi
    internal val arg = MutableStateFlow(initialArg)

    @PublishedApi
    internal var navController: NavHostController? = null

    @PublishedApi
    internal var destination: String = ""

    @PublishedApi
    internal val childRoutes: MutableSet<RouteBuilder<out Route, *>> = mutableSetOf()

    @PublishedApi
    internal val childLayouts: MutableSet<LayoutBuilder<out Layout, *>> = mutableSetOf()

    internal val withParents by lazy {
        var layout = router.safeAccess(type)

        val parents = mutableSetOf(layout)

        while (layout.parentType != null) {
            layout = router.safeAccess(layout.parentType)
            parents.add(layout)
        }

        parents
    }

    @PublishedApi
    internal var wrapperComposable: @Composable (Data, @Composable () -> Unit) -> Unit = { _, children -> children() }

    internal fun setArg(value: Layout) {
        require(type.isInstance(value))
        @Suppress("UNCHECKED_CAST")
        arg.value = value as Arg
    }

    internal var childrenEnterTransition = defaultEnterTransition
    internal var childrenExitTransition = defaultExitTransition
    internal var childrenSizeTransform = defaultSizeTransform

    override fun render(builder: NavGraphBuilder) {
        builder.composable(
            key,
            enterTransition = enterTransition,
            exitTransition = exitTransition,
            sizeTransform = sizeTransform
        ) {
            navController = rememberNavController()
            val isLoaded by loaded.collectAsState()

            DisposableEffect(Unit) {
                onDispose {
                    loaded.value = false
                    loading.value = false
                }
            }

            if (loaderFn == null || isLoaded) {
                val rememberedData = remember { data }

                CurrentLayoutProvider(type) {
                    @Suppress("UNCHECKED_CAST")
                    wrapperComposable(rememberedData as Data) {
                        NavHost(
                            navController!!,
                            destination,
                            enterTransition = childrenEnterTransition,
                            exitTransition = childrenExitTransition,
                            sizeTransform = childrenSizeTransform
                        ) {
                            childRoutes.forEach { route -> route.render(this) }
                            childLayouts.forEach { layoutRoute -> layoutRoute.render(this) }
                        }
                    }
                }
            }
            else {
                CurrentLayoutProvider(type) {
                    pendingComposable?.invoke()
                }
            }
        }
    }

    fun childrenEnterTransition(fn: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition) {
        childrenEnterTransition = fn
    }

    fun childrenExitTransition(fn: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition) {
        childrenExitTransition = fn
    }

    fun childrenSizeTransform(fn: AnimatedContentTransitionScope<NavBackStackEntry>.() -> SizeTransform) {
        childrenSizeTransform = fn
    }

    fun wrapper(fn: @Composable (@Composable () -> Unit) -> Unit) {
        wrapperComposable = { _, children ->
            fn(children)

            LaunchedEffect(Unit) {
                loading.value = false
            }
        }
    }

    fun wrapper(fn: @Composable (Data, @Composable () -> Unit) -> Unit) {
        wrapperComposable = { data, children ->
            fn(data, children)

            LaunchedEffect(Unit) {
                loading.value = false
            }
        }
    }

    @JvmName("routeWithData")
    inline fun <reified RArg : Route, RData> route(init: @NodeMarker RouteBuilder<RArg, RData>.() -> Unit) {
        val routeBuilder = RouteBuilder<RArg, RData>(router, RArg::class, type)

        init(routeBuilder)

        childRoutes.add(routeBuilder)
        router.registerRoute(routeBuilder)
    }

    @JvmName("routeWithoutData")
    inline fun <reified RArg : Route> route(init: @NodeMarker RouteBuilder<RArg, Unit>.() -> Unit) {
        val routeBuilder = RouteBuilder<RArg, Unit>(router, RArg::class, type)

        init(routeBuilder)

        routeBuilder.data = Unit

        childRoutes.add(routeBuilder)
        router.registerRoute(routeBuilder)
    }

    @JvmName("layoutWithData")
    inline fun <reified LArg : Layout, LData> layout(
        initialData: LArg? = null,
        init: @NodeMarker LayoutBuilder<LArg, LData>.() -> Unit
    ) {
        val layoutBuilder = LayoutBuilder<LArg, LData>(router, LArg::class, type, initialData)

        init(layoutBuilder)

        childLayouts.add(layoutBuilder)
        router.registerLayout(layoutBuilder)
    }

    @JvmName("layoutWithoutData")
    inline fun <reified LArg : Layout> layout(
        initialData: LArg? = null,
        init: @NodeMarker LayoutBuilder<LArg, Unit>.() -> Unit
    ) {
        val layoutBuilder = LayoutBuilder<LArg, Unit>(router, LArg::class, type, initialData)

        init(layoutBuilder)

        layoutBuilder.data = Unit

        childLayouts.add(layoutBuilder)
        router.registerLayout(layoutBuilder)
    }

    public override fun loader(fn: suspend (Arg?) -> Data) = super.loader(fn)
}