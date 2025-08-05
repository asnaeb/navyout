package io.github.asnaeb.navzion

import androidx.compose.runtime.Composable
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
    internal val routeBuilders: MutableSet<RouteBuilder<out Route>> = mutableSetOf()

    @PublishedApi
    internal val layoutBuilders: MutableSet<LayoutBuilder<out Layout>> = mutableSetOf()

    private var composable: @Composable (@Composable () -> Unit) -> Unit = { it() }

    internal fun setData(data: Any) {
        if (type.isInstance(data)) {
            @Suppress("UNCHECKED_CAST")
            this.data.value = data as T
        }
    }

    internal fun render(builder: NavGraphBuilder) {
        builder.composable(key) {
            navController = rememberNavController()

            CurrentLayoutProvider(type) {
                composable {
                    NavHost(navController!!, destination ?: error("start destination not set")) {
                        routeBuilders.forEach { route -> route.render(this) }
                        layoutBuilders.forEach { layoutRoute -> layoutRoute.render(this) }
                    }
                }
            }
        }
    }

    fun wrapper(content: @Composable (@Composable () -> Unit) -> Unit) {
        this.composable = content
    }

    inline fun <reified R : Route> route(init: @NodeMarker RouteBuilder<R>.() -> Unit) {
        val routeBuilder = RouteBuilder(R::class, type, router)

        init(routeBuilder)

        routeBuilders.add(routeBuilder)
        router.registerRoute(routeBuilder)
    }

    inline fun <reified L : Layout> layout(initialData: L? = null, init: @NodeMarker LayoutBuilder<L>.() -> Unit) {
        val layoutBuilder = LayoutBuilder(router, L::class, type, initialData)

        init(layoutBuilder)

        layoutBuilders.add(layoutBuilder)
        router.registerLayout(layoutBuilder)
    }
}