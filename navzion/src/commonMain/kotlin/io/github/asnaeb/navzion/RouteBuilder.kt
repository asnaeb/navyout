package io.github.asnaeb.navzion

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlin.reflect.KClass

@NodeMarker
class RouteBuilder<T : Route>(
    internal val type: KClass<T>,
    internal val parentType: KClass<out Layout>,
    val router: Router
) {
    private var composable: @Composable (T) -> Unit = {}

    internal fun render(builder: NavGraphBuilder) {
        builder.composable(type) {
            composable(it.toRoute(type))
        }
    }

    fun content(composable: @Composable (T) -> Unit) {
        this.composable = composable
    }
}