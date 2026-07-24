package com.blacksamdev.bbsmoove.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blacksamdev.bbsmoove.data.DangerZoneRepository
import com.blacksamdev.bbsmoove.data.RegionDownloadManager
import com.blacksamdev.bbsmoove.data.RoadLookupRepository
import com.blacksamdev.bbsmoove.data.Settings
import com.blacksamdev.bbsmoove.data.SettingsRepository
import com.blacksamdev.bbsmoove.data.UpdateChecker
import com.blacksamdev.bbsmoove.model.DangerZoneInfo
import com.blacksamdev.bbsmoove.model.GpsFix
import com.blacksamdev.bbsmoove.model.NowPlaying
import com.blacksamdev.bbsmoove.model.RoadInfo
import com.blacksamdev.bbsmoove.model.SpeedState
import com.blacksamdev.bbsmoove.service.AlertSoundPlayer
import com.blacksamdev.bbsmoove.service.AudioDuckingManager
import com.blacksamdev.bbsmoove.service.LocationTrackingService
import com.blacksamdev.bbsmoove.service.MediaSessionMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HudUiState(
    val speedKmh: Int = 0,
    val limitKmh: Int = 50,
    val speedState: SpeedState = SpeedState.OK,
    val accuracyM: Float = 0f,
    val dangerInfo: DangerZoneInfo? = null,
    val nowPlaying: NowPlaying? = null,
    val avgSpeedKmh: Int = 0,
    val maxSpeedKmh: Int = 0,
    val tripDurationSec: Int = 0,
    /** Id (base locale) du segment retenu par le map-matching — diagnostic. */
    val segmentId: Long? = null,
    /** Id OSM de la way retenue — permet d'ouvrir l'objet à corriger. */
    val osmWayId: Long? = null,
)

class HudViewModel(application: Application) : AndroidViewModel(application) {

    private val roadRepo = RoadLookupRepository(application)
    private val dangerRepo = DangerZoneRepository(application)
    private val mediaMonitor = MediaSessionMonitor(application)
    private val audioDucking = AudioDuckingManager(application)
    private val soundPlayer = AlertSoundPlayer(application)
    private val regionDownloader = RegionDownloadManager(application)
    private val settingsRepo = SettingsRepository(application)
    private val updateChecker = UpdateChecker()

    /** Réglages courants, observés par l'UI (panneau d'options + HUD). */
    val settings: StateFlow<Settings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    /** Accès au repository pour le panneau d'options. */
    fun settingsRepository(): SettingsRepository = settingsRepo

    /** État du téléchargement de région, observé par l'UI (bouton + progression). */
    val downloadState = regionDownloader.state

    // Région fixée pour ce premier jet (détection auto à venir).
    private val currentRegionCode = "bourgogne"

    private val _roadInfo = MutableStateFlow<RoadInfo?>(null)
    private val _dangerInfo = MutableStateFlow<DangerZoneInfo?>(null)

    /** Dernière erreur de lookup, affichée en debug dans le HUD (diagnostic sans adb). */
    private val _lookupError = MutableStateFlow<String?>(null)
    val lookupError = _lookupError.asStateFlow()

    /** Base routes réellement chargée (diagnostic HUD). */
    fun roadDbSource(): String = roadRepo.activeSource()

    /** Base radars réellement chargée (diagnostic HUD). */
    fun radarDbSource(): String = dangerRepo.activeSource

    /**
     * False tant que l'accès aux notifications n'est pas accordé : sans lui,
     * AUCUN lecteur (Groove, Spotify...) ne peut être détecté.
     */
    val mediaPermissionGranted = mediaMonitor.permissionGranted

    /** À appeler quand l'app revient au premier plan (la permission a pu changer). */
    fun onResume() {
        mediaMonitor.refresh()
    }

    // --- Commandes de lecture (boutons du panneau média) ---
    fun mediaPlayPause() = mediaMonitor.playPause()
    fun mediaNext() = mediaMonitor.skipNext()
    fun mediaPrevious() = mediaMonitor.skipPrevious()

    private var lastState: SpeedState? = null
    private var lastSegmentId: Long? = null
    private var wasAlerting = false
    private val tripStartMs = System.currentTimeMillis()
    private var speedSum = 0L
    private var speedSamples = 0L
    private var maxSpeed = 0

