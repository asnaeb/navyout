package io.github.asnaeb.navzion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlin.jvm.JvmName
import kotlin.reflect.KClass

@NodeMarker
class RouteBuilder<Arg : Route, Data>(
    router: Router,
    type: KClass<Arg>,
    override val parentType: KClass<out Layout>,
) : NodeBuilder<Route, Arg, Data>(router, type, parentType) {
    @PublishedApi
    internal var contentComposable: (@Composable (Data) -> Unit)? = null

    internal val parents by lazy {
        var parent = router.safeAccess(router.safeAccess(type).parentType)

        val parents = mutableSetOf(parent)

        while (parent.parentType != null) {
            parent = router.safeAccess(parent.parentType)
            parents.add(parent)
        }

        parents
    }

    override fun render(builder: NavGraphBuilder) {
        builder.composable(
            key,
            enterTransition = enterTransition,
            exitTransition = exitTransition,
            sizeTransform = sizeTransform,
        ) {
            val isLoaded by loaded.collectAsState()

            DisposableEffect(Unit) {
                onDispose {
                    loaded.value = false
                    loading.value = false
                }
            }

            if (loaderFn == null || isLoaded) {
                val rememberedData = remember { data }

                @Suppress("UNCHECKED_CAST")
                contentComposable?.invoke(rememberedData as Data)
            }
            else {
                pendingComposable?.invoke()
            }
        }
    }

    fun content(fn: @Composable () -> Unit) {
        contentComposable = { _ ->
            fn()

            LaunchedEffect(Unit) {
                loading.value = false
            }
        }
    }

    fun content(fn: @Composable (Data) -> Unit) {
        contentComposable = { data ->
            fn(data)

            LaunchedEffect(Unit) {
                loading.value = false
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    @JvmName("routeLoader")
    fun loader(fn: suspend (Arg) -> Data) = super.loader(fn as suspend (Arg?) -> Data)
}