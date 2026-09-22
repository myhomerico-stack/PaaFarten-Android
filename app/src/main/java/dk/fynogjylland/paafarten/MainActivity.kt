package dk.fynogjylland.paafarten

import android.os.Bundle
import android.content.Context
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import androidx.compose.ui.window.Dialog

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
    val context = androidx.compose.ui.platform.LocalContext.current
    val loginPrefs = remember { context.getSharedPreferences("paafarten_login", Context.MODE_PRIVATE) }
    var loggedIn by remember { mutableStateOf(!loginPrefs.getString("token","").isNullOrBlank()) }
    var selected by remember { mutableIntStateOf(0) }
    var startupUpdate by remember { mutableStateOf<AppUpdate?>(null) }
    var updateChecked by remember { mutableStateOf(false) }

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

        LaunchedEffect(loggedIn) {
            if (loggedIn && !updateChecked) {
                updateChecked = true
                UpdateManager.check(context) { it.onSuccess { u -> startupUpdate = u } }
            }
        }

        startupUpdate?.let { u ->
            AlertDialog(
                onDismissRequest = { startupUpdate = null },
                title = { Text("Ny opdatering ${u.versionName}") },
                text = { Text(u.message + "\n\nVil du opdatere På farten nu?") },
                confirmButton = { Button(onClick = { UpdateManager.install(context,u){} }) { Text("Opdater nu") } },
                dismissButton = { TextButton(onClick = { startupUpdate = null }) { Text("Ikke nu") } }
            )
        }

        val tabs = listOf(
            AppTab("I dag") { Icon(Icons.Default.Home, null) },
            AppTab("Vagtplan") { Icon(Icons.Default.CalendarMonth, null) },
            AppTab("Timer") { Icon(Icons.Default.Schedule, null) },
            AppTab("Kvittering") { Icon(Icons.Default.ReceiptLong, null) },
            AppTab("Profil") { Icon(Icons.Default.Person, null) },
            AppTab("Info & Update") { Icon(Icons.Default.SystemUpdate, null) }
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
                    actions = { IconButton(onClick = { loginPrefs.edit().clear().apply(); loggedIn = false }) { Icon(Icons.Default.Logout, "Log ud") } }
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
                    4 -> ProfileScreen()
                    else -> InfoUpdateScreen()
                }
            }
        }
    }
}

