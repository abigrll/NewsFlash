
package com.example.newsflash

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.net.URL

class NoticiasWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        return try {
            val conexion = URL(
                "https://hn.algolia.com/api/v1/search?tags=front_page"
            ).openConnection()

            conexion.connectTimeout = 10000
            conexion.readTimeout = 10000

            val respuesta = conexion.getInputStream()
                .bufferedReader().use { it.readText() }

            val noticias = JSONObject(respuesta)
                .getJSONArray("hits")

            if (noticias.length() > 0) {
                val noticia = noticias.getJSONObject(0)
                val id = noticia.optString("objectID")
                val titulo = noticia.optString("title")

                val preferencias =
                    applicationContext.getSharedPreferences(
                        "newsflash", Context.MODE_PRIVATE
                    )

                val anterior = preferencias.getString(
                    "ultima_noticia", null
                )

                if (id.isNotEmpty() && titulo.isNotEmpty()
                    && id != anterior) {

                    mostrarNotificacion(titulo)

                    preferencias.edit()
                        .putString("ultima_noticia", id)
                        .apply()
                }
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun mostrarNotificacion(titulo: String) {
        val manager = applicationContext.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager

        val canal = NotificationChannel(
            "noticias",
            "Noticias importantes",
            NotificationManager.IMPORTANCE_HIGH
        )

        manager.createNotificationChannel(canal)

        if (Build.VERSION.SDK_INT >= 33 &&
            applicationContext.checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        val notificacion = Notification.Builder(
            applicationContext, "noticias"
        )
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("NewsFlash - Última noticia")
            .setContentText(titulo)
            .setStyle(
                Notification.BigTextStyle().bigText(titulo)
            )
            .setAutoCancel(true)
            .build()

        manager.notify(2, notificacion)
    }
}
