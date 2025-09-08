import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application

fun main() = application {
    Window(onCloseRequest = ::exitApplication, undecorated = true, transparent = true, title = "NAVZION") {
        Surface(
            Modifier
                .fillMaxSize()
                .padding(12.dp)
                .shadow(6.dp, RoundedCornerShape(12.dp))
                .border(1.dp, Color(0, 0, 0, 150), RoundedCornerShape(12.dp))
                .padding(1.dp)
                .border(1.dp, Color(255, 255, 255, 75), RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column {
                WindowDraggableArea(
                    Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .background(Color.DarkGray)
                        .padding(horizontal = 12.dp)
                ) {
                    Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                        BasicText("navzion", color = { Color.White })
                    }
                    Row(
                        Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(Color.Red).clickable { exitApplication() })
                        Box(Modifier.size(12.dp).clip(CircleShape).background(Color.Yellow).clickable { window.isMinimized = true })
                        Box(
                            Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(Color.Green)
                                .clickable {
                                    if (window.placement == WindowPlacement.Maximized) {
                                        window.placement = WindowPlacement.Floating
                                    }
                                    else {
                                        window.placement = WindowPlacement.Maximized
                                    }
                                }
                        )
                    }
                }
                router.Render()
            }
        }
    }
}