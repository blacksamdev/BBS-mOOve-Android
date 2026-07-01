package com.blacksamdev.bbsmoove.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blacksamdev.bbsmoove.data.DangerZoneRepository
import com.blacksamdev.bbsmoove.data.RoadLookupRepository
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
)

class HudViewModel(application: Application) : AndroidViewModel(application) {

    private val roadRepo = RoadLookupRepository(application)
    private val dangerRepo = DangerZoneRepository(application)
    private val mediaMonitor = MediaSessionMonitor(application)
    private val audioDucking = AudioDuckingManager(application)
    private val soundPlayer = AlertSoundPlayer()
    private val regionDownloader = com.blacksamdev.bbsmoove.data.RegionDownloadManager(application)

    /** État du téléchargement de région, observé par l'UI (bouton + progression). */
    val downloadState = regionDownloader.state

    // Région fixée pour ce premier jet (détection auto à venir).
    private val currentRegionCode = "bourgogne"

    private val _roadInfo = MutableStateFlow<RoadInfo?>(null)
    private val _dangerInfo = MutableStateFlow<DangerZoneInfo?>(null)

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
            regionDownloader.download(currentRegionCode)
            // Bascule les lookups sur les vraies bases sans redémarrer l'app.
            if (regionDownloader.isAvailable(currentRegionCode)) {
                roadRepo.reload()
            }
            if (regionDownloader.isRadarsAvailable()) {
                dangerRepo.reload()
            }
        }
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

        viewModelScope.launch {
            LocationTrackingService.gpsFix.collect { fix ->
                if (fix == null) return@collect
                launch(Dispatchers.Default) {
                    val road = roadRepo.lookup(
                        lat = fix.lat,
                        lon = fix.lon,
                        heading = fix.headingDeg,
                        accuracyM = fix.accuracyM,
                        prevSegmentId = lastSegmentId,
                    )
                    _roadInfo.value = road
                    // Mémorise le tronçon pour le bonus de continuité au tick suivant
                    if (road?.segmentId != null) lastSegmentId = road.segmentId
                }
                launch(Dispatchers.Default) {
                    val danger = dangerRepo.lookup(fix.lat, fix.lon)
                    _dangerInfo.value = danger
                    handleDangerTransition(danger)
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
        val state = SpeedState.from(speed, limit)

        if (lastState != null && lastState != state) {
            soundPlayer.playFor(state)
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
        )
    }

    /** Ducking audio synchronisé avec l'entrée/sortie de la zone de danger. */
    private fun handleDangerTransition(danger: DangerZoneInfo?) {
        val alerting = danger?.shouldAlert == true
        if (alerting && !wasAlerting) {
            audioDucking.startDucking()
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
