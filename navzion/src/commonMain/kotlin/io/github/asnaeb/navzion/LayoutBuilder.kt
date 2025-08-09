package io.github.asnaeb.navzion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    val router: Router,
    @PublishedApi
    internal val type: KClass<Arg>,
    internal val parentType: KClass<out Layout>?,
    initialArg: Arg? = null,
) {
    @PublishedApi
    internal var data: Data? = null

    @PublishedApi
    internal val arg = MutableStateFlow(initialArg)

    @PublishedApi
    internal val key get() = type.qualifiedName ?: hashCode().toString()

    @PublishedApi
    internal var navController: NavHostController? = null

    @PublishedApi
    internal var destination: Any? = null

    @PublishedApi
    internal val childRoutes: MutableSet<RouteBuilder<out Route, *>> = mutableSetOf()

    @PublishedApi
    internal val childLayouts: MutableSet<LayoutBuilder<out Layout, *>> = mutableSetOf()

    internal val withParents by lazy { router.getWithParents(type) }

    @PublishedApi
    internal var wrapperComposable: @Composable (Data, @Composable () -> Unit) -> Unit = { _, children -> children() }

    internal var pendingComposable: (@Composable () -> Unit)? = null

    internal var loaderFn: (suspend (Layout?) -> Unit)? = null

    internal var loaderRan = false

    @PublishedApi
    internal val loading = MutableStateFlow(false)

    internal fun setArg(value: Layout) {
        require(type.isInstance(value))
        @Suppress("UNCHECKED_CAST")
        arg.value = value as Arg
    }

    internal fun render(builder: NavGraphBuilder) {
        builder.composable(key) {
            navController = rememberNavController()
            var loaded by remember { mutableStateOf(loaderRan || loaderFn == null) }

            if (loaded) {
                val rememberedData = remember { data }

                CurrentLayoutProvider(type) {
                    @Suppress("UNCHECKED_CAST")
                    wrapperComposable(rememberedData as Data) {
                        NavHost(navController!!, destination ?: error("start destination not set")) {
                            childRoutes.forEach { route -> route.render(this) }
                            childLayouts.forEach { layoutRoute -> layoutRoute.render(this) }
                        }
                    }
                }

                return@composable
            }

            LaunchedEffect(Unit) {
                loaderFn?.invoke(arg.value)
                loaderRan = false
                loaded = true
            }

            CurrentLayoutProvider(type) {
                pendingComposable?.invoke()
            }
        }
    }

    inline fun wrapper(crossinline fn: @Composable (@Composable () -> Unit) -> Unit) {
        wrapperComposable = { _, children -> fn(children) }
    }

    fun wrapper(fn: @Composable (Data, @Composable () -> Unit) -> Unit) {
        wrapperComposable = fn
    }

    @JvmName("routeWithData")
    inline fun <reified RArg : Route, RData> route(init: @NodeMarker RouteBuilder<RArg, RData>.() -> Unit) {
        val routeBuilder = RouteBuilder<RArg, RData>(RArg::class, type, router)

        init(routeBuilder)

        childRoutes.add(routeBuilder)
        router.registerRoute(routeBuilder)
    }

    @JvmName("routeWithoutData")
    inline fun <reified RArg : Route> route(init: @NodeMarker RouteBuilder<RArg, Unit>.() -> Unit) {
        val routeBuilder = RouteBuilder<RArg, Unit>(RArg::class, type, router)

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

    fun loader(fn: suspend () -> Data) {
        loaderFn = loaderFn@ {
            if (loading.value) {
                return@loaderFn
            }

            loading.value = true
            data = fn()
            loading.value = false
        }
    }

    fun loader(fn: suspend (Arg?) -> Data) {
        loaderFn = loaderFn@ {
            if (loading.value) {
                return@loaderFn
            }

            loading.value = true
            @Suppress("UNCHECKED_CAST")
            data = fn(it as Arg?)
            loading.value = false
        }
    }

    fun pending(fn: @Composable () -> Unit) {
        pendingComposable = fn
    }
}