package dk.fynogjylland.paafarten

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdate(val versionCode:Int,val versionName:String,val apkUrl:String,val message:String)

object UpdateManager {
    const val MANIFEST_URL="https://minside.fynogjylland.dk/update/update.json"

    fun check(context:Context, callback:(Result<AppUpdate?>)->Unit){
        Thread {
            val result=runCatching {
                val conn=(URL(MANIFEST_URL).openConnection() as HttpURLConnection).apply {
                    connectTimeout=6000;readTimeout=6000;setRequestProperty("Accept","application/json")
                }
                if(conn.responseCode !in 200..299) throw IllegalStateException("Serveren svarede HTTP "+conn.responseCode)
                val j=JSONObject(conn.inputStream.bufferedReader().use{it.readText()})
                val remote=j.getInt("versionCode")
                val current=context.packageManager.getPackageInfo(context.packageName,0).longVersionCode.toInt()
                if(remote>current) AppUpdate(remote,j.optString("versionName"),j.getString("apkUrl"),j.optString("message","Ny version er klar.")) else null
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post{callback(result)}
        }.start()
    }

    fun install(context:Context, update:AppUpdate, status:(String)->Unit){
        if(android.os.Build.VERSION.SDK_INT>=26 && !context.packageManager.canRequestPackageInstalls()){
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:"+context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            status("Tillad installation fra På farten, gå tilbage til appen og tryk Opdater igen.")
            return
        }
        status("Henter opdatering…")
        Thread {
            try {
                val dir=File(context.cacheDir,"updates").apply{mkdirs()}
                val apk=File(dir,"paafarten-update.apk")
                val conn=(URL(update.apkUrl).openConnection() as HttpURLConnection).apply{connectTimeout=10000;readTimeout=30000}
                if(conn.responseCode !in 200..299) throw IllegalStateException("HTTP "+conn.responseCode)
                conn.inputStream.use{input->apk.outputStream().use{output->input.copyTo(output)}}
                val uri=FileProvider.getUriForFile(context,context.packageName+".fileprovider",apk)
                val intent=Intent(Intent.ACTION_VIEW).apply{
                    setDataAndType(uri,"application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                android.os.Handler(android.os.Looper.getMainLooper()).post{
                    status("Åbner Android-opdateringen…")
                    context.startActivity(intent)
                }
            } catch(e:Exception) {
                android.os.Handler(android.os.Looper.getMainLooper()).post{status("Opdatering fejlede: "+(e.message?:"ukendt fejl"))}
            }
        }.start()
    }
}
