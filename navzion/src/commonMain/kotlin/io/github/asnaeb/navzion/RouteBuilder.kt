package io.github.asnaeb.navzion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.reflect.KClass

@NodeMarker
class RouteBuilder<T : Route>(
    internal val type: KClass<T>,
    internal val parentType: KClass<out Layout>,
    val router: Router
) {
    private var ContentComposable: @Composable (T) -> Unit = {}

    internal var PendingComposable: (@Composable (T) -> Unit)? = null

    internal var loaderFn: (suspend (Any) -> Any)? = null

    internal var loaderRan = false

    @PublishedApi
    internal val loading = MutableStateFlow(false)

    internal fun render(builder: NavGraphBuilder) {
        builder.composable(type) { entry ->
            val data: T = entry.toRoute(type)
            var loaded by remember { mutableStateOf(loaderRan || loaderFn == null) }

            if (loaded) {
                ContentComposable(data)
                return@composable
            }

            LaunchedEffect(Unit) {
                loading.value = true
                loaderFn!!(data)
                loading.value = false
                loaderRan = false
                loaded = true
            }

            PendingComposable?.invoke(data)
        }
    }

    fun content(fn: @Composable (T) -> Unit) {
        ContentComposable = fn
    }

    fun pending(fn: @Composable (T) -> Unit) {
        PendingComposable = fn
    }

    fun  loader(fn: suspend (T) -> Any) {
        @Suppress("UNCHECKED_CAST")
        loaderFn = fn as suspend (Any) -> Any
    }
}