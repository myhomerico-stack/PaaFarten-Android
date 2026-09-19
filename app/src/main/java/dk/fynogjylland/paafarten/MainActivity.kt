package dk.fynogjylland.paafarten

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PaaFartenApp() }
    }
}

private data class AppTab(val title: String, val icon: @Composable () -> Unit)
private data class Shift(val date: String, val start: String, val end: String, val title: String, val details: List<String>)
private data class Receipt(val type: String, val text: String, val amount: String, val status: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaaFartenApp() {
    var loggedIn by remember { mutableStateOf(false) }
    var selected by remember { mutableIntStateOf(0) }

    MaterialTheme {
        if (!loggedIn) {
            LoginScreen { loggedIn = true }
            return@MaterialTheme
        }

        val tabs = listOf(
            AppTab("I dag") { Icon(Icons.Default.Home, null) },
            AppTab("Vagtplan") { Icon(Icons.Default.CalendarMonth, null) },
            AppTab("Timer") { Icon(Icons.Default.Schedule, null) },
            AppTab("Kvittering") { Icon(Icons.Default.ReceiptLong, null) },
            AppTab("Profil") { Icon(Icons.Default.Person, null) }
        )

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("På farten") },
                    actions = { IconButton(onClick = { loggedIn = false }) { Icon(Icons.Default.Logout, "Log ud") } }
                )
            },
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
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (selected) {
                    0 -> TodayScreen()
                    1 -> ShiftCalendarScreen()
                    2 -> HoursScreen()
                    3 -> ReceiptScreen()
                    else -> ProfileScreen()
                }
            }
        }
    }
}

@Composable
private fun LoginScreen(onLogin: () -> Unit) {
    var showServer by remember { mutableStateOf(false) }
    if (showServer) {
        ServerSettingsScreen { showServer = false }
        return
    }
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(28.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("På farten", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text("Chauffør & medarbejder", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(28.dp))
            OutlinedTextField(user, { user = it }, label = { Text("Brugernavn") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(password, { password = it }, label = { Text("Adgangskode") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(20.dp))
            Button(onClick = onLogin, enabled = user.isNotBlank() && password.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text("Log ind")
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { showServer = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Settings, null)
                Spacer(Modifier.width(8.dp))
                Text("Serverindstillinger")
            }
            Spacer(Modifier.height(12.dp))
            Text("Serveroplysninger gemmes kun lokalt på telefonen og lægges ikke i GitHub.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private val demoShifts = listOf(
    Shift("I dag", "06:45", "15:30", "Vagtplan", listOf("06:45 Mød på garage", "07:00 Klargør bus", "07:30 Kørsel", "12:15 Pause", "15:15 Retur til garage", "15:30 Vagt slut")),
    Shift("Mandag 21/9", "07:00", "16:00", "Vagtplan", listOf("07:00 Mød på garage", "07:30 Kørsel", "16:00 Vagt slut")),
    Shift("Tirsdag 22/9", "08:00", "14:00", "Klargøring", listOf("08:00 Start", "14:00 Slut"))
)

@Composable
private fun TodayScreen() {
    val shift = demoShifts.first()
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("I dag", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Din næste planlagte vagt")
        }
        item { ShiftCard(shift, expanded = true) }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Hurtig adgang", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Vagtplan, kvitteringer og profil ligger i menuen nederst.")
                }
            }
        }
    }
}

@Composable
private fun ShiftCalendarScreen() {
    var month by remember { mutableStateOf("September 2026") }
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Vagtplan", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {}) { Icon(Icons.Default.ChevronLeft, null) }
                Text(month, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = {}) { Icon(Icons.Default.ChevronRight, null) }
            }
            Text("Kalenderen viser dine vagter. Data kobles senere direkte til pf_shifts og pf_shift_steps.")
        }
        items(demoShifts) { ShiftCard(it, expanded = false) }
    }
}

@Composable
private fun ShiftCard(shift: Shift, expanded: Boolean) {
    var open by remember { mutableStateOf(expanded) }
    ElevatedCard(Modifier.fillMaxWidth().clickable { open = !open }) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(shift.date, fontWeight = FontWeight.Bold)
                    Text(shift.title)
                }
                Text("${shift.start} – ${shift.end}", fontWeight = FontWeight.Bold)
            }
            if (open) {
                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                shift.details.forEach { Text(it, modifier = Modifier.padding(vertical = 3.dp)) }
            }
        }
    }
}

