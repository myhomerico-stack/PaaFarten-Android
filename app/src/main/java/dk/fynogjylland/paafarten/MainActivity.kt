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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth

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

    val companyColors = lightColorScheme(
        primary = Color(0xFFD99A00),
        onPrimary = Color.Black,
        secondary = Color.Black,
        onSecondary = Color.White,
        surface = Color(0xFFFFFBF2),
        background = Color(0xFFFFFBF2)
    )
    MaterialTheme(colorScheme = companyColors) {
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
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.foundation.Image(
                                painter = painterResource(R.drawable.company_logo),
                                contentDescription = null,
                                modifier = Modifier.size(46.dp),
                                contentScale = ContentScale.Fit
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("På farten", fontWeight = FontWeight.Bold)
                        }
                    },
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("paafarten_server", Context.MODE_PRIVATE) }
    var showServer by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    if (showServer) {
        ServerSettingsScreen { showServer = false }
        return
    }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center) {
            androidx.compose.foundation.Image(
                painter = painterResource(R.drawable.company_logo),
                contentDescription = "Farhusser Fyn & Jylland",
                modifier = Modifier.fillMaxWidth().height(180.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.height(10.dp))
            Text("På farten", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text("Chauffør & medarbejder", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(28.dp))
            OutlinedTextField(email, { email = it }, label = { Text("E-mail") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                pin, { pin = it },
                label = { Text("PIN") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
            )
            if (error.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(error, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    busy = true
                    error = ""
                    val apiUrl = prefs.getString("api_url", "") ?: ""
                    ApiClient.login(apiUrl, email.trim(), pin) { result ->
                        busy = false
                        if (result.ok) {
                            context.getSharedPreferences("paafarten_login", Context.MODE_PRIVATE).edit()
                                .putString("token", result.token)
                                .putString("driver_name", result.name)
                                .putString("driver_email", email.trim())
                                .apply()
                            onLogin()
                        } else error = result.message
                    }
                },
                enabled = !busy && email.isNotBlank() && pin.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (busy) "Logger ind…" else "Log ind") }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { showServer = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Settings, null)
                Spacer(Modifier.width(8.dp))
                Text("Serverindstillinger")
            }
            Spacer(Modifier.height(12.dp))
            Text("Bruger samme e-mail og PIN som chaufførsiden.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun rememberConnection(): Pair<String,String> {
    val context=androidx.compose.ui.platform.LocalContext.current
    val server=remember { context.getSharedPreferences("paafarten_server",Context.MODE_PRIVATE) }
    val login=remember { context.getSharedPreferences("paafarten_login",Context.MODE_PRIVATE) }
    return (server.getString("api_url","https://minside.fynogjylland.dk/api/") ?: "https://minside.fynogjylland.dk/api/") to
        (login.getString("token","") ?: "")
}
private fun mins(v:Int)=if(v<=0) "0 t." else "${v/60} t. ${v%60} min."

@Composable
private fun TodayScreen() {
    val (url,token)=rememberConnection()
    var shifts by remember { mutableStateOf<List<ApiShift>?>(null) }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(token) {
        val d=LocalDate.now().toString()
        ApiClient.shifts(url,token,d,d){ it.onSuccess { x->shifts=x }.onFailure { e->error=e.message?:"Serverfejl" } }
    }
    LazyColumn(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item { Text("I dag",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold) }
        when {
            error.isNotBlank()->item{Text(error,color=MaterialTheme.colorScheme.error)}
            shifts==null->item{CircularProgressIndicator()}
            shifts!!.isEmpty()->item{Text("Ingen vagt planlagt i dag.")}
            else->items(shifts!!){ RealShiftCard(it,true) }
        }
    }
}
@Composable
private fun ShiftCalendarScreen() {
    val (url,token)=rememberConnection()
    var month by remember { mutableStateOf(YearMonth.now()) }
    var shifts by remember { mutableStateOf<List<ApiShift>?>(null) }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(month,token){
        shifts=null; error=""
        ApiClient.shifts(url,token,month.atDay(1).toString(),month.atEndOfMonth().toString()){
            it.onSuccess{x->shifts=x}.onFailure{e->error=e.message?:"Serverfejl"}
        }
    }
    LazyColumn(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item {
            Text("Vagtplan",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
            Row(verticalAlignment=Alignment.CenterVertically){
                IconButton(onClick={month=month.minusMonths(1)}){Icon(Icons.Default.ChevronLeft,null)}
                Text(month.toString(),Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                IconButton(onClick={month=month.plusMonths(1)}){Icon(Icons.Default.ChevronRight,null)}
            }
        }
        when {
            error.isNotBlank()->item{Text(error,color=MaterialTheme.colorScheme.error)}
            shifts==null->item{CircularProgressIndicator()}
            shifts!!.isEmpty()->item{Text("Ingen vagter i denne måned.")}
            else->items(shifts!!){RealShiftCard(it,false)}
        }
    }
}
@Composable
private fun RealShiftCard(s:ApiShift,expanded:Boolean){
    var open by remember { mutableStateOf(expanded) }
    ElevatedCard(Modifier.fillMaxWidth().clickable{open=!open}){
        Column(Modifier.padding(16.dp)){
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                Text(s.date,fontWeight=FontWeight.Bold)
                Text("${s.start} – ${s.end}",fontWeight=FontWeight.Bold)
            }
            Text("Planlagt: ${mins(s.plannedMinutes)}")
            if(open){
                HorizontalDivider(Modifier.padding(vertical=10.dp))
                if(s.steps.isEmpty()) Text("Ingen vagttrin.") else s.steps.forEach{Text(it,Modifier.padding(vertical=3.dp))}
            }
        }
    }
}
@Composable
private fun HoursScreen(){
    val (url,token)=rememberConnection()
    var offset by remember { mutableIntStateOf(0) }
    var data by remember { mutableStateOf<ApiHours?>(null) }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(offset,token){
        data=null;error=""
        ApiClient.hours(url,token,offset){it.onSuccess{x->data=x}.onFailure{e->error=e.message?:"Serverfejl"}}
    }
    LazyColumn(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{
            Text("Mine timer",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
            Row(verticalAlignment=Alignment.CenterVertically){
                IconButton(onClick={offset--}){Icon(Icons.Default.ChevronLeft,null)}
                Text(data?.let{"${it.from} – ${it.to}"}?:"Henter lønperiode…",Modifier.weight(1f))
                IconButton(onClick={ if(offset < 0) offset++ }){Icon(Icons.Default.ChevronRight,null)}
            }
        }
        if(error.isNotBlank()) item{Text(error,color=MaterialTheme.colorScheme.error)}
        else if(data==null) item{CircularProgressIndicator()}
        else {
            item{ElevatedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){
                Text("Registrerede timer",style=MaterialTheme.typography.labelLarge)
                Text(mins(data!!.actualMinutes),style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
                Text("Planlagt: ${mins(data!!.plannedMinutes)}")
            }}}
            if(data!!.rows.isEmpty()) item{Text("Ingen timer registreret i perioden.")}
            else items(data!!.rows){r->ElevatedCard(Modifier.fillMaxWidth()){Row(Modifier.fillMaxWidth().padding(16.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(r.first);Text(mins(r.second),fontWeight=FontWeight.Bold)}}}
        }
    }
}
@Composable
private fun ProfileScreen(){
    val (url,token)=rememberConnection()
    var p by remember { mutableStateOf<ApiProfile?>(null) }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(token){ApiClient.profile(url,token){it.onSuccess{x->p=x}.onFailure{e->error=e.message?:"Serverfejl"}}}
    LazyColumn(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Icon(Icons.Default.AccountCircle,null,Modifier.size(72.dp));Text("Min profil",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)}
        if(error.isNotBlank()) item{Text(error,color=MaterialTheme.colorScheme.error)}
        else if(p==null) item{CircularProgressIndicator()}
        else item{ElevatedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            ProfileLine("Navn",p!!.name); ProfileLine("Telefon",p!!.phone); ProfileLine("E-mail",p!!.email)
        }}}
    }
}
@Composable private fun ProfileLine(label:String,value:String){Column{Text(label,style=MaterialTheme.typography.labelMedium);Text(if(value.isBlank()) "Ikke oplyst" else value,fontWeight=FontWeight.Medium)}}

@Composable
private fun ReceiptScreen() {
    LazyColumn(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Text("Kvitteringer",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold);Text("Kvitteringsupload kobles på som næste serverfunktion.")}
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerSettingsScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("paafarten_server", Context.MODE_PRIVATE) }
    var apiUrl by remember {
        mutableStateOf(
            (prefs.getString("api_url", "") ?: "").let {
                if (it.isBlank() || it == "https://fynogjylland.dk/minside/api/") "https://minside.fynogjylland.dk/api/" else it
            }
        )
    }
    var saved by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Serverindstillinger") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Tilbage") } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("På farten-server", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Appen forbinder via HTTPS. MySQL-bruger og kodeord ligger kun på webserveren og ikke i appen.")
            OutlinedTextField(apiUrl, { apiUrl = it }, label = { Text("API-adresse") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Button(
                onClick = {
                    var u = apiUrl.trim()
                    if (u.isNotBlank() && !u.endsWith("/")) u += "/"
                    prefs.edit().putString("api_url", u).apply()
                    apiUrl = u
                    saved = true
                },
                enabled = apiUrl.startsWith("https://"),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Gem serverindstillinger") }
            if (saved) Text("Serverindstillinger er gemt på telefonen.")
        }
    }
}
