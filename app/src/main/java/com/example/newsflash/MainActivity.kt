
package com.example.newsflash

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import org.json.JSONObject
import java.net.URL
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Crear canal de notificaciones
        val canal = NotificationChannel(
            "noticias",
            "Noticias importantes",
            NotificationManager.IMPORTANCE_HIGH
        )

        val administrador = getSystemService(
            NotificationManager::class.java
        )
        administrador.createNotificationChannel(canal)

        // Solicitar permiso de notificaciones
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                100
            )
        }

        // Crear interfaz
        val pantalla = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 80, 40, 40)
        }

        val titulo = TextView(this).apply {
            text = "📰 NewsFlash"
            textSize = 28f
        }

        val descripcion = TextView(this).apply {
            text = "Mantente informado con las últimas noticias."
            textSize = 18f
        }

        val boton = Button(this).apply {
            text = "Actualizar noticias"
        }

        // Consultar noticias al presionar el botón
        boton.setOnClickListener {
            descripcion.text = "Consultando noticias..."

            Thread {
                try {
                    val url = URL(
                        "https://hn.algolia.com/api/v1/search?tags=front_page"
                    )

                    val conexion = url.openConnection()
                    conexion.connectTimeout = 10000
                    conexion.readTimeout = 10000

                    val respuesta = conexion.getInputStream()
                        .bufferedReader()
                        .use { it.readText() }

                    val datos = JSONObject(respuesta)
                    val noticias = datos.getJSONArray("hits")

                    val resultado = if (noticias.length() > 0) {
                        val noticia = noticias.getJSONObject(0)
                        noticia.optString("title", "Sin título")
                    } else {
                        "No hay noticias disponibles."
                    }

                    runOnUiThread {
                        descripcion.text = resultado
                    }

                } catch (e: Exception) {
                    runOnUiThread {
                        descripcion.text =
                            "Error al consultar noticias."
                    }
                }
            }.start()
        }

        pantalla.addView(titulo)
        pantalla.addView(descripcion)
        pantalla.addView(boton)

        setContentView(pantalla)

        // Requiere conexión a internet
        val restricciones = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Consulta periódica cada 15 minutos como mínimo
        val tareaNoticias =
            PeriodicWorkRequestBuilder<NoticiasWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(restricciones)
                .build()

        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(
                "actualizar_noticias",
                ExistingPeriodicWorkPolicy.KEEP,
                tareaNoticias
            )

        // Prueba de ejecución en segundo plano
        // Se programa una sola vez por instalación
        val preferencias = getSharedPreferences(
            "newsflash", MODE_PRIVATE
        )

        if (!preferencias.getBoolean("prueba_programada", false)) {
            val pruebaNoticias =
                OneTimeWorkRequestBuilder<NoticiasWorker>()
                    .setInitialDelay(30, TimeUnit.SECONDS)
                    .setConstraints(restricciones)
                    .build()

            WorkManager.getInstance(this)
                .enqueue(pruebaNoticias)

            preferencias.edit()
                .putBoolean("prueba_programada", true)
                .apply()
        }
    }
}
