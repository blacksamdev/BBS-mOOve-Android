package com.blacksamdev.bbsmoove.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import com.blacksamdev.bbsmoove.data.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Lecture de la consommation via un boîtier ELM327 Bluetooth classique.
 *
 * Tourne sur son propre fil (Dispatchers.IO) tant que l'option est active
 * et qu'un boîtier est choisi. En cas de coupure (contact coupé, boîtier
 * débranché, Bluetooth éteint), il réessaie tout seul toutes les 5 s.
 *
 * Le carburant compté depuis le dernier plein est sauvegardé régulièrement :
 * il sert à l'étalonnage au plein suivant.
 */
class ObdManager(
    private val context: Context,
    private val settingsRepo: SettingsRepository,
    private val params: () -> ObdParams,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _status = MutableStateFlow<ObdStatus>(ObdStatus.Off)
    val status: StateFlow<ObdStatus> = _status.asStateFlow()

    private val _reading = MutableStateFlow<ObdReading?>(null)
    val reading: StateFlow<ObdReading?> = _reading.asStateFlow()

    private var job: Job? = null
    private var currentAddress: String? = null
    @Volatile
    private var client: Elm327Client? = null

    // Cumuls du trajet : remis à zéro au lancement de l'app.
    private var tripLiters = 0.0
    private var tripKm = 0.0
    // Cumul depuis le dernier plein : persistant.
    private var tankLiters = 0.0
    private var tankLoaded = false
    private var smoothedLh: Double? = null

    /** Démarre, redémarre ou arrête la lecture selon les réglages. */
    fun configure(enabled: Boolean, address: String?) {
        if (!enabled || address.isNullOrBlank()) {
            stop()
            return
        }
        if (job?.isActive == true && address == currentAddress) return
        stop()
        currentAddress = address
        job = scope.launch { runLoop(address) }
    }

    fun stop() {
        job?.cancel()
        job = null
        // Fermer la liaison interrompt une lecture en cours sur l'autre fil.
        client?.close()
        client = null
        currentAddress = null
        // La sauvegarde du compteur de réservoir est faite par le finally de
        // runLoop, qui s'exécute à l'annulation.
        _status.value = ObdStatus.Off
    }

    fun release() {
        stop()
        scope.cancel()
    }

    /**
     * Étalonnage au plein : compare les litres mis à la pompe avec ceux
     * comptés depuis le plein précédent, ajuste le facteur, puis repart de
     * zéro pour le réservoir suivant. Renvoie un message pour l'utilisateur.
     */
    suspend fun calibrateWithFill(realLiters: Double): String {
        ensureTankLoaded()
        val counted = tankLiters
        if (counted < 5.0) {
            return "Pas assez de carburant compté depuis le dernier plein " +
                "(${fmt1(counted)} L, il en faut au moins 5)."
        }
        val current = params().calibration
        val updated = FuelMath.calibratedFactor(current, counted, realLiters)
        settingsRepo.setObdCalibration(updated)
        resetTank()
        return "Étalonnage : ${fmt1(counted)} L comptés pour ${fmt1(realLiters)} L réels. " +
            "Facteur ${(current * 100).toInt()} % → ${(updated * 100).toInt()} %."
    }

    /** Plein fait sans étalonner : le compteur du réservoir repart de zéro. */
    suspend fun resetTank() {
        tankLiters = 0.0
        tankLoaded = true
        settingsRepo.setTankLiters(0.0)
        _reading.value = _reading.value?.copy(tankLiters = 0.0)
    }

    private suspend fun ensureTankLoaded() {
        if (!tankLoaded) {
            tankLiters = settingsRepo.tankLiters()
            tankLoaded = true
        }
    }

    // --- Boucle principale -------------------------------------------------

    private suspend fun runLoop(address: String) {
        ensureTankLoaded()
        // Affiche tout de suite le compteur du réservoir dans les options,
        // même avant que la voiture réponde (utile à la pompe, moteur coupé).
        if (_reading.value == null) _reading.value = ObdReading(tankLiters = tankLiters)
        while (coroutineContext.isActive) {
            try {
                session(address)
            } catch (e: CancellationException) {
                throw e
            } catch (e: NoAnswerException) {
                if (coroutineContext.isActive) {
                    _status.value = ObdStatus.WaitingEngine(
                        "boîtier joint, la voiture ne répond pas (contact coupé ?)",
                    )
                }
            } catch (e: Exception) {
                // Après un arrêt volontaire, la fermeture de la liaison fait
                // échouer la lecture en cours : ce n'est pas une vraie erreur.
                if (coroutineContext.isActive) {
                    Log.w(TAG, "session OBD interrompue", e)
                    _status.value = ObdStatus.Error(e.message ?: e.javaClass.simpleName)
                }
            } finally {
                client?.close()
                client = null
                // NonCancellable : la sauvegarde doit aboutir même si la
                // boucle vient d'être annulée.
                withContext(NonCancellable) { settingsRepo.setTankLiters(tankLiters) }
            }
            delay(5_000)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun session(address: String) {
        _status.value = ObdStatus.Connecting
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = manager.adapter ?: throw IllegalStateException("pas de Bluetooth sur ce téléphone")
        if (!adapter.isEnabled) throw IllegalStateException("Bluetooth désactivé")
        val device = adapter.getRemoteDevice(address)

        val elm = Elm327Client(adapter, device)
        client = elm
        elm.connect()

        // Initialisation : réinitialisation, pas d'écho, pas d'espaces ni
        // d'en-têtes, temporisation adaptative, protocole automatique.
        elm.send("ATZ", timeoutMs = 3_000)
        for (cmd in listOf("ATE0", "ATL0", "ATS0", "ATH0", "ATAT1", "ATSP0")) {
            elm.send(cmd)
        }

        // Recherche du protocole + liste des PID pris en charge. La première
        // requête peut prendre plusieurs secondes (« SEARCHING... »).
        val supported = readSupportedPids(elm)
        if (supported.isEmpty()) throw NoAnswerException()

        val method = when {
            0x5E in supported -> FuelMethod.FUEL_RATE
            0x10 in supported -> FuelMethod.MAF
            0x0B in supported && 0x0C in supported -> FuelMethod.MAP
            else -> throw IllegalStateException(
                "la voiture ne fournit ni débit carburant, ni débit d'air, ni pression d'admission",
            )
        }
        _status.value = ObdStatus.Running(method)

        var lastMs = System.currentTimeMillis()
        var lastSaveMs = lastMs
        var cycle = 0
        var failures = 0
        var stft: Double? = null
        var ltft: Double? = null
        var coolant: Int? = null

        while (coroutineContext.isActive) {
            val speed = read(elm, 0x0D)?.let { it[0].toDouble() }
            val rpmBytes = read(elm, 0x0C)
            val rpm = rpmBytes?.let { (it[0] * 256 + it.getOrElse(1) { 0 }) / 4.0 }

            if (speed == null && rpm == null) {
                // Plus aucune réponse : moteur coupé, ou liaison perdue.
                failures++
                if (failures >= 3) throw NoAnswerException()
                delay(500)
                continue
            }
            failures = 0

            // Trims et température d'eau : ils varient lentement, inutile de
            // les relire à chaque cycle (le boîtier est lent).
            if (cycle % 3 == 0) {
                if (0x06 in supported) stft = read(elm, 0x06)?.let { (it[0] - 128) * 100.0 / 128.0 }
                if (0x07 in supported) ltft = read(elm, 0x07)?.let { (it[0] - 128) * 100.0 / 128.0 }
            }
            if (cycle % 10 == 0 && 0x05 in supported) {
                coolant = read(elm, 0x05)?.let { it[0] - 40 }
            }

            val rawLh: Double? = if (isFuelCut(elm, supported, rpm)) {
                0.0
            } else {
                when (method) {
                    FuelMethod.FUEL_RATE -> read(elm, 0x5E)?.let { (it[0] * 256 + it.getOrElse(1) { 0 }) / 20.0 }
                    FuelMethod.MAF -> read(elm, 0x10)?.let {
                        val airGs = (it[0] * 256 + it.getOrElse(1) { 0 }) / 100.0
                        FuelMath.litersPerHourFromAir(airGs, FuelMath.trimFactor(stft, ltft))
                    }
                    FuelMethod.MAP -> {
                        val map = read(elm, 0x0B)?.let { it[0].toDouble() }
                        val iat = if (0x0F in supported) read(elm, 0x0F)?.let { it[0] - 40.0 } else null
                        if (map != null && rpm != null) {
                            val airGs = FuelMath.airFromMap(
                                mapKpa = map,
                                intakeTempC = iat ?: 25.0,
                                rpm = rpm,
                                displacementLiters = params().displacementCc / 1000.0,
                            )
                            FuelMath.litersPerHourFromAir(airGs, FuelMath.trimFactor(stft, ltft))
                        } else {
                            null
                        }
                    }
                }
            }

            val now = System.currentTimeMillis()
            val dtSec = ((now - lastMs) / 1000.0).coerceIn(0.0, 5.0)
            lastMs = now

            if (rawLh != null) {
                val lh = rawLh * params().calibration
                val liters = lh * dtSec / 3600.0
                tripLiters += liters
                tankLiters += liters
                // Lissage léger : l'instantané brut saute trop pour être lu en roulant.
                val prev = smoothedLh
                smoothedLh = if (prev == null) lh else prev + 0.35 * (lh - prev)
            }
            if (speed != null) tripKm += speed * dtSec / 3600.0

            val shown = smoothedLh
            _reading.value = ObdReading(
                instantLh = shown,
                instantL100 = shown?.let { FuelMath.litersPer100Km(it, speed ?: 0.0) },
                avgL100 = if (tripKm >= 0.5) tripLiters * 100.0 / tripKm else null,
                tripLiters = tripLiters,
                tripKm = tripKm,
                tankLiters = tankLiters,
                rpm = rpm?.toInt(),
                coolantC = coolant,
            )

            if (now - lastSaveMs > 30_000) {
                settingsRepo.setTankLiters(tankLiters)
                lastSaveMs = now
            }
            cycle++
        }
    }

    /**
     * Coupure d'injection en décélération (pied levé, régime élevé) : le
     * moteur ne consomme rien, mais la méthode MAP/MAF verrait encore de
     * l'air passer. Le PID 03 signale « boucle ouverte » dans ce cas ; la
     * charge moteur faible le distingue de la pleine charge.
     */
    private fun isFuelCut(elm: Elm327Client, supported: Set<Int>, rpm: Double?): Boolean {
        if (0x03 !in supported || 0x04 !in supported) return false
        if (rpm == null || rpm < 1_100) return false
        val fuelStatus = read(elm, 0x03)?.let { it[0] } ?: return false
        if ((fuelStatus and 0x04) == 0) return false
        val load = read(elm, 0x04)?.let { it[0] * 100.0 / 255.0 } ?: return false
        return load < 20.0
    }

    /** Lit un PID en mode 01. Renvoie null si la voiture ne répond pas. */
    private fun read(elm: Elm327Client, pid: Int): IntArray? {
        val response = try {
            elm.send("01" + "%02X".format(pid))
        } catch (e: ElmTimeoutException) {
            return null
        }
        if (Elm327Client.isNoAnswer(response)) return null
        return Elm327Client.pidBytes(response, pid)
    }

    /**
     * PID pris en charge : le PID 00 renvoie un masque des PID 01 à 20, le
     * 20 ceux de 21 à 40, le 40 ceux de 41 à 60. On fusionne les réponses de
     * tous les calculateurs.
     */
    private fun readSupportedPids(elm: Elm327Client): Set<Int> {
        val supported = mutableSetOf<Int>()
        for (base in listOf(0x00, 0x20, 0x40)) {
            if (base != 0x00 && base !in supported) break
            val response = try {
                elm.send("01" + "%02X".format(base), timeoutMs = 10_000)
            } catch (e: ElmTimeoutException) {
                break
            }
            if (Elm327Client.isNoAnswer(response)) break
            for (bytes in Elm327Client.allPidBytes(response, base)) {
                for (byteIndex in 0 until minOf(4, bytes.size)) {
                    for (bit in 0 until 8) {
                        if ((bytes[byteIndex] and (0x80 shr bit)) != 0) {
                            supported.add(base + byteIndex * 8 + bit + 1)
                        }
                    }
                }
            }
        }
        return supported
    }

    private class NoAnswerException : Exception()

    companion object {
        private const val TAG = "BBSmOOve-OBD"

        fun fmt1(value: Double): String = "%.1f".format(value).replace('.', ',')
    }
}