    /** Déclenché par le bouton "Télécharger ma région". */
    fun downloadCurrentRegion() {
        viewModelScope.launch {
            // Option "téléchargement en WiFi uniquement"
            if (settings.value.wifiOnlyDownload && !isOnWifi()) {
                regionDownloader.reportError("WiFi requis (option activée)")
                return@launch
            }
            regionDownloader.download(currentRegionCode)
            afterSuccessfulDownload()
        }
    }

    /** Déclenché par la pastille "Mise à jour disponible". */
    fun downloadUpdate() {
        viewModelScope.launch {
            if (settings.value.wifiOnlyDownload && !isOnWifi()) {
                regionDownloader.reportError("WiFi requis (option activée)")
                return@launch
            }
            regionDownloader.download(currentRegionCode, force = true)
            afterSuccessfulDownload()
        }
    }

    /** Recharge les bases et enregistre la version installée. */
    private suspend fun afterSuccessfulDownload() {
        if (regionDownloader.isAvailable(currentRegionCode)) {
            roadRepo.reload()
        }
        if (regionDownloader.isRadarsAvailable()) {
            dangerRepo.reload()
        }
        updateChecker.fetchRemoteVersion()?.let { settingsRepo.setDataVersion(it) }
    }

    /**
     * Check quotidien de mise à jour des cartes (au démarrage, en fond).
     * Règle décidée : si l'option WiFi-only est ACTIVE et qu'on est en WiFi,
     * la mise à jour se télécharge automatiquement ; dans tous les autres
     * cas, une pastille propose la mise à jour à l'utilisateur.
     */
    private fun maybeCheckForUpdate() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Pas de bases locales = rien à mettre à jour (le bouton de
                // premier téléchargement gère ce cas).
                if (!regionDownloader.isAvailable(currentRegionCode)) return@launch

                // Cooldown : au plus un check par 24 h.
                val now = System.currentTimeMillis()
                if (now - settingsRepo.lastUpdateCheckMs() < 24 * 3600 * 1000L) return@launch
                settingsRepo.setLastUpdateCheckMs(now)

                val remote = updateChecker.fetchRemoteVersion() ?: return@launch
                val local = settingsRepo.dataVersion()

                if (local == null) {
                    // Bases présentes mais version inconnue (installées avant
                    // cette fonctionnalité) : on adopte la version distante
                    // comme référence, sans re-télécharger.
                    settingsRepo.setDataVersion(remote)
                    return@launch
                }

                if (remote != local) {
                    if (settings.value.wifiOnlyDownload && isOnWifi()) {
                        // Auto-update silencieux en WiFi (règle de Michael).
                        regionDownloader.download(currentRegionCode, force = true)
                        afterSuccessfulDownload()
                    } else {
                        regionDownloader.markUpdateAvailable()
                    }
                }
            } catch (e: Exception) {
                // Un échec de check ne doit jamais perturber l'app.
                android.util.Log.e("BBSmOOve", "check MAJ échoué", e)
            }
        }
    }

    private fun isOnWifi(): Boolean {
        val cm = getApplication<Application>()
            .getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
            as android.net.ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
    }

    /** Vrai si la base régionale est déjà présente (pour masquer le bouton). */
    fun isRegionAvailable(): Boolean = regionDownloader.isAvailable(currentRegionCode)

    val uiState: StateFlow<HudUiState> = combine(
        LocationTrackingService.gpsFix,
        _roadInfo,
        _dangerInfo,
        mediaMonitor.nowPlaying,
    ) { fix, road, danger, media ->
        buildState(fix, road, danger, media)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HudUiState())

    init {
        mediaMonitor.start()

        // Si les bases ont déjà été téléchargées lors d'une session
        // précédente, on recharge les lookups dessus et on masque le bouton.
        regionDownloader.markReadyIfAvailable(currentRegionCode)
        if (regionDownloader.isAvailable(currentRegionCode)) {
            roadRepo.reload()
        }
        if (regionDownloader.isRadarsAvailable()) {
            dangerRepo.reload()
        }

        // Check quotidien de mise à jour des cartes (en fond, non bloquant).
        maybeCheckForUpdate()

        viewModelScope.launch {
            LocationTrackingService.gpsFix.collect { fix ->
                if (fix == null) return@collect
                launch(Dispatchers.Default) {
                    try {
                        val road = roadRepo.lookup(
                            lat = fix.lat,
                            lon = fix.lon,
                            heading = fix.headingDeg,
                            accuracyM = fix.accuracyM,
                            prevSegmentId = lastSegmentId,
                        )
                        _roadInfo.value = road
                        _lookupError.value = null
                        // Mémorise le tronçon pour le bonus de continuité au tick suivant
                        if (road?.segmentId != null) lastSegmentId = road.segmentId
                    } catch (e: Exception) {
                        // Un échec de lookup (base, Python, données) ne doit
                        // JAMAIS faire tomber l'app : on log et on garde la
                        // dernière limite connue.
                        android.util.Log.e("BBSmOOve", "lookup route échoué", e)
                        _lookupError.value = "ROUTE: ${e.javaClass.simpleName}: ${e.message?.take(120)}"
                    }
                }
                launch(Dispatchers.Default) {
                    try {
                        val danger = dangerRepo.lookup(
                            fix.lat, fix.lon,
                            alertDistanceM = settings.value.dangerDistanceM,
                        )
                        _dangerInfo.value = danger
                        handleDangerTransition(danger)
                    } catch (e: Exception) {
                        android.util.Log.e("BBSmOOve", "lookup radar échoué", e)
                        _lookupError.value = "RADAR: ${e.javaClass.simpleName}: ${e.message?.take(120)}"
                    }
                }
                recordStat(fix.speedKmh)
            }
        }
    }

    private fun recordStat(speed: Int) {
        speedSum += speed
        speedSamples += 1
        if (speed > maxSpeed) maxSpeed = speed
    }

    private fun buildState(
        fix: GpsFix?,
        road: RoadInfo?,
        danger: DangerZoneInfo?,
        media: NowPlaying?,
    ): HudUiState {
        val speed = fix?.speedKmh ?: 0
        val limit = road?.limitKmh ?: 50
        val s = settings.value
        val state = SpeedState.from(
            speed, limit,
            orangeAt = s.orangeThresholdKmh,
            redAt = s.redThresholdKmh,
        )

        if (lastState != state) {
            // Le player décide du son selon le SENS (montée = alerte,
            // descente = son doux), filtré par les toggles des options,
            // et reste muet au tout premier passage (démarrage).
            soundPlayer.playTransition(
                prev = lastState, next = state,
                soundGreen = s.soundGreen,
                soundOrange = s.soundOrange,
                soundRed = s.soundRed,
            )
        }
        lastState = state

        val avg = if (speedSamples > 0) (speedSum / speedSamples).toInt() else 0
        val durationSec = ((System.currentTimeMillis() - tripStartMs) / 1000).toInt()

        return HudUiState(
            speedKmh = speed,
            limitKmh = limit,
            speedState = state,
            accuracyM = fix?.accuracyM ?: 0f,
            dangerInfo = danger,
            nowPlaying = media,
            avgSpeedKmh = avg,
            maxSpeedKmh = maxSpeed,
            tripDurationSec = durationSec,
            segmentId = road?.segmentId,
            osmWayId = road?.osmWayId,
        )
    }

    /** Ducking audio + son d'alerte synchronisés avec l'entrée/sortie de zone. */
    private fun handleDangerTransition(danger: DangerZoneInfo?) {
        val alerting = danger?.shouldAlert == true
        if (alerting && !wasAlerting) {
            audioDucking.startDucking()
            // Son d'alerte à l'ENTRÉE en zone (une seule fois), si activé.
            if (settings.value.soundDanger) {
                soundPlayer.playDangerAlert()
            }
        } else if (!alerting && wasAlerting) {
            audioDucking.stopDucking()
        }
        wasAlerting = alerting
    }

    override fun onCleared() {
        mediaMonitor.stop()
        audioDucking.stopDucking()
        soundPlayer.release()
        roadRepo.close()
        dangerRepo.close()
        super.onCleared()
    }
}
