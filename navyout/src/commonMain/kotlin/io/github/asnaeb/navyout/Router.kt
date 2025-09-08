package io.github.asnaeb.navyout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.util.fastForEachReversed
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.reflect.KClass

@Stable
class Router(
    private val start: Route,
    internal val loadingDelayMs: Long = 50,
    init: @NodeMarker LayoutBuilder<Nothing, Unit>.() -> Unit
) {
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

    private suspend fun runLoaderIfNeeded(route: Route) {
        safeAccess(route::class).let {
            if (it.loaderFn == null) {
                return
            }

            CoroutineScope(Dispatchers.Default).launch {
                delay(loadingDelayMs)
                if (!it.loaded.value) {
                    it.loading.value = true
                }
            }

            if (it.pendingComposable == null) {
                it.loaderFn?.invoke(route)
            }
            else {
                CoroutineScope(Dispatchers.Default).launch {
                    it.loaderFn?.invoke(route)
                }
            }
        }
    }

    private suspend fun runLoaderIfNeeded(layoutBuilder: LayoutBuilder<*, *>) {
        if (layoutBuilder.loaderFn == null) {
            return
        }

        CoroutineScope(Dispatchers.Default).launch {
            delay(loadingDelayMs)

            if (!layoutBuilder.loaded.value) {
                layoutBuilder.loading.value = true
            }
        }

        if (layoutBuilder.pendingComposable == null) {
            layoutBuilder.loaderFn?.invoke(layoutBuilder.arg.value)
        }
        else {
            CoroutineScope(Dispatchers.Default).launch {
                layoutBuilder.loaderFn?.invoke(layoutBuilder.arg.value)
            }
        }
    }

    private fun navigateInternal(nearestCommonParent: LayoutBuilder<*, *>, requestedRoute: Route) {
        val requestedRouteBuilder = safeAccess(requestedRoute::class)
        val toParents = requestedRouteBuilder.parents
        val navController = nearestCommonParent.navController ?: error("NavHostController not registered")
        val relevantParents = toParents.take(toParents.indexOf(nearestCommonParent))

        val destination: String = if (relevantParents.isEmpty()) {
            requestedRouteBuilder.key
        }
        else if (relevantParents.size == 1) {
            relevantParents.first().destination = requestedRouteBuilder.key
            relevantParents.first().key
        }
        else {
            relevantParents.forEachIndexed { i, it ->
                if (i == 0) {
                    it.destination = requestedRouteBuilder.key
                }
                else {
                    it.destination = relevantParents[i - 1].key
                }
            }

            relevantParents.last().key
        }

        job?.cancel()

        job = CoroutineScope(Dispatchers.Default).launch {
            // TODO verify if this needs to run in reverse order
            relevantParents.fastForEachReversed {
                runLoaderIfNeeded(it)
            }

            runLoaderIfNeeded(requestedRoute)

            if (requestedRouteBuilder.loading.value || relevantParents.any { it.loading.value }) {
                delay(loadingDelayMs)
            }

            withContext(Dispatchers.Main) {
                val prevActiveRoute = safeAccess(activeRoute::class)

                launch {
                    navController.navigate(destination)
                }

                launch {
                    backStack.update {
                        if (it.size == 2) {
                            it.takeLast(1) + requestedRoute
                        }

                        it + requestedRoute
                    }
                }

                if (prevActiveRoute.data !is Unit) {
                    launch {
                        prevActiveRoute.data = null
                    }
                }


                layoutBuilders.values.forEach {
                    if (it !in safeAccess(requestedRoute::class).parents) {
                        launch {
                            it.arg.value = null
                            it.data = null
                        }
                    }
                }
            }
        }

        job?.invokeOnCompletion { e ->
            if (e is CancellationException) {
                relevantParents.forEach {
                    it.loading.value = false
                }

                requestedRouteBuilder.loading.value = false
            }
        }
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

    fun navigate(to: Route, layoutArg: Layout, vararg args: Layout) {
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

    fun navigate(to: Route) {
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
        val loadingStates = remember {
            layoutBuilders.values.map { it.loading } + routeBuilders.values.map { it.loading }
        }

        val initialValue = loadingStates.any { it.value }

        val combined = remember {
            combine (loadingStates) {
                states -> states.any { it }
            }
        }

        return combined.collectAsState(initialValue).value
    }

    @Composable
    fun getActiveRoute() = backStack.collectAsState().value.last()

    @Composable
    fun Render() {
        LaunchedEffect(Unit) {
            launch {
                runLoaderIfNeeded(rootLayoutBuilder)
            }

            launch {
                runLoaderIfNeeded(start)
            }
        }

        RouterProvider(this) {
            NavHost(rememberNavController(), rootLayoutBuilder.key) {
                rootLayoutBuilder.render(this@NavHost)
            }
        }
    }
}