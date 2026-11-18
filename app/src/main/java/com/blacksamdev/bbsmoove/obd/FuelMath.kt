package com.blacksamdev.bbsmoove.obd

/**
 * Calculs de consommation à partir des données OBD-II.
 *
 * Trois méthodes, de la plus précise à la plus approximative :
 *  1. PID 5E (débit carburant) : la voiture donne directement des L/h.
 *     Rare sur les voitures essence grand public.
 *  2. PID 10 (débitmètre d'air, MAF) : la masse d'air aspirée est mesurée ;
 *     on en déduit le carburant par le rapport air/essence. ±5 % environ.
 *  3. Pression d'admission (MAP) + régime + température d'air : la masse
 *     d'air est ESTIMÉE (méthode « speed-density »). Dépend de la cylindrée
 *     et d'un rendement volumétrique supposé. ±10-15 % avant étalonnage.
 *
 * Dans tous les cas, le facteur d'étalonnage (réglé au plein) corrige
 * l'écart restant.
 */
object FuelMath {

    /** Rapport stœchiométrique air/essence (masse d'air / masse d'essence). */
    const val AFR_GASOLINE = 14.7

    /** Masse volumique de l'essence, en g/L. */
    const val GASOLINE_DENSITY_G_PER_L = 745.0

    /**
     * Rendement volumétrique supposé pour la méthode MAP : part du volume
     * des cylindres réellement remplie à chaque cycle. 0,85 est une valeur
     * moyenne pour un petit moteur atmosphérique ; l'étalonnage au plein
     * corrige l'écart réel.
     */
    const val DEFAULT_VOLUMETRIC_EFFICIENCY = 0.85

    private const val AIR_MOLAR_MASS_G_PER_MOL = 28.965
    private const val GAS_CONSTANT = 8.314

    /**
     * Facteur de correction des trims carburant (court et long terme, en %).
     * Un trim de +5 % signifie que le calculateur injecte 5 % de plus que sa
     * cartographie de base : on suit ce qu'il injecte réellement.
     */
    fun trimFactor(shortTermPct: Double?, longTermPct: Double?): Double =
        1.0 + ((shortTermPct ?: 0.0) + (longTermPct ?: 0.0)) / 100.0

    /** Débit carburant (L/h) à partir d'un débit d'air (g/s). */
    fun litersPerHourFromAir(airGramsPerSec: Double, trimFactor: Double): Double =
        airGramsPerSec / AFR_GASOLINE * trimFactor * 3600.0 / GASOLINE_DENSITY_G_PER_L

    /**
     * Débit d'air estimé (g/s) par la méthode speed-density.
     *
     * Volume aspiré par seconde = cylindrée × régime / 2 (un remplissage tous
     * les deux tours sur un 4 temps) / 60, multiplié par le rendement
     * volumétrique ; la loi des gaz parfaits donne ensuite la masse.
     */
    fun airFromMap(
        mapKpa: Double,
        intakeTempC: Double,
        rpm: Double,
        displacementLiters: Double,
        volumetricEfficiency: Double = DEFAULT_VOLUMETRIC_EFFICIENCY,
    ): Double {
        val tempK = intakeTempC + 273.15
        return mapKpa * displacementLiters * volumetricEfficiency * rpm / 120.0 *
            AIR_MOLAR_MASS_G_PER_MOL / (GAS_CONSTANT * tempK)
    }

    /**
     * Consommation instantanée en L/100 km. Sous 5 km/h elle n'a pas de sens
     * (elle tend vers l'infini à l'arrêt) : on renvoie null et l'affichage
     * bascule sur les L/h.
     */
    fun litersPer100Km(litersPerHour: Double, speedKmh: Double): Double? =
        if (speedKmh >= 5.0) litersPerHour * 100.0 / speedKmh else null

    /**
     * Nouveau facteur d'étalonnage après un plein.
     *
     * [countedLiters] a déjà été calculé avec [currentFactor] : le facteur
     * corrigé est donc l'ancien multiplié par le rapport réel / compté.
     * Borné à [0,5 ; 2,0] pour éviter qu'une saisie erronée ne fausse tout.
     */
    fun calibratedFactor(currentFactor: Double, countedLiters: Double, realLiters: Double): Double =
        (currentFactor * realLiters / countedLiters).coerceIn(0.5, 2.0)
}
