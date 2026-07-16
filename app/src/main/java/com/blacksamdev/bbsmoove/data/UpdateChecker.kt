package com.blacksamdev.bbsmoove.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Détecte la version des données publiées dans la Release "osm-data-latest"
 * (régénérée chaque mois par le workflow CI).
 *
 * Méthode : une requête HEAD sur le fichier radars.db.gz de la Release, dont
 * on lit l'en-tête Last-Modified. Ce fichier est republié à chaque run du
 * workflow, donc sa date = la version des données.
 *
 * Avantages sur l'API GitHub REST : aucun rate limit (c'est le CDN de
 * téléchargement, pas l'API), pas de JSON, requête minuscule (pas de corps).
 */
class UpdateChecker {

    private val probeUrl =
        "https://github.com/blacksamdev/BBS-mOOve-Android/releases/download/osm-data-latest/radars.db.gz"

    /**
     * Renvoie la version distante (valeur brute du Last-Modified), ou null
     * si indisponible. Ne lève jamais : un échec de check ne doit pas
     * perturber l'app.
     */
    suspend fun fetchRemoteVersion(): String? = withContext(Dispatchers.IO) {
        try {
            // Suivi manuel des redirections GitHub -> CDN (HttpURLConnection
            // ne suit pas les redirections cross-domain), comme pour le
            // téléchargement.
            var currentUrl = probeUrl
            var redirects = 0
            var result: String? = null
            while (redirects <= 5) {
                val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 15_000
                    instanceFollowRedirects = false
                    requestMethod = "HEAD"
                    setRequestProperty("User-Agent", "BBS-mOOve")
                }
                val code = connection.responseCode
                if (code in listOf(301, 302, 303, 307, 308)) {
                    val location = connection.getHeaderField("Location")
                    connection.disconnect()
                    if (location == null) break
                    currentUrl = location
                    redirects++
                    continue
                }
                if (code == 200) {
                    result = connection.getHeaderField("Last-Modified")
                }
                connection.disconnect()
                break
            }
            result
        } catch (e: Exception) {
            null
        }
    }
}
