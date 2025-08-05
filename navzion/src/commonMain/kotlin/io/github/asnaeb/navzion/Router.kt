package io.github.asnaeb.navzion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.reflect.KClass

class Router(start: Route, init: @NodeMarker LayoutBuilder<Nothing>.() -> Unit) {
    private val activeLayoutType get() = safeAccessRoute(activeRoute.value::class).parentType

    private val rootLayoutBuilder: LayoutBuilder<Nothing> = LayoutBuilder(this, Nothing::class, null)

    private val routeBuilders: MutableMap<KClass<out Route>, RouteBuilder<out Route>> = mutableMapOf()

    @PublishedApi
    internal var activeRoute = MutableStateFlow(start)

    @PublishedApi
    internal val layoutBuilders: MutableMap<KClass<out Layout>, LayoutBuilder<out Layout>> = mutableMapOf()

    init {
        rootLayoutBuilder.destination = start
        init(rootLayoutBuilder)
        registerLayout(rootLayoutBuilder)
    }

    private fun safeAccessRoute(type: KClass<out Route>): RouteBuilder<out Route> {
        require(type in routeBuilders) {
            "Tried to access a route (${type.simpleName}) that wasn't registered"
        }

        return routeBuilders[type]!!
    }

    private fun safeAccessLayout(type: KClass<out Layout>): LayoutBuilder<out Layout> {
        require(type in layoutBuilders) {
            "Tried to access a LayoutRoute (${type}) that wasn't registered"
        }

        return layoutBuilders[type]!!
    }

    private fun navigateAndUpdateState(
        navController: NavHostController,
        actualDestination: String,
        requestedDestination: Route
    ) {
        navController.navigate(actualDestination)
        activeRoute.value = requestedDestination
    }

    private fun navigateAndUpdateState(
        navController: NavHostController,
        actualDestination: Route,
        requestedDestination: Route
    ) {
        navController.navigate(actualDestination)
        activeRoute.value = requestedDestination
    }

    private fun setDestinationsAndNavigate(navController: NavHostController, relevantParents: List<LayoutBuilder<*>>, to: Route) {
        if (relevantParents.isEmpty()) {
            navigateAndUpdateState(navController, to, to)
            return
        }

        if (relevantParents.size == 1) {
            relevantParents.first().destination = to
            navigateAndUpdateState(navController, relevantParents.first().key, to)
            return
        }

        relevantParents.forEachIndexed { i, it ->
            if (i == 0) {
                it.destination = to
            }
            else {
                it.destination = relevantParents[i - 1].key
            }
        }

        navigateAndUpdateState(navController, relevantParents.last().key, to)
    }

    private fun getAllParents(routeType: KClass<out Route>): MutableSet<LayoutBuilder<out Layout>> {
        var parent = safeAccessLayout(safeAccessRoute(routeType).parentType)

        val parents = mutableSetOf(parent)

        while (parent.parentType != null) {
            parent = safeAccessLayout(parent.parentType)
            parents.add(parent)
        }

        return parents
    }

    @PublishedApi
    internal fun getWithAllParents(layoutType: KClass<out Layout>): MutableSet<LayoutBuilder<out Layout>> {
        var layout = safeAccessLayout(layoutType)

        val parents = mutableSetOf(layout)

        while (layout.parentType != null) {
            layout = safeAccessLayout(layout.parentType)
            parents.add(layout)
        }

        return parents
    }

    @PublishedApi
    internal fun registerRoute(routeBuilder: RouteBuilder<out Route>) {
        require(routeBuilder.type !in routeBuilders) {
            "A route with type ${routeBuilder.type} already exists. Route types mut be unique."
        }

        routeBuilders[routeBuilder.type] = routeBuilder
    }

    @PublishedApi
    internal fun registerLayout(layoutBuilder: LayoutBuilder<out Layout>) {
        require(layoutBuilder.type !in layoutBuilders) {
            "A layout with type ${layoutBuilder.type} already exists. Layout types mut be unique."
        }

        layoutBuilders[layoutBuilder.type] = layoutBuilder
    }

    fun <T : Route> navigate(to: T, layoutData: Layout, vararg args: Layout) {
        val fromParents = getWithAllParents(activeLayoutType)
        val toParents = getAllParents(to::class)
        val layoutDataSet = args.toMutableSet().apply { add(layoutData) }

        val layoutDataTypes: MutableSet<KClass<out Layout>> = mutableSetOf()

        for (data in layoutDataSet) {
            layoutBuilders[data::class]?.apply {
                layoutDataTypes.add(data::class)
            }
        }

        val nearestCommonParent = fromParents.first { it in toParents }
            .let {
                if (it.type in layoutDataTypes && it.parentType != null) {
                    try {
                        safeAccessLayout(it.parentType)
                    }
                    catch (_: Throwable) {
                        it
                    }
                }
                else {
                    it
                }
            }


        val navController = nearestCommonParent.navController ?: error("NavHostController not registered")
        val relevantParents = toParents.take(toParents.indexOf(nearestCommonParent))

        setDestinationsAndNavigate(navController, relevantParents, to)

        for (data in layoutDataSet) {
            layoutBuilders[data::class]?.apply {
                setData(data)
            }
        }
    }

    fun <T : Route> navigate(to: T) {
        val fromParents = getWithAllParents(activeLayoutType)
        val toParents = getAllParents(to::class)

        val nearestCommonParent = fromParents.first { it in toParents }
        val navController = nearestCommonParent.navController ?: error("NavHostController not registered")
        val relevantParents = toParents.take(toParents.indexOf(nearestCommonParent))

        setDestinationsAndNavigate(navController, relevantParents, to)
    }

    @Composable
    inline fun <reified T : Layout> getLayoutData(): T? {
        val currentLayout = getCurrentLayout()

        if (getWithAllParents(currentLayout).any { it.type == T::class }) {
            return layoutBuilders[T::class]?.data?.value as T?
        }

        return layoutBuilders[T::class]?.data?.collectAsState()?.value as T?
    }

    @Composable
    fun getActiveRouteAsState() = activeRoute.collectAsState().value

    @Composable
    fun Render() {
        // TODO Refactor to not use this extra NavHost
        NavHost(rememberNavController(), rootLayoutBuilder.key) {
            rootLayoutBuilder.render(this@NavHost)
        }
    }
}