package com.blacksamdev.bbsmoove.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * Télécharge et décompresse les bases régionales (.db.gz) depuis la Release
 * GitHub "osm-data-latest", et les stocke dans le répertoire privé de l'app
 * (filesDir/regions/), où le lookup pourra les lire.
 *
 * Pour ce premier jet, la région est fixée (Bourgogne). La détection
 * automatique + la bulle de régions limitrophes viendront ensuite.
 */
class RegionDownloadManager(private val context: Context) {

    sealed interface State {
        data object Idle : State
        data class Downloading(val percent: Int) : State
        data object Decompressing : State
        data class Ready(val regionCode: String) : State
        data class Error(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(
        // Si la base d'une région est déjà présente au démarrage, on part
        // directement en Idle ; le ViewModel corrigera en Ready au besoin.
        State.Idle
    )
    val state: StateFlow<State> = _state.asStateFlow()

    /** Force l'état à Ready (appelé au démarrage si la base est déjà là). */
    fun markReadyIfAvailable(regionCode: String) {
        if (isAvailable(regionCode)) {
            _state.value = State.Ready(regionCode)
        }
    }

    private val regionsDir: File
        get() = File(context.filesDir, "regions").apply { mkdirs() }

    /** Chemin local du .db décompressé pour une région (qu'il existe ou non). */
    fun localDbFile(regionCode: String): File = File(regionsDir, "$regionCode.db")

    /** Vrai si la base de cette région est déjà téléchargée et prête. */
    fun isAvailable(regionCode: String): Boolean = localDbFile(regionCode).exists()

    private fun releaseUrl(regionCode: String): String =
        "https://github.com/blacksamdev/BBS-mOOve-Android/releases/download/osm-data-latest/$regionCode.db.gz"

    /**
     * Télécharge le .db.gz de la région, le décompresse en .db local.
     * À appeler depuis une coroutine (ne bloque pas le thread UI).
     */
    suspend fun download(regionCode: String) = withContext(Dispatchers.IO) {
        try {
            if (isAvailable(regionCode)) {
                _state.value = State.Ready(regionCode)
                return@withContext
            }

            val gzTmp = File(regionsDir, "$regionCode.db.gz.tmp")
            val dbTmp = File(regionsDir, "$regionCode.db.tmp")
            val dbFinal = localDbFile(regionCode)

            // --- Téléchargement (avec gestion manuelle des redirections) ---
            // GitHub Releases redirige vers un CDN (objects.githubusercontent.com).
            // HttpURLConnection ne suit PAS automatiquement une redirection qui
            // change de domaine/protocole, donc on la suit nous-mêmes.
            var currentUrl = releaseUrl(regionCode)
            var connection: HttpURLConnection
            var redirects = 0
            while (true) {
                connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 30_000
                    readTimeout = 60_000
                    instanceFollowRedirects = false
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "BBS-mOOve")
                }
                val code = connection.responseCode
                if (code in listOf(301, 302, 303, 307, 308)) {
                    val location = connection.getHeaderField("Location")
                    connection.disconnect()
                    if (location == null || redirects++ > 5) {
                        _state.value = State.Error("Trop de redirections")
                        return@withContext
                    }
                    currentUrl = location
                    continue
                }
                if (code !in 200..299) {
                    _state.value = State.Error("HTTP $code")
                    connection.disconnect()
                    return@withContext
                }
                break
            }

            val totalBytes = connection.contentLengthLong
            var downloaded = 0L

            connection.inputStream.use { input ->
                gzTmp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (totalBytes > 0) {
                            val pct = (downloaded * 100 / totalBytes).toInt()
                            _state.value = State.Downloading(pct)
                        }
                    }
                }
            }

            // --- Décompression ---
            _state.value = State.Decompressing
            GZIPInputStream(gzTmp.inputStream().buffered()).use { gz ->
                dbTmp.outputStream().use { out -> gz.copyTo(out, 64 * 1024) }
            }

            // --- Validation minimale + bascule atomique ---
            if (dbTmp.length() < 10_000) {
                _state.value = State.Error("Base décompressée invalide")
                gzTmp.delete(); dbTmp.delete()
                return@withContext
            }
            // rename atomique : la base n'apparaît "prête" que complète
            if (dbFinal.exists()) dbFinal.delete()
            dbTmp.renameTo(dbFinal)
            gzTmp.delete()

            _state.value = State.Ready(regionCode)
        } catch (e: Exception) {
            _state.value = State.Error(e.message ?: "Erreur de téléchargement")
        }
    }

    /** Supprime la base d'une région (pour gestion du cache plus tard). */
    fun delete(regionCode: String) {
        localDbFile(regionCode).delete()
    }
}
