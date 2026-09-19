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
