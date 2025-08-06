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
import kotlin.reflect.KClass

@NodeMarker
class LayoutBuilder<T : Layout>(
    val router: Router,
    @PublishedApi
    internal val type: KClass<T>,
    internal val parentType: KClass<out Layout>?,
    initialData: T? = null,
) {
    @PublishedApi
    internal val data = MutableStateFlow(initialData)

    @PublishedApi
    internal val key get() = type.qualifiedName ?: hashCode().toString()

    @PublishedApi
    internal var navController: NavHostController? = null

    @PublishedApi
    internal var destination: Any? = null

    @PublishedApi
    internal val childRoutes: MutableSet<RouteBuilder<out Route>> = mutableSetOf()

    @PublishedApi
    internal val childLayouts: MutableSet<LayoutBuilder<out Layout>> = mutableSetOf()

    private var WrapperComposable: @Composable (@Composable () -> Unit) -> Unit = { it() }

    internal var PendingComposable: (@Composable () -> Unit)? = null

    internal var loaderFn: (suspend (Any?) -> Any)? = null

    internal var loaderRan = false

    @PublishedApi
    internal val loading = MutableStateFlow(false)

    internal fun setData(data: Any) {
        require(type.isInstance(data))
        @Suppress("UNCHECKED_CAST")
        this.data.value = data as T
    }

    internal fun render(builder: NavGraphBuilder) {
        builder.composable(key) {
            navController = rememberNavController()

            var loaded by remember { mutableStateOf(loaderRan || loaderFn == null) }

            if (loaded) {
                CurrentLayoutProvider(type) {
                    WrapperComposable {
                        NavHost(navController!!, destination ?: error("start destination not set")) {
                            childRoutes.forEach { route -> route.render(this) }
                            childLayouts.forEach { layoutRoute -> layoutRoute.render(this) }
                        }
                    }
                }

                return@composable
            }

            LaunchedEffect(Unit) {
                loading.value = true
                loaderFn?.invoke(data.value)
                loading.value = false
                loaderRan = false
                loaded = true
            }

            CurrentLayoutProvider(type) {
                PendingComposable?.invoke()
            }
        }
    }

    fun wrapper(fn: @Composable (@Composable () -> Unit) -> Unit) {
        WrapperComposable = fn
    }

    inline fun <reified R : Route> route(init: @NodeMarker RouteBuilder<R>.() -> Unit) {
        val routeBuilder = RouteBuilder(R::class, type, router)

        init(routeBuilder)

        childRoutes.add(routeBuilder)
        router.registerRoute(routeBuilder)
    }

    inline fun <reified L : Layout> layout(initialData: L? = null, init: @NodeMarker LayoutBuilder<L>.() -> Unit) {
        val layoutBuilder = LayoutBuilder(router, L::class, type, initialData)

        init(layoutBuilder)

        childLayouts.add(layoutBuilder)
        router.registerLayout(layoutBuilder)
    }

    fun loader(fn: suspend (T?) -> Unit) {
        @Suppress("UNCHECKED_CAST")
        loaderFn = fn as suspend (Any?) -> Any
    }

    fun pending(fn: @Composable () -> Unit) {
        PendingComposable = fn
    }
}