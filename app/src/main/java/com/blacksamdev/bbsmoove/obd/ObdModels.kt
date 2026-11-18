package com.blacksamdev.bbsmoove.obd

/** Méthode de calcul de la consommation retenue pour cette voiture. */
enum class FuelMethod(val label: String) {
    FUEL_RATE("débit carburant lu directement"),
    MAF("débitmètre d'air"),
    MAP("estimation par pression d'admission"),
}

/** État de la liaison avec le boîtier OBD. */
sealed interface ObdStatus {
    /** Lecture désactivée dans les options, ou aucun boîtier choisi. */
    data object Off : ObdStatus

    /** Connexion Bluetooth en cours. */
    data object Connecting : ObdStatus

    /** Boîtier joint, mais la voiture ne répond pas (contact coupé). */
    data class WaitingEngine(val message: String) : ObdStatus

    /** Lecture en cours. */
    data class Running(val method: FuelMethod) : ObdStatus

    /** Échec : nouvel essai automatique quelques secondes plus tard. */
    data class Error(val message: String) : ObdStatus
}

/** Texte court de l'état, pour le panneau d'options. */
fun ObdStatus.label(): String = when (this) {
    ObdStatus.Off -> "désactivé"
    ObdStatus.Connecting -> "connexion au boîtier…"
    is ObdStatus.WaitingEngine -> message
    is ObdStatus.Running -> "connecté — ${method.label}"
    is ObdStatus.Error -> "erreur : $message (nouvel essai dans 5 s)"
}

/** Dernières valeurs lues et calculées. */
data class ObdReading(
    /** Consommation instantanée en L/h (lissée). */
    val instantLh: Double? = null,
    /** Consommation instantanée en L/100 km, null sous 5 km/h. */
    val instantL100: Double? = null,
    /** Moyenne du trajet en L/100 km, null tant que la distance est trop courte. */
    val avgL100: Double? = null,
    /** Carburant consommé depuis le début du trajet (L). */
    val tripLiters: Double = 0.0,
    /** Distance du trajet selon la voiture (km). */
    val tripKm: Double = 0.0,
    /** Carburant compté depuis le dernier plein (L), pour l'étalonnage. */
    val tankLiters: Double = 0.0,
    val rpm: Int? = null,
    val coolantC: Int? = null,
    /** Valeurs brutes lues au dernier cycle, affichées dans les options pour le diagnostic. */
    val debug: String? = null,
)

/** Appareil Bluetooth déjà appairé, proposé dans les options. */
data class BtDeviceInfo(val name: String, val address: String)

/** Paramètres lus à chaque cycle depuis les réglages. */
data class ObdParams(
    val displacementCc: Int,
    val calibration: Double,
)