@Composable
private fun InfoUpdateScreen() {
    val context=androidx.compose.ui.platform.LocalContext.current
    var checking by remember { mutableStateOf(false) }
    var update by remember { mutableStateOf<AppUpdate?>(null) }
    var message by remember { mutableStateOf("") }
    val info=remember { context.packageManager.getPackageInfo(context.packageName,0) }
    LazyColumn(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        item { Text("Info & Update",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold) }
        item {
            ElevatedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text("På farten",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                Text("Installeret version: "+(info.versionName ?: "ukendt")+" ("+info.longVersionCode+")")
                Text("Appen søger automatisk efter en ny version, når den starter.")
            }}
        }
        item {
            Button(onClick={
                checking=true;message="Søger efter opdatering…";update=null
                UpdateManager.check(context){r->
                    checking=false
                    r.onSuccess{u->update=u;message=if(u==null)"Du har den nyeste version." else "Version "+u.versionName+" er klar."}
                     .onFailure{message="Kunne ikke søge efter opdatering: "+(it.message?:"serverfejl")}
                }
            },enabled=!checking,modifier=Modifier.fillMaxWidth()){
                Icon(Icons.Default.Refresh,null);Spacer(Modifier.width(8.dp));Text(if(checking)"Søger…" else "Søg efter opdatering")
            }
        }
        if(message.isNotBlank()) item { Text(message) }
        update?.let { u ->
            item { ElevatedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                Text("Ny version "+u.versionName,fontWeight=FontWeight.Bold)
                Text(u.message)
                Button(onClick={UpdateManager.install(context,u){message=it}},modifier=Modifier.fillMaxWidth()){
                    Icon(Icons.Default.Download,null);Spacer(Modifier.width(8.dp));Text("Opdater app")
                }
            }}}
        }
        item { Text("Ved installation viser Android sin sikkerhedsbekræftelse. Efter installation kan På farten åbnes igen med den nye version.",style=MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun LoginScreen(onLogin: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val apiUrl = "https://minside.fynogjylland.dk/api/"
    var email by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var showPin by remember { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxWidth().height(260.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
            )
            Column(
                Modifier.fillMaxSize().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(54.dp))
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    tonalElevation = 3.dp,
                    shadowElevation = 8.dp
                ) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(R.drawable.company_logo),
                        contentDescription = "Barbussen Fyn & Jylland",
                        modifier = Modifier.size(118.dp).padding(12.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text("På farten", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Chauffør & medarbejder",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(28.dp))
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Velkommen", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Log ind for at se vagtplan, timer og kvitteringer.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("E-mail") },
                            leadingIcon = { Icon(Icons.Default.Email, null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp)
                        )
                        OutlinedTextField(
                            value = pin,
                            onValueChange = { pin = it },
                            label = { Text("PIN") },
                            leadingIcon = { Icon(Icons.Default.Lock, null) },
                            trailingIcon = {
                                IconButton(onClick = { showPin = !showPin }) {
                                    Icon(if (showPin) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (showPin) "Skjul PIN" else "Vis PIN")
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            visualTransformation = if (showPin) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation()
                        )
                        if (error.isNotBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                                    Spacer(Modifier.width(8.dp))
                                    Text(error, color = MaterialTheme.colorScheme.onErrorContainer)
                                }
                            }
                        }
                        Button(
                            onClick = {
                                busy = true
                                error = ""
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
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            if (busy) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text("Logger ind…")
                            } else {
                                Icon(Icons.Default.Login, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Log ind", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    "Barbussen Fyn & Jylland",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text("Sikker adgang til På farten", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun rememberConnection(): Pair<String,String> {
    val context=androidx.compose.ui.platform.LocalContext.current
    val login=remember { context.getSharedPreferences("paafarten_login",Context.MODE_PRIVATE) }
    return "https://minside.fynogjylland.dk/api/" to (login.getString("token","") ?: "")
}
private fun mins(v:Int)=if(v<=0) "0 t." else "${v/60} t. ${v%60} min."
private fun clock(v:String):String {
    val s=v.trim()
    val m=Regex("""(?:^|[ T])(\d{2}:\d{2})(?::\d{2})?(?:$|[+Z])""").find(s)
    return m?.groupValues?.get(1)
        ?: Regex("""^(\d{2}:\d{2})""").find(s)?.groupValues?.get(1)
        ?: s
}

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
    var absences by remember { mutableStateOf<List<ApiAbsence>>(emptyList()) }
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }
    var error by remember { mutableStateOf("") }
    var showAbsence by remember { mutableStateOf(false) }
    LaunchedEffect(month,token){
        shifts=null; error=""; selectedDay=null
        ApiClient.shifts(url,token,month.atDay(1).toString(),month.atEndOfMonth().toString()){
            it.onSuccess{x->shifts=x}.onFailure{e->error=e.message?:"Serverfejl"}
        }
        ApiClient.absences(url,token,month.atDay(1).toString(),month.atEndOfMonth().toString()){
            it.onSuccess{x->absences=x}
        }
    }
    LazyColumn(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item {
            Text("Vagtplan",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
            Row(verticalAlignment=Alignment.CenterVertically){
                IconButton(onClick={month=month.minusMonths(1)}){Icon(Icons.Default.ChevronLeft,null)}
                Text("${month.month.name.lowercase().replaceFirstChar{it.uppercase()}} ${month.year}",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                IconButton(onClick={month=month.plusMonths(1)}){Icon(Icons.Default.ChevronRight,null)}
            }
        }
        item {
            OutlinedButton(onClick={showAbsence=!showAbsence},modifier=Modifier.fillMaxWidth()){
                Icon(Icons.Default.EventAvailable,null); Spacer(Modifier.width(8.dp)); Text("Fri / ferie / sygemelding")
            }
            if(showAbsence) AbsencePanel()
        }
        when {
            error.isNotBlank()->item{Text(error,color=MaterialTheme.colorScheme.error)}
            shifts==null->item{CircularProgressIndicator()}
            else -> {
                item { MonthCalendar(month,shifts!!,absences,selectedDay){selectedDay=it} }
                val dayShifts=selectedDay?.let{d->shifts!!.filter{x->x.date.take(10)==d.toString()}} ?: emptyList()
                if(selectedDay!=null) {
                    item { Text(selectedDay.toString(),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold) }
                    if(dayShifts.isEmpty()) item{Text("Ingen vagt denne dag.")}
                    else items(dayShifts){ RealShiftCard(it,false) }
                }
            }
        }
    }
}
@Composable
private fun MonthCalendar(month:YearMonth, shifts:List<ApiShift>, absences:List<ApiAbsence>, selected:LocalDate?, onDay:(LocalDate)->Unit){
    val shiftDates=shifts.mapNotNull{runCatching{LocalDate.parse(it.date.take(10))}.getOrNull()}.toSet()
    fun absenceOn(d:LocalDate)=absences.firstOrNull{runCatching{d>=LocalDate.parse(it.from)&&d<=LocalDate.parse(it.to)}.getOrDefault(false)}
    val first=month.atDay(1)
    val offset=first.dayOfWeek.value-1
    val cells=offset+month.lengthOfMonth()
    ElevatedCard(Modifier.fillMaxWidth()){
        Column(Modifier.padding(10.dp)){
            Row(Modifier.fillMaxWidth()){ listOf("Man","Tir","Ons","Tor","Fre","Lør","Søn").forEach{Text(it,Modifier.weight(1f),style=MaterialTheme.typography.labelMedium)} }
            repeat((cells+6)/7){week->
                Row(Modifier.fillMaxWidth()){
                    repeat(7){dow->
                        val n=week*7+dow-offset+1
                        if(n !in 1..month.lengthOfMonth()) Spacer(Modifier.weight(1f).height(64.dp))
                        else {
                            val d=month.atDay(n); val has=shiftDates.contains(d); val absence=absenceOn(d)
                            Surface(
                                modifier=Modifier.weight(1f).height(64.dp).padding(2.dp).clickable{onDay(d)},
                                shape=RoundedCornerShape(8.dp),
                                tonalElevation=if(selected==d) 6.dp else 0.dp
                            ){
                                Column(Modifier.padding(6.dp)){
                                    Text(n.toString(),fontWeight=if(has) FontWeight.Bold else FontWeight.Normal)
                                    if(has){ Spacer(Modifier.height(2.dp)); Text("Vagt",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold) }
                                    if(absence!=null){ Text("${absence.kind} · ${absence.status}",style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold) }
                                }
                            }
                        }
                    }
                }
            }
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
                Text("${clock(s.start)} – ${clock(s.end)}",fontWeight=FontWeight.Bold)
            }
            Text("Planlagt: ${mins(s.plannedMinutes)}")
            if(open){
                HorizontalDivider(Modifier.padding(vertical=10.dp))
                if(s.steps.isEmpty()) Text("Ingen vagttrin.") else s.steps.forEach { step ->
                    Row(Modifier.fillMaxWidth().padding(vertical=6.dp)) {
                        Text(clock(step.time), modifier=Modifier.width(58.dp), fontWeight=FontWeight.Bold)
                        Column {
                            Text(step.title, fontWeight=FontWeight.Bold)
                            if(step.detail.isNotBlank()) Text(step.detail, style=MaterialTheme.typography.bodySmall)
                        }
                    }
                }
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
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(token){ApiClient.profile(url,token){it.onSuccess{x->p=x;name=x.name;phone=x.phone}.onFailure{e->error=e.message?:"Serverfejl"}}}
    LazyColumn(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Icon(Icons.Default.AccountCircle,null,Modifier.size(72.dp));Text("Min profil",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)}
        if(error.isNotBlank()) item{Text(error,color=MaterialTheme.colorScheme.error)}
        else if(p==null) item{CircularProgressIndicator()}
        else item{ElevatedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            OutlinedTextField(name,{name=it},label={Text("Navn")},modifier=Modifier.fillMaxWidth(),singleLine=true)
            OutlinedTextField(phone,{phone=it},label={Text("Telefon")},modifier=Modifier.fillMaxWidth(),singleLine=true)
            OutlinedTextField(p!!.email,{},label={Text("E-mail")},modifier=Modifier.fillMaxWidth(),singleLine=true,readOnly=true,
                supportingText={Text("E-mail kan ikke ændres i appen.")})
            Button(onClick={
                saving=true;message=""
                ApiClient.updateProfile(url,token,name.trim(),phone.trim()){r->
                    saving=false
                    r.onSuccess { updated->p=updated;name=updated.name;phone=updated.phone;message="Oplysningerne er gemt." }
                     .onFailure { message=it.message?:"Kunne ikke gemme oplysningerne." }
                }
            },enabled=!saving&&name.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text(if(saving)"Gemmer…" else "Gem ændringer")}
            if(message.isNotBlank()) Text(message)
        }}}
    }
}
@Composable private fun ProfileLine(label:String,value:String){Column{Text(label,style=MaterialTheme.typography.labelMedium);Text(if(value.isBlank()) "Ikke oplyst" else value,fontWeight=FontWeight.Medium)}}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AbsencePanel(){
    val (url,token)=rememberConnection()
    var kind by remember { mutableStateOf("Fri") }
    var note by remember { mutableStateOf("") }
    var from by remember { mutableStateOf<LocalDate?>(null) }
    var to by remember { mutableStateOf<LocalDate?>(null) }
    var pickFrom by remember { mutableStateOf(false) }
    var pickTo by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    fun pretty(d:LocalDate?)=d?.let{"%02d-%02d-%04d".format(it.dayOfMonth,it.monthValue,it.year)} ?: "Vælg dato"

    if(pickFrom || pickTo){
        val state=rememberDatePickerState()
        DatePickerDialog(onDismissRequest={pickFrom=false;pickTo=false},confirmButton={
            TextButton(onClick={
                state.selectedDateMillis?.let{ms->
                    val d=java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneOffset.UTC).toLocalDate()
                    if(pickFrom){from=d;if(to==null||to!!<d)to=d}else to=d
                }
                pickFrom=false;pickTo=false
            }){Text("Vælg")}
        },dismissButton={TextButton(onClick={pickFrom=false;pickTo=false}){Text("Annuller")}}){DatePicker(state=state)}
    }

    ElevatedCard(Modifier.fillMaxWidth()){
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Text("Fravær",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                listOf("Fri","Ferie","Syg").forEach{k->FilterChip(selected=kind==k,onClick={kind=k;message=""},label={Text(k)})}
            }
            if(kind=="Syg"){
                Text("Sygemelding skal ske senest kl. 08:00 på vagttelefonen.",fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.error)
                Text("Denne registrering erstatter ikke opkaldet til vagttelefonen.",style=MaterialTheme.typography.bodySmall)
            } else {
                Text("Fra dato",fontWeight=FontWeight.Medium)
                OutlinedButton(onClick={pickFrom=true},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.CalendarMonth,null);Spacer(Modifier.width(8.dp));Text(pretty(from))}
                Text("Til dato",fontWeight=FontWeight.Medium)
                OutlinedButton(onClick={pickTo=true},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.CalendarMonth,null);Spacer(Modifier.width(8.dp));Text(pretty(to))}
                OutlinedTextField(note,{note=it},label={Text("Bemærkning")},modifier=Modifier.fillMaxWidth())
                Button(onClick={
                    val f=from?:return@Button;val t=to?:return@Button
                    busy=true;message=""
                    ApiClient.sendAbsence(url,token,kind,f.toString(),t.toString(),note){r->
                        busy=false
                        r.onSuccess{message="$kind-forespørgslen er sendt.";note="";from=null;to=null}
                         .onFailure{message="Kunne ikke sende: "+(it.message?:"serverfejl")}
                    }
                },enabled=!busy&&from!=null&&to!=null&&to!!>=from!!,modifier=Modifier.fillMaxWidth()){Text(if(busy)"Sender…" else "Send forespørgsel")}
                if(message.isNotBlank()) Text(message)
            }
        }
    }
}

@Composable
private fun ReceiptImage(imageUrl: String) {
    var image by remember(imageUrl) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(imageUrl) { mutableStateOf(false) }
    LaunchedEffect(imageUrl) {
        Thread {
            try {
                val conn = (java.net.URL(imageUrl).openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 6000
                    readTimeout = 10000
                }
                if (conn.responseCode in 200..299) {
                    val bmp = conn.inputStream.use { BitmapFactory.decodeStream(it) }
                    android.os.Handler(android.os.Looper.getMainLooper()).post { image = bmp }
                } else android.os.Handler(android.os.Looper.getMainLooper()).post { failed = true }
            } catch (_: Exception) {
                android.os.Handler(android.os.Looper.getMainLooper()).post { failed = true }
            }
        }.start()
    }
    when {
        image != null -> androidx.compose.foundation.Image(image!!.asImageBitmap(),"Kvitteringsfoto",Modifier.fillMaxWidth().heightIn(max=420.dp),contentScale=ContentScale.Fit)
        failed -> Text("Kunne ikke hente kvitteringsbilledet.",style=MaterialTheme.typography.bodySmall)
        else -> Box(Modifier.fillMaxWidth().height(120.dp),contentAlignment=Alignment.Center){CircularProgressIndicator()}
    }
}

@Composable
private fun ReceiptScreen() {
    val context=androidx.compose.ui.platform.LocalContext.current
    val (url,token)=rememberConnection()
    var type by remember { mutableStateOf("Udlæg") }
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val receiptPrefs=remember { context.getSharedPreferences("paafarten_receipts",Context.MODE_PRIVATE) }
    fun readCachedReceipts():List<ApiReceipt> = runCatching {
        val a=org.json.JSONArray(receiptPrefs.getString("items","[]"))
        (0 until a.length()).map { i ->
            val x=a.getJSONObject(i)
            ApiReceipt(x.optInt("id"),x.optString("type"),x.optString("description"),x.optString("amount"),x.optString("created_at"),x.optString("status","Ny"),x.optString("image_url"))
        }
    }.getOrDefault(emptyList())
    fun saveCachedReceipts(list:List<ApiReceipt>) {
        val a=org.json.JSONArray()
        list.take(100).forEach { r ->
            a.put(org.json.JSONObject().apply {
                put("id",r.id); put("type",r.type); put("description",r.description); put("amount",r.amount)
                put("created_at",r.createdAt); put("status",r.status); put("image_url",r.imageUrl)
            })
        }
        receiptPrefs.edit().putString("items",a.toString()).apply()
    }
    fun mergeReceipts(a:List<ApiReceipt>,b:List<ApiReceipt>) =
        (a+b).distinctBy { x -> if(x.id>0) "id:${x.id}" else "${x.createdAt}|${x.type}|${x.description}|${x.amount}" }
            .sortedByDescending { it.createdAt }

    var receipts by remember { mutableStateOf<List<ApiReceipt>?>(readCachedReceipts()) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var selectedReceipt by remember { mutableStateOf<ApiReceipt?>(null) }

    fun loadHistory(preserveExisting:Boolean=true){ ApiClient.receipts(url,token){
        it.onSuccess{server->
            val local=if(preserveExisting) (receipts ?: readCachedReceipts()) else emptyList()
            val merged=mergeReceipts(server,local)
            receipts=merged
            saveCachedReceipts(merged)
        }.onFailure{e->
            if(receipts==null) receipts=readCachedReceipts()
            message="Historik kunne ikke hentes: "+(e.message?:"serverfejl")
        }
    } }
    LaunchedEffect(token){ loadHistory(false) }
    DisposableEffect(Unit) {
        onDispose { }
    }

    val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->
        if(uri!=null) runCatching {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }.onSuccess { bitmap=it; message="" }.onFailure { message="Kunne ikke åbne billedet." }
    }
    val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()){ok->
        if(ok) {
            val uri=cameraUri
            if(uri!=null) runCatching {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            }.onSuccess { bitmap=it; message="" }.onFailure { message="Kunne ikke åbne kamerabilledet." }
        }
    }

    LazyColumn(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{
            Text("Udlæg & køb på kort",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
            Text("Kvitteringen sendes til kontoret og gemmes på din profil.")
        }
        item{
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                FilterChip(selected=type=="Udlæg",onClick={type="Udlæg"},label={Text("Udlæg")})
                FilterChip(selected=type=="Købt på kort",onClick={type="Købt på kort"},label={Text("Købt på kort")})
            }
        }
        item{
            OutlinedTextField(description,{description=it},label={Text("Hvad er købt?")},placeholder={Text("Fx 100 l diesel eller 2 pærer")},modifier=Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(amount,{amount=it.filter{x->x.isDigit()||x==','||x=='.'}},label={Text("Beløb i kr.")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        }
        item{
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedButton(onClick={
                    runCatching {
                        val dir=File(context.cacheDir,"receipts").apply{mkdirs()}
                        val file=File.createTempFile("receipt_",".jpg",dir)
                        val uri=FileProvider.getUriForFile(context,"dk.fynogjylland.paafarten.fileprovider",file)
                        cameraUri=uri
                        camera.launch(uri)
                    }.onFailure { message="Kamera kunne ikke startes: "+(it.message?:"ukendt fejl") }
                }){Icon(Icons.Default.PhotoCamera,null);Spacer(Modifier.width(6.dp));Text("Tag foto")}
                OutlinedButton(onClick={picker.launch("image/*")}){Icon(Icons.Default.PhotoLibrary,null);Spacer(Modifier.width(6.dp));Text("Vælg foto")}
            }
            bitmap?.let { img ->
                Spacer(Modifier.height(10.dp))
                androidx.compose.foundation.Image(img.asImageBitmap(),"Kvittering",Modifier.fillMaxWidth().heightIn(max=260.dp),contentScale=ContentScale.Fit)
            }
        }
        item{
            Button(onClick={
                val img=bitmap ?: return@Button
                busy=true; message=""
                ApiClient.uploadReceipt(url,token,type,description,amount,img){r->
                    busy=false
                    r.onSuccess { saved->
                        val updated=mergeReceipts(listOf(saved),receipts?:emptyList())
                        receipts=updated
                        saveCachedReceipts(updated)
                        message="Kvitteringen er sendt til kontoret og gemt."
                        description=""; amount=""; bitmap=null
                        loadHistory(preserveExisting=false)
                    }
                     .onFailure { message=it.message?:"Kunne ikke sende kvitteringen." }
                }
            },enabled=!busy&&description.isNotBlank()&&amount.isNotBlank()&&bitmap!=null,modifier=Modifier.fillMaxWidth()){
                Icon(Icons.Default.Send,null);Spacer(Modifier.width(8.dp));Text(if(busy)"Sender…" else "Send til kontoret")
            }
            if(message.isNotBlank()) Text(message)
        }
        item{Text("Mine indsendte kvitteringer",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)}
        when {
            receipts==null -> item{CircularProgressIndicator()}
            receipts!!.isEmpty() -> item{Text("Du har endnu ikke indsendt kvitteringer.")}
            else -> items(receipts!!){r->
                ElevatedCard(Modifier.fillMaxWidth().clickable{selectedReceipt=r}){Column(Modifier.padding(14.dp)){
                    Text("${r.createdAt} · ${r.type}",fontWeight=FontWeight.Bold)
                    Text(r.description)
                    Text("${r.amount} kr. · ${r.status}")
                }}
            }
        }
    }
    selectedReceipt?.let { r ->
        Dialog(onDismissRequest={selectedReceipt=null}) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                        Text("Kvittering",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                        IconButton(onClick={selectedReceipt=null}){Icon(Icons.Default.Close,"Luk")}
                    }
                    Text(r.createdAt,fontWeight=FontWeight.Bold)
                    Text(r.type)
                    Text(r.description)
                    Text("${r.amount} kr. · ${r.status}")
                    if(r.imageUrl.isNotBlank()) {
                        ReceiptImage(r.imageUrl)
                    } else {
                        Text("Billedet er gemt, men serveren sender endnu ikke billedadressen tilbage til appen.",style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
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
