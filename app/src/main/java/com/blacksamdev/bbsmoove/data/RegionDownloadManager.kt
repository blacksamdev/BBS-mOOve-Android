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

    /** Chemin local de la base radars (France entière, une seule). */
    fun radarsDbFile(): File = File(regionsDir, "radars.db")

    /** Vrai si la base de cette région est déjà téléchargée et prête. */
    fun isAvailable(regionCode: String): Boolean = localDbFile(regionCode).exists()

    /** Vrai si la base radars est déjà téléchargée. */
    fun isRadarsAvailable(): Boolean = radarsDbFile().exists()

    private fun releaseUrl(fileName: String): String =
        "https://github.com/blacksamdev/BBS-mOOve-Android/releases/download/osm-data-latest/$fileName"

    /**
     * Télécharge la région (routes) ET les radars (France entière), puis
     * décompresse. À appeler depuis une coroutine.
     *
     * Les radars sont un seul petit fichier national ; la région est le gros
     * morceau (~20 Mo). On télécharge d'abord la région (progression
     * visible), puis les radars en fin de course.
     */
    suspend fun download(regionCode: String) = withContext(Dispatchers.IO) {
        try {
            // 1. Routes de la région
            if (!isAvailable(regionCode)) {
                val ok = downloadAndDecompress(
                    fileName = "$regionCode.db.gz",
                    finalFile = localDbFile(regionCode),
                    reportProgress = true,
                )
                if (!ok) return@withContext
            }

            // 2. Radars (France entière) — petit fichier, on le (re)prend si absent
            if (!isRadarsAvailable()) {
                _state.value = State.Decompressing // libellé générique "préparation"
                val ok = downloadAndDecompress(
                    fileName = "radars.db.gz",
                    finalFile = radarsDbFile(),
                    reportProgress = false,
                    minValidBytes = 1_000, // la base radars est petite
                )
                if (!ok) return@withContext
            }

            _state.value = State.Ready(regionCode)
        } catch (e: Exception) {
            _state.value = State.Error(e.message ?: "Erreur de téléchargement")
        }
    }

    /**
     * Télécharge un .gz depuis la Release, le décompresse dans finalFile
     * (bascule atomique). Renvoie true si succès. En cas d'échec, met à jour
     * _state en Error et renvoie false.
     */
    private fun downloadAndDecompress(
        fileName: String,
        finalFile: File,
        reportProgress: Boolean,
        minValidBytes: Long = 10_000,
    ): Boolean {
        val gzTmp = File(regionsDir, "$fileName.tmp")
        val dbTmp = File(finalFile.path + ".tmp")

        // --- Téléchargement (gestion manuelle des redirections GitHub->CDN) ---
        var currentUrl = releaseUrl(fileName)
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
                    return false
                }
                currentUrl = location
                continue
            }
            if (code !in 200..299) {
                _state.value = State.Error("HTTP $code ($fileName)")
                connection.disconnect()
                return false
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
                    if (reportProgress && totalBytes > 0) {
                        _state.value = State.Downloading((downloaded * 100 / totalBytes).toInt())
                    }
                }
            }
        }

        // --- Décompression ---
        _state.value = State.Decompressing
        GZIPInputStream(gzTmp.inputStream().buffered()).use { gz ->
            dbTmp.outputStream().use { out -> gz.copyTo(out, 64 * 1024) }
        }

        if (dbTmp.length() < minValidBytes) {
            _state.value = State.Error("Base décompressée invalide ($fileName)")
            gzTmp.delete(); dbTmp.delete()
            return false
        }

        if (finalFile.exists()) finalFile.delete()
        dbTmp.renameTo(finalFile)
        gzTmp.delete()
        return true
    }

    /** Supprime la base d'une région (pour gestion du cache plus tard). */
    fun delete(regionCode: String) {
        localDbFile(regionCode).delete()
    }
}
