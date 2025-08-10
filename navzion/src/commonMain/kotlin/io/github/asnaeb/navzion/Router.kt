package io.github.asnaeb.navzion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

class Router(start: Route, init: @NodeMarker LayoutBuilder<Nothing, Unit>.() -> Unit) {
    private val activeLayoutType get() = safeAccess(activeRoute.value::class).parentType

    private val rootLayoutBuilder: LayoutBuilder<Nothing, Unit> = LayoutBuilder(this, Nothing::class, null)

    @PublishedApi
    internal var activeRoute = MutableStateFlow(start)

    @PublishedApi
    internal val routeBuilders: MutableMap<KClass<out Route>, RouteBuilder<out Route, *>> = mutableMapOf()

    @PublishedApi
    internal val layoutBuilders: MutableMap<KClass<out Layout>, LayoutBuilder<out Layout, *>> = mutableMapOf()

    init {
        rootLayoutBuilder.destination = start
        init(rootLayoutBuilder)
        registerLayout(rootLayoutBuilder)
    }

    private suspend fun runLoaderIfNeeded(route: Route) {
        safeAccess(route::class).let {
            if (it.loaderFn != null && it.pendingComposable == null && !it.loading.value) {
                it.loaderFn?.invoke(route)
                it.loaderRan = true
            }
        }
    }

    private suspend fun runLoaderIfNeeded(layoutBuilder: LayoutBuilder<*, *>) {
        if (layoutBuilder.loaderFn != null && layoutBuilder.pendingComposable == null && !layoutBuilder.loading.value) {
            layoutBuilder.loaderFn?.invoke(layoutBuilder.arg.value)
            layoutBuilder.loaderRan = true
        }
    }

    private fun performNavigation(
        navController: NavController,
        layouts: List<LayoutBuilder<*, *>>,
        actualDestination: Any,
        requestedRoute: Route
    ) {
        CoroutineScope(Dispatchers.Main).launch {
            layouts.forEach {
                launch {
                    runLoaderIfNeeded(it)
                }
            }

            launch {
                runLoaderIfNeeded(requestedRoute)
            }
        }
        .invokeOnCompletion {
            when (actualDestination) {
                is String -> navController.navigate(actualDestination)
                is Route -> navController.navigate(actualDestination)
                else -> error("Unexpected destination type ${actualDestination::class}")
            }

            val prevActiveRoute = safeAccess(activeRoute.value::class)

            activeRoute.value = requestedRoute

            if (prevActiveRoute.data !is Unit) {
                prevActiveRoute.data = null
            }

            layoutBuilders.values.forEach {
                if (it !in safeAccess(requestedRoute::class).parents) {
                    it.arg.value = null
                    it.data = null
                }
            }
        }
    }

    private fun navigateInternal(nearestCommonParent: LayoutBuilder<*, *>, requestedRoute: Route) {
        val toParents = safeAccess(requestedRoute::class).parents
        val navController = nearestCommonParent.navController ?: error("NavHostController not registered")
        val relevantParents = toParents.take(toParents.indexOf(nearestCommonParent))

        if (relevantParents.isEmpty()) {
            performNavigation(navController, relevantParents, requestedRoute, requestedRoute)
            return
        }

        if (relevantParents.size == 1) {
            relevantParents.first().destination = requestedRoute
            performNavigation(navController, relevantParents, relevantParents.first().key, requestedRoute)
            return
        }

        relevantParents.forEachIndexed { i, it ->
            if (i == 0) {
                it.destination = requestedRoute
            }
            else {
                it.destination = relevantParents[i - 1].key
            }
        }

        performNavigation(navController, relevantParents, relevantParents.last().key, requestedRoute)
    }

    @PublishedApi
    internal fun safeAccess(type: KClass<out Route>): RouteBuilder<out Route, *> {
        require(type in routeBuilders) {
            "Tried to access a route (${type.simpleName}) that wasn't registered"
        }

        return routeBuilders[type]!!
    }

    @PublishedApi
    internal fun safeAccess(type: KClass<out Layout>): LayoutBuilder<out Layout, *> {
        require(type in layoutBuilders) {
            "Tried to access a LayoutRoute (${type}) that wasn't registered"
        }

        return layoutBuilders[type]!!
    }

    internal fun getParents(routeType: KClass<out Route>): MutableSet<LayoutBuilder<out Layout, *>> {
        var parent = safeAccess(safeAccess(routeType).parentType)

        val parents = mutableSetOf(parent)

        while (parent.parentType != null) {
            parent = safeAccess(parent.parentType)
            parents.add(parent)
        }

        return parents
    }

    @PublishedApi
    internal fun getWithParents(layoutType: KClass<out Layout>): MutableSet<LayoutBuilder<out Layout, *>> {
        var layout = safeAccess(layoutType)

        val parents = mutableSetOf(layout)

        while (layout.parentType != null) {
            layout = safeAccess(layout.parentType)
            parents.add(layout)
        }

        return parents
    }

    @PublishedApi
    internal fun registerRoute(routeBuilder: RouteBuilder<out Route, *>) {
        require(routeBuilder.type !in routeBuilders) {
            "A route with type ${routeBuilder.type} already exists. Route types mut be unique."
        }

        routeBuilders[routeBuilder.type] = routeBuilder
    }

    @PublishedApi
    internal fun registerLayout(layoutBuilder: LayoutBuilder<out Layout, *>) {
        require(layoutBuilder.type !in layoutBuilders) {
            "A layout with type ${layoutBuilder.type} already exists. Layout types mut be unique."
        }

        layoutBuilders[layoutBuilder.type] = layoutBuilder
    }

    fun <Arg : Route> navigate(to: Arg, layoutArg: Layout, vararg args: Layout) {
        val layoutArgSet = args.toMutableSet().apply { add(layoutArg) }

        if (activeRoute.value == to && layoutArgSet.all { safeAccess(it::class).arg.value == it }) {
            return
        }

        val toParents = safeAccess(to::class).parents
        val fromParents = safeAccess(activeLayoutType).withParents

        val layoutDataTypes: MutableSet<KClass<out Layout>> = mutableSetOf()

        for (arg in layoutArgSet) {
            safeAccess(arg::class).apply {
                setArg(arg)
                layoutDataTypes.add(arg::class)
            }
        }

        val nearestCommonParent = fromParents.first { it in toParents }
            .let {
                if (it.type in layoutDataTypes && it.parentType != null) {
                    try {
                        safeAccess(it.parentType)
                    }
                    catch (_: Throwable) {
                        it
                    }
                }
                else {
                    it
                }
            }

        navigateInternal(nearestCommonParent, to)
    }

    fun <Arg : Route> navigate(to: Arg) {
        if (activeRoute.value == to) {
            return
        }

        val fromParents = safeAccess(activeLayoutType).withParents
        val toParents = safeAccess(to::class).parents
        val nearestCommonParent = fromParents.first { it in toParents }

        navigateInternal(nearestCommonParent, to)
    }

    @Composable
    fun isLoading(): Boolean {
        return layoutBuilders.values.any { it.loading.collectAsState().value  }
                || routeBuilders.values.any { it.loading.collectAsState().value  }
    }

    @Composable
    fun getActiveRoute() = activeRoute.collectAsState().value

    @Composable
    fun Render() {
        RouterProvider(this) {
            // TODO Refactor to not use this extra NavHost
            NavHost(rememberNavController(), rootLayoutBuilder.key) {
                rootLayoutBuilder.render(this@NavHost)
            }
        }
    }
}