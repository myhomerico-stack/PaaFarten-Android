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
    var selectedReceipt by remember { mutableStateOf<ApiReceipt?>(null) }
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
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }
    var error by remember { mutableStateOf("") }
    var showAbsence by remember { mutableStateOf(false) }
    LaunchedEffect(month,token){
        shifts=null; error=""; selectedDay=null
        ApiClient.shifts(url,token,month.atDay(1).toString(),month.atEndOfMonth().toString()){
            it.onSuccess{x->shifts=x}.onFailure{e->error=e.message?:"Serverfejl"}
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
                item { MonthCalendar(month,shifts!!,selectedDay){selectedDay=it} }
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
private fun MonthCalendar(month:YearMonth, shifts:List<ApiShift>, selected:LocalDate?, onDay:(LocalDate)->Unit){
    val shiftDates=shifts.mapNotNull{runCatching{LocalDate.parse(it.date.take(10))}.getOrNull()}.toSet()
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
                            val d=month.atDay(n); val has=shiftDates.contains(d)
                            Surface(
                                modifier=Modifier.weight(1f).height(64.dp).padding(2.dp).clickable{onDay(d)},
                                shape=RoundedCornerShape(8.dp),
                                tonalElevation=if(selected==d) 6.dp else 0.dp
                            ){
                                Column(Modifier.padding(6.dp)){
                                    Text(n.toString(),fontWeight=if(has) FontWeight.Bold else FontWeight.Normal)
                                    if(has){ Spacer(Modifier.height(4.dp)); Text("Vagt",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold) }
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
private fun AbsencePanel(){
    var kind by remember { mutableStateOf("Fri") }
    var note by remember { mutableStateOf("") }
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    ElevatedCard(Modifier.fillMaxWidth()){
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text("Fravær",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                listOf("Fri","Ferie","Syg").forEach{k->FilterChip(selected=kind==k,onClick={kind=k},label={Text(k)})}
            }
            if(kind=="Syg"){
                Text("Sygemelding skal ske senest kl. 08:00 på vagttelefonen.",fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.error)
                Text("Denne registrering erstatter ikke opkaldet til vagttelefonen.",style=MaterialTheme.typography.bodySmall)
            } else {
                OutlinedTextField(from,{from=it},label={Text("Fra dato (ÅÅÅÅ-MM-DD)")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                OutlinedTextField(to,{to=it},label={Text("Til dato (ÅÅÅÅ-MM-DD)")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                OutlinedTextField(note,{note=it},label={Text("Bemærkning")},modifier=Modifier.fillMaxWidth())
                Button(onClick={},enabled=false,modifier=Modifier.fillMaxWidth()){Text("Send ansøgning – servertilkobling følger")}
            }
        }
    }
}

@Composable
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

private fun ReceiptScreen() {
    val context=androidx.compose.ui.platform.LocalContext.current
    val (url,token)=rememberConnection()
    var type by remember { mutableStateOf("Udlæg") }
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var receipts by remember { mutableStateOf<List<ApiReceipt>?>(null) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var selectedReceipt by remember { mutableStateOf<ApiReceipt?>(null) }

    fun loadHistory(preserveExisting:Boolean=false){ ApiClient.receipts(url,token){
        it.onSuccess{r->
            receipts = if(preserveExisting) {
                val local=receipts ?: emptyList()
                (local+r).distinctBy { x -> if(x.id>0) "id:${x.id}" else "${x.createdAt}|${x.type}|${x.description}|${x.amount}" }
            } else r
        }.onFailure{e->
            if(receipts==null) receipts=emptyList()
            message="Historik kunne ikke hentes: "+(e.message?:"serverfejl")
        }
    } }
    LaunchedEffect(token){ loadHistory() }

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
                        receipts=listOf(saved)+(receipts?:emptyList())
                        message="Kvitteringen er sendt til kontoret og gemt."
                        description=""; amount=""; bitmap=null
                        loadHistory(preserveExisting=true)
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
