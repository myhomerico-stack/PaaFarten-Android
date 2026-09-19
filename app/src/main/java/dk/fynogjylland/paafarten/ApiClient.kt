package dk.fynogjylland.paafarten

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

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
                    connectTimeout = 10000
                    readTimeout = 10000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    setRequestProperty("Accept", "application/json")
                }
                val body = "email=" + URLEncoder.encode(email, "UTF-8") + "&pin=" + URLEncoder.encode(pin, "UTF-8")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
                val json = JSONObject(stream.bufferedReader().use { it.readText() })
                if (json.optBoolean("ok")) {
                    LoginResult(true, json.optString("token"), json.optString("name"))
                } else LoginResult(false, message = json.optString("message", "Login mislykkedes."))
            } catch (e: Exception) {
                LoginResult(false, message = "Kunne ikke kontakte serveren. Kontroller serveradressen og internetforbindelsen.")
            }
            Handler(Looper.getMainLooper()).post { callback(result) }
        }.start()
    }
}