@Composable
private fun ReceiptScreen() {
    var type by remember { mutableStateOf("Udlæg") }
    var text by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Kvittering", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Send udlæg eller køb foretaget med firmakort.")
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = type == "Udlæg", onClick = { type = "Udlæg" }, label = { Text("Udlæg") })
                FilterChip(selected = type == "Indkøb fra kort", onClick = { type = "Indkøb fra kort" }, label = { Text("Indkøb fra kort") })
            }
        }
        item {
            OutlinedButton(onClick = { }, modifier = Modifier.fillMaxWidth().height(72.dp)) {
                Icon(Icons.Default.PhotoCamera, null)
                Spacer(Modifier.width(8.dp))
                Text("Tag billede af kvittering")
            }
        }
        item { OutlinedTextField(text, { text = it }, label = { Text("Tekst / hvad er købt?") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(amount, { amount = it }, label = { Text("Beløb i kr.") }, modifier = Modifier.fillMaxWidth()) }
        item {
            Button(
                onClick = { sent = true },
                enabled = text.isNotBlank() && amount.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Send kvittering") }
        }
        if (sent) item {
            AssistChip(onClick = {}, label = { Text("Klar til servertilkobling – oplysningerne er udfyldt") }, leadingIcon = { Icon(Icons.Default.CheckCircle, null) })
        }
        item {
            Text("Seneste", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            ReceiptCard(Receipt("Udlæg", "Parkering", "85,00 kr.", "Ny"))
            Spacer(Modifier.height(8.dp))
            ReceiptCard(Receipt("Indkøb fra kort", "Sprinklervæske", "149,95 kr.", "Godkendt"))
        }
    }
}

@Composable
private fun ReceiptCard(r: Receipt) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(r.type, fontWeight = FontWeight.Bold)
                Text(r.text)
                Text(r.status, style = MaterialTheme.typography.bodySmall)
            }
            Text(r.amount, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ProfileScreen() {
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(72.dp))
            Text("Min profil", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProfileLine("Navn", "Hentes fra På farten")
                    ProfileLine("Telefon", "Hentes fra chaufførprofil")
                    ProfileLine("E-mail", "Hentes fra chaufførprofil")
                    ProfileLine("Garage", "Hentes fra chaufførprofil")
                }
            }
        }
        item {
            Text("Appen er klar til næste trin: forbindelse til På farten-serveren, rigtigt login, vagtplaner og upload af kvitteringsbilleder.")
        }
    }
}

@Composable
private fun ProfileLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, fontWeight = FontWeight.Medium)
    }
}


@Composable
private fun HoursScreen() {
    val rows = listOf(
        "20/08" to "8 t. 45 min.",
        "21/08" to "7 t. 30 min.",
        "24/08" to "9 t. 00 min.",
        "25/08" to "8 t. 15 min."
    )
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Mine timer", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Lønperiode: 20. august – 19. september")
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Timer i perioden", style = MaterialTheme.typography.labelLarge)
                    Text("33 t. 30 min.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Timerne hentes fra dine registrerede vagter.")
                }
            }
        }
        items(rows) { row ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(row.first, fontWeight = FontWeight.Medium)
                    Text(row.second, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Text("Perioden går altid fra den 20. i måneden til den 19. i næste måned. Forrige og næste periode kobles på sammen med serverdata.")
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerSettingsScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("paafarten_server", Context.MODE_PRIVATE) }
    var host by remember { mutableStateOf(prefs.getString("host", "") ?: "") }
    var port by remember { mutableStateOf(prefs.getString("port", "3306") ?: "3306") }
    var database by remember { mutableStateOf(prefs.getString("database", "") ?: "") }
    var username by remember { mutableStateOf(prefs.getString("username", "") ?: "") }
    var password by remember { mutableStateOf(prefs.getString("password", "") ?: "") }
    var saved by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Serverindstillinger") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Tilbage") } }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("MySQL-server", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Indstillingerne gemmes lokalt på denne telefon. Adgangskoden bliver ikke skrevet ind i kildekoden eller GitHub.")
            }
            item { OutlinedTextField(host, { host = it }, label = { Text("Server / host") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
            item { OutlinedTextField(port, { port = it }, label = { Text("Port") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
            item { OutlinedTextField(database, { database = it }, label = { Text("Database") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
            item { OutlinedTextField(username, { username = it }, label = { Text("Bruger") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
            item {
                OutlinedTextField(
                    password, { password = it },
                    label = { Text("Kodeord") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                )
            }
            item {
                Button(
                    onClick = {
                        prefs.edit()
                            .putString("host", host.trim())
                            .putString("port", port.trim())
                            .putString("database", database.trim())
                            .putString("username", username.trim())
                            .putString("password", password)
                            .apply()
                        saved = true
                    },
                    enabled = host.isNotBlank() && port.isNotBlank() && database.isNotBlank() && username.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Gem serverindstillinger") }
            }
            if (saved) item {
                AssistChip(onClick = {}, label = { Text("Serverindstillinger er gemt på telefonen") }, leadingIcon = { Icon(Icons.Default.CheckCircle, null) })
            }
            item {
                Text("Bemærk: Den endelige app bør forbinde til en sikker HTTPS-API på WWW-serveren i stedet for at åbne MySQL direkte mod internettet. Denne menu kan stadig bruges til serveropsætningen.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
