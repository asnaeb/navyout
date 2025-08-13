import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.asnaeb.navzion.Layout
import io.github.asnaeb.navzion.Route
import io.github.asnaeb.navzion.Router
import io.github.asnaeb.navzion.extensions.get
import io.github.asnaeb.navzion.extensions.getOrNull
import kotlinx.coroutines.delay
import kotlin.random.Random

data class User(
    val id: Int,
    val username: String,
    val email: String,
    val firstname: String,
    val lastname: String,
    val addressStreet: String,
    val addressCity: String
)

data object Main : Route

data class UserLayout(val userId: Int) : Layout

data object AnotherLayout : Layout

data object UserHome : Route

data object UserFirstName : Route

data object UserLastName : Route

data object UserEmail : Route

data class UserAddress(val type: String) : Layout

data object AddressStreet : Route

data object AddressCity : Route

data class AnotherRoute(val value: String) : Route

val users = arrayOf(
    User(1, "asnaeb", "asnaeb.dev@gmail.com", "Roberto", "De Lucia", "Contrada feo", "Montemarano"),
    User(2, "baboon", "baboon@gmail.com", "Baa", "Boon", "Giungla", "Del caz"),
    User(3, "neikol", "nik@email.it", "Nikola", "Tesla", "via kokok", "Polonia")
)

val router = Router(start = Main) {
    wrapper { content ->
        val id = UserLayout::class.getOrNull()?.userId
        val addTyp = UserAddress::class.getOrNull()?.type
        val activeRoute = router.getActiveRoute()
        val isLoading = router.isLoading()

        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText("Is Loading: $isLoading")
            BasicText("Active route is: $activeRoute")
            BasicText("User id is: $id")
            BasicText("Address type is: $addTyp")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (id in 1..3) {
                        Box(Modifier.clickable { router.navigate(AddressStreet, UserAddress("CUST"), UserLayout(id)) }) {
                            BasicText("Go to user $id >")
                        }
                    }
                    Box(Modifier.clickable { router.navigate(AnotherRoute("hello")) }) {
                        BasicText("Go to another route >")
                    }
                    Box(Modifier.clickable { router.navigate(Main) }) {
                        BasicText("< Back to root")
                    }
                }
                content()
            }
        }
    }

    route<Main> {
        content { ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText("Please select a user from the list")
            }
        }
    }

    layout<AnotherLayout, Int> {
        loader { -> Random.nextInt() }

        wrapper { data, content ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText("AnotherLayout with data: $data")
                content()
            }
        }

        route<AnotherRoute, String> {
            loader { arg ->
                println("Loading AnotherRoute with arg: ${arg.value}...")
                delay(1000)
                "asnaeb"
            }

            pending {
                BasicText("Loading AnotherRoute...")
            }

            content { data ->
                val route = AnotherRoute::class.get()
                BasicText("Hello another router: ${route}, data: $data")
            }
        }
    }

    layout<UserLayout> {
        wrapper { content ->
            val sections = remember {
                mapOf(
                    "firstname" to UserFirstName,
                    "lastname" to UserLastName,
                    "email" to UserEmail
                )
            }

            val data = UserLayout::class.get()
            val user = users.find { it.id == data.userId }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText("Showing details for user ${user?.username}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    sections.forEach {
                        Box(Modifier.clickable { router.navigate(it.value) }) {
                            BasicText(it.key)
                        }
                    }
                    Box(Modifier.clickable { router.navigate(AddressStreet, UserAddress("SHIP")) }) {
                        BasicText("Address SHIP street")
                    }
                    Box(Modifier.clickable { router.navigate(AddressCity, UserAddress("BILL")) }) {
                        BasicText("Address BILL city")
                    }
                }
                content()
            }
        }

        route<UserHome> {
            content { ->
                val user = users.find { it.id == UserLayout::class.get().userId }

                BasicText("This is the home page of ${user?.username}")
            }
        }

        route<UserFirstName> {
            content { ->
                val user = users.find { it.id == UserLayout::class.get().userId }

                BasicText("First name of ${user?.username} is ${user?.firstname}")
            }
        }

        route<UserLastName> {
            content { ->
                val user = users.find { it.id == UserLayout::class.get().userId }

                BasicText("Last name of ${user?.username} is ${user?.lastname}")
            }
        }

        route<UserEmail> {
            content { ->
                val user = users.find { it.id == UserLayout::class.get().userId }

                BasicText("Email of ${user?.username} is ${user?.email}")
            }
        }

        layout<UserAddress> {
            wrapper { content ->
                val userId = UserLayout::class.get().userId
                val addressType = UserAddress::class.get().type
                val user = users.find { it.id == userId }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.clickable { router.navigate(AddressStreet) }) {
                            BasicText("Street")
                        }
                        Box(modifier = Modifier.clickable { router.navigate(AddressCity) }) {
                            BasicText("City")
                        }
                    }
                    BasicText("Showing address of type $addressType for ${user?.username}")
                    content()
                }
            }

            route<AddressStreet> {
                content { ->
                    val userId = UserLayout::class.get().userId
                    val user = users.find { it.id == userId }

                    BasicText("Address Street: ${user?.addressStreet}")
                }
            }

            route<AddressCity> {
                content { ->
                    val userId = UserLayout::class.get().userId
                    val user = users.find { it.id == userId }

                    BasicText("Address City: ${user?.addressCity}")
                }
            }
        }
    }
}