package io.github.asnaeb.navzion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.reflect.KClass

@Stable
class Router(start: Route, init: @NodeMarker LayoutBuilder<Nothing, Unit>.() -> Unit) {
    private val rootLayoutBuilder: LayoutBuilder<Nothing, Unit> = LayoutBuilder(this, Nothing::class, null)

    internal val backStack = MutableStateFlow(listOf(start))

    internal val activeRoute get() = backStack.value.last()

    private val activeLayoutType get() = safeAccess(activeRoute::class).parentType

    @PublishedApi
    internal val routeBuilders: MutableMap<KClass<out Route>, RouteBuilder<out Route, *>> = mutableMapOf()

    @PublishedApi
    internal val layoutBuilders: MutableMap<KClass<out Layout>, LayoutBuilder<out Layout, *>> = mutableMapOf()

    private var job: Job? = null

    init {
        init(rootLayoutBuilder)
        rootLayoutBuilder.destination = safeAccess(start::class).key
        registerLayout(rootLayoutBuilder)
    }

    private fun updateActiveRoute(route: Route) {
        backStack.update {
            if (it.size >= 2) {
                it.takeLast(1) + route
            }

            it + route
        }
    }

    private suspend fun runLoaderIfNeeded(route: Route) {
        safeAccess(route::class).let {
            if (it.loaderFn != null && it.pendingComposable == null) {
                it.loaderFn?.invoke(route)
            }
        }
    }

    private suspend fun runLoaderIfNeeded(layoutBuilder: LayoutBuilder<*, *>) {
        if (layoutBuilder.loaderFn != null && layoutBuilder.pendingComposable == null) {
            layoutBuilder.loaderFn?.invoke(layoutBuilder.arg.value)
        }
    }

    private suspend fun runLoaders(layouts: List<LayoutBuilder<*, *>>, requestedRoute: Route) {
        coroutineScope {
            layouts.forEach {
                launch {
                    runLoaderIfNeeded(it)
                }
            }

            launch {
                runLoaderIfNeeded(requestedRoute)
            }
        }
    }

    private fun changeDestination(
        navController: NavController,
        actualDestination: String,
        requestedRoute: Route
    ) {
        val prevActiveRoute = safeAccess(activeRoute::class)

        navController.navigate(actualDestination)

        updateActiveRoute(requestedRoute)

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

    private fun performNavigation(
        navController: NavController,
        layouts: List<LayoutBuilder<*, *>>,
        actualDestination: String,
        requestedRoute: Route
    ) {
        job?.cancel()
        job = CoroutineScope(Dispatchers.Default).launch {
            runLoaders(layouts, requestedRoute)
            withContext(Dispatchers.Main) {
                changeDestination(navController, actualDestination, requestedRoute)
            }
        }
    }

    private fun navigateInternal(nearestCommonParent: LayoutBuilder<*, *>, requestedRoute: Route) {
        val requestedRouteBuilder = safeAccess(requestedRoute::class)
        val toParents = requestedRouteBuilder.parents
        val navController = nearestCommonParent.navController ?: error("NavHostController not registered")
        val relevantParents = toParents.take(toParents.indexOf(nearestCommonParent))

        if (relevantParents.isEmpty()) {
            performNavigation(navController, relevantParents, requestedRouteBuilder.key, requestedRoute)
            return
        }

        if (relevantParents.size == 1) {
            relevantParents.first().destination = requestedRouteBuilder.key
            performNavigation(navController, relevantParents, relevantParents.first().key, requestedRoute)
            return
        }

        relevantParents.forEachIndexed { i, it ->
            if (i == 0) {
                it.destination = requestedRouteBuilder.key
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

        if (activeRoute == to && layoutArgSet.all { safeAccess(it::class).arg.value == it }) {
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

        val nearestCommonParent = fromParents
            .lastOrNull {
                it in toParents && it.type in layoutDataTypes && it.parentType != null
            }
            ?.let {
                safeAccess(it.parentType!!)
            }
            ?: fromParents.first { it in toParents }

        navigateInternal(nearestCommonParent, to)
    }

    fun <Arg : Route> navigate(to: Arg) {
        if (activeRoute == to) {
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
    fun getActiveRoute() = backStack.collectAsState().value.last()

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