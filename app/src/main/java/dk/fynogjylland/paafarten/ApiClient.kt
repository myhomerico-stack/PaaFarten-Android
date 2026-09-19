package dk.fynogjylland.paafarten

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.SSLHandshakeException

data class LoginResult(val ok: Boolean, val token: String = "", val name: String = "", val message: String = "")

object ApiClient {
    fun login(baseUrl: String, email: String, pin: String, callback: (LoginResult) -> Unit) {
        if (!baseUrl.startsWith("https://")) {
            callback(LoginResult(false, message = "Indstil først en gyldig HTTPS-server under Serverindstillinger."))
            return
        }
        Thread {
            val result = try {
                val root = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
                val conn = (URL(root + "login.php").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 6000
                    readTimeout = 6000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    setRequestProperty("Accept", "application/json")
                }
                val body = "email=" + URLEncoder.encode(email, "UTF-8") + "&pin=" + URLEncoder.encode(pin, "UTF-8")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (raw.isBlank()) {
                    LoginResult(false, message = "Serveren svarede HTTP $code uden data.")
                } else {
                    try {
                        val json = JSONObject(raw)
                        if (json.optBoolean("ok")) {
                            LoginResult(true, json.optString("token"), json.optString("name"))
                        } else {
                            LoginResult(false, message = "Server HTTP $code: " + json.optString("message", "Login mislykkedes."))
                        }
                    } catch (e: Exception) {
                        LoginResult(false, message = "Server HTTP $code svarede ikke med gyldig JSON: " + raw.take(160))
                    }
                }
            } catch (e: SSLHandshakeException) {
                LoginResult(false, message = "SSL-fejl: " + (e.message ?: e.javaClass.simpleName))
            } catch (e: Exception) {
                LoginResult(false, message = "Forbindelsesfejl: " + e.javaClass.simpleName + ": " + (e.message ?: "ukendt fejl"))
            }
            Handler(Looper.getMainLooper()).post { callback(result) }
        }.start()
    }
}


data class ApiShiftStep(val time:String,val title:String,val detail:String)
data class ApiShift(val date:String,val start:String,val end:String,val plannedMinutes:Int,val actualMinutes:Int,val steps:List<ApiShiftStep>)
data class ApiHours(val from:String,val to:String,val plannedMinutes:Int,val actualMinutes:Int,val rows:List<Pair<String,Int>>)
data class ApiProfile(val name:String,val email:String,val phone:String)

private fun authGet(baseUrl:String, token:String, endpoint:String): JSONObject {
    val root=if(baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
    val conn=(URL(root+endpoint).openConnection() as HttpURLConnection).apply {
        requestMethod="GET"; connectTimeout=6000; readTimeout=6000
        setRequestProperty("Accept","application/json")
        setRequestProperty("Authorization","Bearer $token")
    }
    val code=conn.responseCode
    val raw=(if(code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use{it.readText()}.orEmpty()
    if(code !in 200..299) throw IllegalStateException(JSONObject(raw).optString("message","HTTP $code"))
    return JSONObject(raw)
}

fun ApiClient.shifts(baseUrl:String,token:String,from:String,to:String,callback:(Result<List<ApiShift>>)->Unit) {
    Thread {
        val r=runCatching {
            val j=authGet(baseUrl,token,"shifts.php?from=$from&to=$to")
            val a=j.getJSONArray("shifts")
            (0 until a.length()).map { i ->
                val s=a.getJSONObject(i); val sa=s.optJSONArray("steps")
                val steps=if(sa==null) emptyList() else (0 until sa.length()).map { k ->
                    val x=sa.getJSONObject(k)
                    // Samme felter som minside/day.php: step_time, step_text og detail.
                    // Tidspunktet er det gemte tidspunkt fra På farten-vagtplanen.
                    val time=x.optString("step_time")
                    val title=x.optString("step_text").ifBlank { "Vagttrin" }
                    val detail=x.optString("detail")
                    ApiShiftStep(time,title,detail)
                }
                ApiShift(s.optString("work_date"),s.optString("planned_start"),s.optString("planned_end"),s.optInt("planned_minutes"),s.optInt("actual_minutes"),steps)
            }
        }
        Handler(Looper.getMainLooper()).post{callback(r)}
    }.start()
}
fun ApiClient.hours(baseUrl:String,token:String,offset:Int,callback:(Result<ApiHours>)->Unit) {
    Thread {
        val r=runCatching {
            val j=authGet(baseUrl,token,"hours.php?offset=$offset"); val p=j.getJSONObject("period"); val a=j.getJSONArray("hours")
            val rows=(0 until a.length()).map{i-> val x=a.getJSONObject(i); x.optString("work_date") to x.optInt("actual_minutes")}
            ApiHours(p.optString("from"),p.optString("to"),p.optInt("planned_minutes"),p.optInt("actual_minutes"),rows)
        }
        Handler(Looper.getMainLooper()).post{callback(r)}
    }.start()
}
fun ApiClient.profile(baseUrl:String,token:String,callback:(Result<ApiProfile>)->Unit) {
    Thread {
        val r=runCatching {
            val p=authGet(baseUrl,token,"profile.php").getJSONObject("profile")
            ApiProfile(p.optString("name"),p.optString("email"),p.optString("phone",p.optString("mobile")))
        }
        Handler(Looper.getMainLooper()).post{callback(r)}
    }.start()
}
