package dk.fynogjylland.paafarten

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PaaFartenApp() }
    }
}

private data class AppTab(val title: String, val icon: @Composable () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaaFartenApp() {
    var selected by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        AppTab("I dag") { Icon(Icons.Default.Home, null) },
        AppTab("Vagtplan") { Icon(Icons.Default.CalendarMonth, null) },
        AppTab("Kvittering") { Icon(Icons.Default.ReceiptLong, null) },
        AppTab("Profil") { Icon(Icons.Default.Person, null) }
    )

    MaterialTheme {
        Scaffold(
            topBar = { TopAppBar(title = { Text("På farten") }) },
            bottomBar = {
                NavigationBar {
                    tabs.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = selected == index,
                            onClick = { selected = index },
                            icon = tab.icon,
                            label = { Text(tab.title) }
                        )
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
                contentAlignment = Alignment.TopStart
            ) {
                when (selected) {
                    0 -> StartScreen()
                    1 -> Placeholder("Vagtplan & kalender", "Her kommer chaufførens vagter fra På farten.")
                    2 -> Placeholder("Udlæg & indkøb", "Tag billede af kvittering, vælg type, skriv tekst og beløb.")
                    else -> Placeholder("Profil", "Chaufførens oplysninger og indstillinger.")
                }
            }
        }
    }
}

@Composable
private fun StartScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("I dag", style = MaterialTheme.typography.headlineMedium)
        Text("På farten chauffør-app")
        Text("Android 16 • første projektversion")
    }
}

@Composable
private fun Placeholder(title: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(text)
    }
}
