package com.alijafari.red.astronomy.astro_engine

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.*

/**
 * International Space Station tracking and pass-prediction engine.
 *
 * Responsibilities:
 *
 * 1. Obtain and validate the current ISS TLE.
 * 2. Ensure live tracking and pass prediction use the SAME TLE snapshot.
 * 3. Convert SGP4 TEME coordinates to Earth-fixed coordinates.
 * 4. Convert the observer from WGS-84 geodetic coordinates to ECEF.
 * 5. Calculate topocentric azimuth/elevation/range.
 * 6. Calculate satellite illumination using a conical Earth-umbra model.
 * 7. Calculate observer solar altitude.
 * 8. Search for complete orbital passes.
 * 9. Determine whether any portion of a pass is genuinely observable.
 *
 * IMPORTANT:
 * SGP4 itself is deliberately isolated in SGP4Propagator.
 * This class must never "repair" orbital positions using ad-hoc offsets.
 *
 * Scientific references:
 * - Vallado et al., "Revisiting Spacetrack Report #3",
 *   AIAA 2006-6753.
 * - CelesTrak SGP4 verification guidance.
 * - WGS-84 Earth reference ellipsoid.
 */
class ISSEngine {

    companion object {
        private const val TAG = "ISSEngine"

        const val ISS_NORAD_ID = 25544

        private const val CELESTRAK_URL =
            "https://celestrak.org/NORAD/elements/gp.php?CATNR=25544&FORMAT=TLE"

        private const val EARTH_EQUATORIAL_RADIUS_KM = 6378.137
        private const val EARTH_FLATTENING = 1.0 / 298.257223563
        private const val EARTH_ECCENTRICITY_SQUARED =
            EARTH_FLATTENING * (2.0 - EARTH_FLATTENING)

        private const val SUN_RADIUS_KM = 695700.0
        private const val ASTRONOMICAL_UNIT_KM = 149597870.7

        private const val MIN_VALID_ELEVATION_DEG = 10.0

        /*
         * -6° is the end of civil twilight.
         *
         * We intentionally do NOT require the Sun to be below -18°.
         * ISS passes near dawn/dusk can be visible while the sky is
         * substantially brighter than astronomical darkness.
         */
        private const val MAX_SUN_ALTITUDE_FOR_VISIBILITY_DEG = -6.0

        /*
         * A TLE is not an immutable physical orbit. It is a fitted
         * mean-element model whose predictive accuracy degrades with age.
         *
         * We therefore refuse to silently use very old orbital data
         * for the "current" ISS solution.
         */
        private const val MAX_CURRENT_TLE_AGE_DAYS = 7.0

        /*
         * Pass search resolution.
         *
         * 15 seconds is sufficient to detect normal ISS passes while
         * keeping the seven-day calculation computationally reasonable.
         *
         * Visibility itself is evaluated more finely inside each pass.
         */
        private const val PASS_SCAN_STEP_MS = 15_000L
        private const val VISIBILITY_SCAN_STEP_MS = 5_000L

        private const val ROOT_MAX_ITERATIONS = 32
        private const val ROOT_TIME_TOLERANCE_MS = 250L

        private const val PEAK_MAX_ITERATIONS = 32
        private const val PEAK_TIME_TOLERANCE_MS = 250L

        /*
         * Apparent magnitude is only an estimate here.
         *
         * It is NOT used as the primary pass-detection condition.
         * A physically illuminated ISS above the elevation/twilight
         * thresholds must not disappear simply because an approximate
         * photometric model predicts a faint magnitude.
         */
        private const val DEFAULT_ISS_MAGNITUDE = -1.5

        val defaultEngine = ISSEngine()

        var cachedTLE: TLEData
            get() = defaultEngine.cachedTLE
            set(value) {
                defaultEngine.cachedTLE = value
            }

        suspend fun fetchLatestTLE(): TLEData =
            defaultEngine.fetchLatestTLE()

        fun calculateTopocentricPos(
            timestampMs: Long,
            userLatDeg: Double,
            userLonDeg: Double,
            userAltMeters: Double = 940.0,
            tle: TLEData = defaultEngine.cachedTLE
        ): TopocentricPosition =
            defaultEngine.calculateTopocentricPos(
                timestampMs = timestampMs,
                userLatDeg = userLatDeg,
                userLonDeg = userLonDeg,
                userAltMeters = userAltMeters,
                tle = tle
            )

        fun checkIssSunlit(
            jd: Double,
            gmstDeg: Double,
            xEcef: Double,
            yEcef: Double,
            zEcef: Double
        ): Boolean =
            defaultEngine.checkIssSunlit(
                jd,
                gmstDeg,
                xEcef,
                yEcef,
                zEcef
            )

        fun getObserverSunAltitude(
            timestampMs: Long,
            userLatDeg: Double,
            userLonDeg: Double
        ): Double =
            defaultEngine.getObserverSunAltitude(
                timestampMs,
                userLatDeg,
                userLonDeg
            )

        fun predictPasses(
            userLatDeg: Double,
            userLonDeg: Double,
            startTimestampMs: Long = System.currentTimeMillis(),
            tle: TLEData = defaultEngine.cachedTLE,
            scanDays: Int = 7,
            visibleOnly: Boolean = true,
            standardMag: Double = DEFAULT_ISS_MAGNITUDE
        ): List<ISSPass> =
            defaultEngine.predictPasses(
                userLatDeg = userLatDeg,
                userLonDeg = userLonDeg,
                startTimestampMs = startTimestampMs,
                tle = tle,
                scanDays = scanDays,
                visibleOnly = visibleOnly,
                standardMag = standardMag
            )
    }

    enum class PassClassification(
        val labelEn: String,
        val labelFa: String,
        val colorHex: Long
    ) {
        OUTSTANDING(
            "Outstanding Pass",
            "گذر فوق‌العاده استثنایی",
            0xFF2DC653
        ),

        EXCELLENT(
            "Excellent Pass",
            "گذر عالی",
            0xFF38B000
        ),

        VERY_GOOD(
            "Very Good Pass",
            "گذر بسیار خوب",
            0xFF70E000
        ),

        GOOD(
            "Good Pass",
            "گذر خوب",
            0xFF9EF01A
        ),

        MARGINAL(
            "Marginal Pass",
            "گذر حاشیه‌ای / کم‌نور",
            0xFFFFB703
        ),

        POOR(
            "Poor Pass",
            "گذر ضعیف",
            0xFFFF8C00
        ),

        NOT_VISIBLE(
            "Not Visible",
            "قابل مشاهده نیست",
            0xFFE63946
        ),

        INVISIBLE_SHADOW(
            "Invisible (In Shadow)",
            "پنهان در سایه زمین",
            0xFF6C757D
        ),

        DAYLIGHT_ONLY(
            "Daylight Only",
            "گذر در روز",
            0xFF4A90E2
        )
    }

    /**
     * External representation of a TLE.
     *
     * The default values are deliberately EMPTY.
     *
     * We no longer embed a fabricated ISS orbit into the engine.
     */
    data class TLEData(
        val name: String = "ISS (NO TLE)",
        val line1: String = "",
        val line2: String = ""
    )

    data class ISSPass(
        val startTimeMs: Long,
        val maxTimeMs: Long,
        val endTimeMs: Long,

        val maxElevationDeg: Double,
        val maxAltitudeKm: Double,
        val maxAzimuthDeg: Double,

        val startAzimuthDeg: Double,
        val endAzimuthDeg: Double,

        val estimatedMagnitude: Double,
        val passDurationSec: Long,

        val sunAltitudeDegAtMax: Double,

        val isObserverInDarkness: Boolean,
        val isIssSunlitAtMax: Boolean,

        val shadowEntryMs: Long? = null,
        val shadowExitMs: Long? = null,

        val classification: PassClassification,
        val visibilityScore: Int,

        val summaryReasonEn: String,
        val summaryReasonFa: String,

        val detailedReasonsEn: List<String>,
        val detailedReasonsFa: List<String>,

        val isVisible: Boolean = false
    )

    data class TopocentricPosition(
        val elevationDeg: Double,
        val azimuthDeg: Double,
        val rangeKm: Double,

        val subLatDeg: Double,
        val subLonDeg: Double,

        val satAltKm: Double,
        val velocityKmS: Double,

        val isSunlit: Boolean,

        val xEcef: Double,
        val yEcef: Double,
        val zEcef: Double
    )

    private data class EcefPosition(
        val xKm: Double,
        val yKm: Double,
        val zKm: Double
    )

    private data class VisibilityInterval(
        val startMs: Long,
        val endMs: Long
    )

    private val sgp4 = SGP4Propagator()

    /**
     * Last successfully validated TLE.
     *
     * Starts empty rather than with a fabricated orbital element set.
     */
    var cachedTLE: TLEData = TLEData()
        private set

    /**
     * Fetches the latest ISS TLE directly from CelesTrak.
     *
     * This method is retained because the application already has
     * a TLE repository/synchronization layer. It is also useful as
     * an independent fallback source.
     */
    suspend fun fetchLatestTLE(): TLEData =
        withContext(Dispatchers.IO) {
            try {
                val connection =
                    URL(CELESTRAK_URL).openConnection() as HttpURLConnection

                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.requestMethod = "GET"
                connection.instanceFollowRedirects = true

                try {
                    if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                        Log.w(
                            TAG,
                            "CelesTrak returned HTTP ${connection.responseCode}"
                        )

                        return@withContext getBestCachedTleOrThrow()
                    }

                    val text =
                        connection.inputStream.bufferedReader().use {
                            it.readText()
                        }

                    val parsed = parseTleText(text)

                    validateTle(parsed)

                    cachedTLE = parsed

                    logTleSelection("celestrak-direct", parsed)

                    parsed
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                Log.w(
                    TAG,
                    "Unable to fetch current ISS TLE: ${e.message}"
                )

                getBestCachedTleOrThrow()
            }
        }

    /**
     * Calculates the ISS topocentric position for a specific timestamp.
     *
     * The supplied TLE is treated as a snapshot. This is important:
     * every calculation in a pass prediction must use exactly the
     * same TLE epoch/elements.
     */
    fun calculateTopocentricPos(
        timestampMs: Long,
        userLatDeg: Double,
        userLonDeg: Double,
        userAltMeters: Double = 940.0,
        tle: TLEData = cachedTLE
    ): TopocentricPosition {

        require(userLatDeg in -90.0..90.0) {
            "Latitude must be between -90 and +90 degrees."
        }

        require(userLonDeg in -180.0..180.0) {
            "Longitude must be between -180 and +180 degrees."
        }

        val effectiveTle = resolveTleForCalculation(tle)

        val sgp4Tle = parseToSgp4(effectiveTle)

        val teme = sgp4.propagate(
            sgp4Tle,
            timestampMs
        )

        val jd = TimeEngine.getJulianDate(timestampMs)
        val gmstDeg = normalizeDegrees(
            TimeEngine.getGMST(jd)
        )

        /*
         * TEME -> PEF/ECEF using GMST.
         *
         * SGP4 produces TEME. For the current engine architecture
         * we use the standard GMST rotation used for TEME-to-Earth
         * fixed conversion. Polar motion is negligible for the
         * application's intended ISS visualisation scale.
         */
        val gmstRad = Math.toRadians(gmstDeg)

        val cosGmst = cos(gmstRad)
        val sinGmst = sin(gmstRad)

        val xEcef =
            teme.xKm * cosGmst +
                teme.yKm * sinGmst

        val yEcef =
            -teme.xKm * sinGmst +
                teme.yKm * cosGmst

        val zEcef = teme.zKm

        /*
         * WGS-84 geodetic observer.
         *
         * The old implementation treated the Earth as a sphere.
         * At latitude 30° that introduces a real, avoidable
         * topocentric error.
         */
        val observer =
            geodeticToEcef(
                latitudeDeg = userLatDeg,
                longitudeDeg = userLonDeg,
                altitudeMeters = userAltMeters
            )

        val dx = xEcef - observer.xKm
        val dy = yEcef - observer.yKm
        val dz = zEcef - observer.zKm

        val latRad = Math.toRadians(userLatDeg)
        val lonRad = Math.toRadians(userLonDeg)

        /*
         * ECEF -> local ENU.
         */
        val east =
            -sin(lonRad) * dx +
                cos(lonRad) * dy

        val north =
            -sin(latRad) * cos(lonRad) * dx -
                sin(latRad) * sin(lonRad) * dy +
                cos(latRad) * dz

        val up =
            cos(latRad) * cos(lonRad) * dx +
                cos(latRad) * sin(lonRad) * dy +
                sin(latRad) * dz

        val rangeKm =
            sqrt(
                east * east +
                    north * north +
                    up * up
            )

        require(rangeKm > 0.0) {
            "Invalid topocentric range."
        }

        val elevationRad =
            asin(
                (up / rangeKm)
                    .coerceIn(-1.0, 1.0)
            )

        /*
         * Azimuth convention:
         * 0° = North
         * 90° = East
         * 180° = South
         * 270° = West
         */
        val azimuthRad =
            normalizeRadians(
                atan2(east, north)
            )

        /*
         * Geocentric sub-satellite latitude/longitude.
         *
         * This is intentionally labelled geocentric rather than
         * geodetic because the raw ECEF vector is used.
         */
        val subLonRad =
            atan2(yEcef, xEcef)

        val subLatRad =
            atan2(
                zEcef,
                sqrt(
                    xEcef * xEcef +
                        yEcef * yEcef
                )
            )

        val geocentricRadiusKm =
            sqrt(
                xEcef * xEcef +
                    yEcef * yEcef +
                    zEcef * zEcef
            )

        val satAltKm =
            geocentricRadiusKm -
                EARTH_EQUATORIAL_RADIUS_KM

        val velocityKmS =
            sqrt(
                teme.vxKmS * teme.vxKmS +
                    teme.vyKmS * teme.vyKmS +
                    teme.vzKmS * teme.vzKmS
            )

        val isSunlit =
            checkIssSunlit(
                jd = jd,
                gmstDeg = gmstDeg,
                xEcef = xEcef,
                yEcef = yEcef,
                zEcef = zEcef
            )

        return TopocentricPosition(
            elevationDeg =
                Math.toDegrees(elevationRad),

            azimuthDeg =
                Math.toDegrees(azimuthRad),

            rangeKm = rangeKm,

            subLatDeg =
                Math.toDegrees(subLatRad),

            subLonDeg =
                normalizeDegrees(
                    Math.toDegrees(subLonRad)
                ),

            satAltKm = satAltKm,

            velocityKmS = velocityKmS,

            isSunlit = isSunlit,

            xEcef = xEcef,
            yEcef = yEcef,
            zEcef = zEcef
        )
    }

    /**
     * Determines whether the ISS is inside Earth's umbra.
     *
     * This is a conical umbra model rather than the old cylindrical
     * shadow test.
     *
     * The Earth-Sun geometry is calculated in ECEF so the satellite
     * position and Sun direction are expressed in the same frame.
     */
    fun checkIssSunlit(
        jd: Double,
        gmstDeg: Double,
        xEcef: Double,
        yEcef: Double,
        zEcef: Double
    ): Boolean {

        val sunPosition =
            SunEngine.calculatePosition(jd)

        val sunRaRad =
            Math.toRadians(
                sunPosition.raDeg
            )

        val sunDecRad =
            Math.toRadians(
                sunPosition.decDeg
            )

        val gmstRad =
            Math.toRadians(gmstDeg)

        /*
         * Unit vector from Earth toward the Sun in ECEF.
         */
        val sunX =
            cos(sunDecRad) *
                cos(sunRaRad - gmstRad)

        val sunY =
            cos(sunDecRad) *
                sin(sunRaRad - gmstRad)

        val sunZ =
            sin(sunDecRad)

        val satelliteRadiusKm =
            sqrt(
                xEcef * xEcef +
                    yEcef * yEcef +
                    zEcef * zEcef
            )

        /*
         * Projection along Earth -> Sun direction.
         *
         * Positive means the satellite is on the Sun-facing side
         * and therefore cannot be inside Earth's umbra.
         */
        val projection =
            xEcef * sunX +
                yEcef * sunY +
                zEcef * sunZ

        if (projection >= 0.0) {
            return true
        }

        /*
         * Distance behind Earth along the anti-solar axis.
         */
        val distanceBehindEarthKm =
            -projection

        /*
         * Perpendicular distance from the Earth-Sun axis.
         */
        val perpendicularX =
            xEcef - projection * sunX

        val perpendicularY =
            yEcef - projection * sunY

        val perpendicularZ =
            zEcef - projection * sunZ

        val perpendicularDistanceKm =
            sqrt(
                perpendicularX * perpendicularX +
                    perpendicularY * perpendicularY +
                    perpendicularZ * perpendicularZ
            )

        /*
         * Approximate Earth-Sun distance for the current date.
         *
         * The SunEngine position itself contains the time-dependent
         * solar geometry; this constant AU scale is sufficient for
         * the umbral cone's angular size.
         */
        val earthSunDistanceKm =
            ASTRONOMICAL_UNIT_KM

        /*
         * Umbra cone half-angle.
         *
         * tan(alpha) = (Rsun - Rearth) / EarthSunDistance
         */
        val tanUmbraHalfAngle =
            (SUN_RADIUS_KM -
                EARTH_EQUATORIAL_RADIUS_KM) /
                earthSunDistanceKm

        /*
         * Radius of the umbral cone at this distance behind Earth.
         */
        val umbraRadiusKm =
            EARTH_EQUATORIAL_RADIUS_KM -
                distanceBehindEarthKm *
                tanUmbraHalfAngle

        /*
         * The umbra terminates when its radius reaches zero.
         */
        if (umbraRadiusKm <= 0.0) {
            return true
        }

        /*
         * Satellite is in the umbra only when its perpendicular
         * distance from the Earth-Sun axis is smaller than the
         * local umbra radius.
         */
        return perpendicularDistanceKm >=
            umbraRadiusKm
    }

    /**
     * Determines the Sun altitude for the observer.
     *
     * Uses the same Julian-date and sidereal-time chain used by the
     * rest of the astronomy engine.
     */
    fun getObserverSunAltitude(
        timestampMs: Long,
        userLatDeg: Double,
        userLonDeg: Double
    ): Double {

        val jd =
            TimeEngine.getJulianDate(timestampMs)

        val sun =
            SunEngine.calculatePosition(jd)

        val gmstDeg =
            normalizeDegrees(
                TimeEngine.getGMST(jd)
            )

        /*
         * Local apparent sidereal angle.
         *
         * The existing CoordinateEngine expects local sidereal
         * angle = GMST + longitude.
         */
        val lstDeg =
            normalizeDegrees(
                gmstDeg + userLonDeg
            )

        val horizontal =
            CoordinateEngine.equatorialToHorizontal(
                CoordinateEngine.Equatorial(
                    raDeg = sun.raDeg,
                    decDeg = sun.decDeg
                ),
                lstDeg,
                userLatDeg
            )

        return horizontal.altitudeDeg
    }

    /**
     * Checks whether a single instant satisfies the application's
     * operational naked-eye visibility criteria.
     *
     * Conditions:
     *
     * 1. ISS elevation >= 10°.
     * 2. Observer Sun altitude <= -6°.
     * 3. ISS is illuminated by sunlight.
     *
     * These are intentionally geometric/illumination criteria.
     * Approximate magnitude is NOT used to veto the pass.
     */
    fun checkSampleVisibility(
        timestampMs: Long,
        userLatDeg: Double,
        userLonDeg: Double,
        tle: TLEData
    ): Boolean {

        val pos =
            calculateTopocentricPos(
                timestampMs = timestampMs,
                userLatDeg = userLatDeg,
                userLonDeg = userLonDeg,
                userAltMeters = 940.0,
                tle = tle
            )

        if (pos.elevationDeg <
            MIN_VALID_ELEVATION_DEG
        ) {
            return false
        }

        val sunAltitude =
            getObserverSunAltitude(
                timestampMs,
                userLatDeg,
                userLonDeg
            )

        if (sunAltitude >
            MAX_SUN_ALTITUDE_FOR_VISIBILITY_DEG
        ) {
            return false
        }

        return pos.isSunlit
    }

    /**
     * Predicts ISS passes for the requested observer.
     *
     * IMPORTANT:
     *
     * The effective TLE is resolved ONCE at the beginning.
     * Every calculation for the entire prediction window then uses
     * exactly that same TLE.
     *
     * This prevents the live position and pass predictor from
     * accidentally propagating different orbital element sets.
     */
    fun predictPasses(
        userLatDeg: Double,
        userLonDeg: Double,
        startTimestampMs: Long = System.currentTimeMillis(),
        tle: TLEData = cachedTLE,
        scanDays: Int = 7,
        visibleOnly: Boolean = true,
        standardMag: Double = DEFAULT_ISS_MAGNITUDE
    ): List<ISSPass> {

        require(scanDays in 1..30) {
            "scanDays must be between 1 and 30."
        }

        val effectiveTle =
            resolveTleForCalculation(tle)

        logTleSelection(
            "pass-prediction",
            effectiveTle
        )

        val endTimestampMs =
            startTimestampMs +
                scanDays *
                24L *
                60L *
                60L *
                1000L

        val passes =
            mutableListOf<ISSPass>()

        var previousTimeMs =
            startTimestampMs

        var previousPosition =
            calculateTopocentricPos(
                previousTimeMs,
                userLatDeg,
                userLonDeg,
                940.0,
                effectiveTle
            )

        var inPass =
            previousPosition.elevationDeg >=
                MIN_VALID_ELEVATION_DEG

        var passStartMs =
            if (inPass) {
                previousTimeMs
            } else {
                0L
            }

        while (previousTimeMs < endTimestampMs) {

            val currentTimeMs =
                minOf(
                    previousTimeMs +
                        PASS_SCAN_STEP_MS,
                    endTimestampMs
                )

            val currentPosition =
                calculateTopocentricPos(
                    currentTimeMs,
                    userLatDeg,
                    userLonDeg,
                    940.0,
                    effectiveTle
                )

            val previousAbove =
                previousPosition.elevationDeg >=
                    MIN_VALID_ELEVATION_DEG

            val currentAbove =
                currentPosition.elevationDeg >=
                    MIN_VALID_ELEVATION_DEG

            /*
             * Rising through the 10° threshold.
             */
            if (!inPass &&
                !previousAbove &&
                currentAbove
            ) {

                passStartMs =
                    refineElevationThreshold(
                        t1 = previousTimeMs,
                        t2 = currentTimeMs,
                        targetElevationDeg =
                            MIN_VALID_ELEVATION_DEG,
                        rising = true,
                        userLatDeg = userLatDeg,
                        userLonDeg = userLonDeg,
                        tle = effectiveTle
                    )

                inPass = true
            }

            /*
             * If the scan started while the ISS was already above
             * the threshold, treat the beginning of the scan as the
             * beginning of the currently visible segment.
             */
            if (!inPass && currentAbove) {
                inPass = true
                passStartMs =
                    if (previousAbove) {
                        previousTimeMs
                    } else {
                        refineElevationThreshold(
                            t1 = previousTimeMs,
                            t2 = currentTimeMs,
                            targetElevationDeg =
                                MIN_VALID_ELEVATION_DEG,
                            rising = true,
                            userLatDeg = userLatDeg,
                            userLonDeg = userLonDeg,
                            tle = effectiveTle
                        )
                    }
            }

            /*
             * Falling through the 10° threshold.
             */
            if (inPass &&
                previousAbove &&
                !currentAbove
            ) {

                val passEndMs =
                    refineElevationThreshold(
                        t1 = previousTimeMs,
                        t2 = currentTimeMs,
                        targetElevationDeg =
                            MIN_VALID_ELEVATION_DEG,
                        rising = false,
                        userLatDeg = userLatDeg,
                        userLonDeg = userLonDeg,
                        tle = effectiveTle
                    )

                val pass =
                    buildPassFromCompleteInterval(
                        startMs = passStartMs,
                        endMs = passEndMs,
                        userLatDeg = userLatDeg,
                        userLonDeg = userLonDeg,
                        tle = effectiveTle,
                        standardMag = standardMag
                    )

                if (!visibleOnly ||
                    pass.isVisible
                ) {
                    passes.add(pass)
                }

                inPass = false
            }

            /*
             * Handle a scan that ends while the ISS is above
             * the elevation threshold.
             */
            if (currentTimeMs >= endTimestampMs &&
                inPass
            ) {

                val pass =
                    buildPassFromCompleteInterval(
                        startMs = passStartMs,
                        endMs = endTimestampMs,
                        userLatDeg = userLatDeg,
                        userLonDeg = userLonDeg,
                        tle = effectiveTle,
                        standardMag = standardMag
                    )

                if (!visibleOnly ||
                    pass.isVisible
                ) {
                    passes.add(pass)
                }

                inPass = false
            }

            previousTimeMs = currentTimeMs
            previousPosition = currentPosition
        }

        return passes
            .distinctBy {
                /*
                 * Protect against a boundary condition at the
                 * scan-window edge.
                 */
                it.startTimeMs / 1000L
            }
            .sortedBy {
                it.startTimeMs
            }
    }

    /**
     * Builds one complete orbital pass.
     */
    private fun buildPassFromCompleteInterval(
        startMs: Long,
        endMs: Long,
        userLatDeg: Double,
        userLonDeg: Double,
        tle: TLEData,
        standardMag: Double
    ): ISSPass {

        val startPosition =
            calculateTopocentricPos(
                startMs,
                userLatDeg,
                userLonDeg,
                940.0,
                tle
            )

        val endPosition =
            calculateTopocentricPos(
                endMs,
                userLatDeg,
                userLonDeg,
                940.0,
                tle
            )

        val (maxTimeMs, maxPosition) =
            refinePeakElevation(
                startMs = startMs,
                endMs = endMs,
                userLatDeg = userLatDeg,
                userLonDeg = userLonDeg,
                tle = tle
            )

        val visibilityIntervals =
            findVisibilityIntervals(
                startMs = startMs,
                endMs = endMs,
                userLatDeg = userLatDeg,
                userLonDeg = userLonDeg,
                tle = tle
            )

        val isVisible =
            visibilityIntervals.isNotEmpty()

        var shadowEntryMs: Long? = null
        var shadowExitMs: Long? = null

        var previousTime =
            startMs

        var previousSunlit =
            calculateTopocentricPos(
                previousTime,
                userLatDeg,
                userLonDeg,
                940.0,
                tle
            ).isSunlit

        var cursor =
            startMs + VISIBILITY_SCAN_STEP_MS

        while (cursor <= endMs) {

            val currentSunlit =
                calculateTopocentricPos(
                    cursor,
                    userLatDeg,
                    userLonDeg,
                    940.0,
                    tle
                ).isSunlit

            if (currentSunlit !=
                previousSunlit
            ) {

                val transition =
                    refineShadowTransition(
                        t1 = previousTime,
                        t2 = cursor,
                        userLatDeg = userLatDeg,
                        userLonDeg = userLonDeg,
                        tle = tle
                    )

                if (currentSunlit) {
                    shadowExitMs =
                        shadowExitMs ?: transition
                } else {
                    shadowEntryMs =
                        shadowEntryMs ?: transition
                }
            }

            previousSunlit = currentSunlit
            previousTime = cursor
            cursor += VISIBILITY_SCAN_STEP_MS
        }

        val sunAltitudeAtMax =
            getObserverSunAltitude(
                maxTimeMs,
                userLatDeg,
                userLonDeg
            )

        val estimatedMagnitude =
            estimateMagnitude(
                rangeKm = maxPosition.rangeKm,
                elevationDeg =
                    maxPosition.elevationDeg,
                standardMagnitude = standardMag
            )

        return buildPass(
            startMs = startMs,
            maxMs = maxTimeMs,
            endMs = endMs,
            maxPosition = maxPosition,
            startPosition = startPosition,
            endPosition = endPosition,
            sunAltitudeAtMax = sunAltitudeAtMax,
            estimatedMagnitude = estimatedMagnitude,
            shadowEntryMs = shadowEntryMs,
            shadowExitMs = shadowExitMs,
            isVisible = isVisible,
            visibilityIntervals = visibilityIntervals
        )
    }

    /**
     * Evaluates the ENTIRE pass rather than only rise/peak/set.
     *
     * This is one of the most important corrections in this file.
     *
     * A pass can be:
     *
     *   AOS -> ISS enters shadow -> ISS exits shadow -> LOS
     *
     * or:
     *
     *   AOS -> twilight boundary -> visible -> LOS
     *
     * Sampling only AOS, TCA and LOS can therefore miss a genuine
     * visible interval.
     */
    private fun findVisibilityIntervals(
        startMs: Long,
        endMs: Long,
        userLatDeg: Double,
        userLonDeg: Double,
        tle: TLEData
    ): List<VisibilityInterval> {

        val intervals =
            mutableListOf<VisibilityInterval>()

        var cursor =
            startMs

        var previousVisible =
            checkSampleVisibility(
                cursor,
                userLatDeg,
                userLonDeg,
                tle
            )

        var intervalStart =
            if (previousVisible) {
                cursor
            } else {
                null
            }

        cursor += VISIBILITY_SCAN_STEP_MS

        while (cursor <= endMs) {

            val currentVisible =
                checkSampleVisibility(
                    cursor,
                    userLatDeg,
                    userLonDeg,
                    tle
                )

            if (!previousVisible &&
                currentVisible
            ) {

                val refinedStart =
                    refineVisibilityTransition(
                        t1 = cursor -
                            VISIBILITY_SCAN_STEP_MS,
                        t2 = cursor,
                        userLatDeg = userLatDeg,
                        userLonDeg = userLonDeg,
                        tle = tle,
                        entering = true
                    )

                intervalStart =
                    refinedStart
            }

            if (previousVisible &&
                !currentVisible
            ) {

                val refinedEnd =
                    refineVisibilityTransition(
                        t1 = cursor -
                            VISIBILITY_SCAN_STEP_MS,
                        t2 = cursor,
                        userLatDeg = userLatDeg,
                        userLonDeg = userLonDeg,
                        tle = tle,
                        entering = false
                    )

                val start =
                    intervalStart

                if (start != null &&
                    refinedEnd >= start
                ) {
                    intervals.add(
                        VisibilityInterval(
                            startMs = start,
                            endMs = refinedEnd
                        )
                    )
                }

                intervalStart = null
            }

            previousVisible = currentVisible
            cursor += VISIBILITY_SCAN_STEP_MS
        }

        /*
         * If the pass ends while visibility is still true.
         */
        if (previousVisible &&
            intervalStart != null
        ) {

            intervals.add(
                VisibilityInterval(
                    startMs = intervalStart,
                    endMs = endMs
                )
            )
        }

        return intervals
    }

    /**
     * Refines an elevation threshold crossing.
     */
    private fun refineElevationThreshold(
        t1: Long,
        t2: Long,
        targetElevationDeg: Double,
        rising: Boolean,
        userLatDeg: Double,
        userLonDeg: Double,
        tle: TLEData
    ): Long {

        var low =
            minOf(t1, t2)

        var high =
            maxOf(t1, t2)

        repeat(ROOT_MAX_ITERATIONS) {

            if (high - low <=
                ROOT_TIME_TOLERANCE_MS
            ) {
                return (low + high) / 2L
            }

            val mid =
                (low + high) / 2L

            val elevation =
                calculateTopocentricPos(
                    mid,
                    userLatDeg,
                    userLonDeg,
                    940.0,
                    tle
                ).elevationDeg

            if (rising) {
                if (elevation <
                    targetElevationDeg
                ) {
                    low = mid
                } else {
                    high = mid
                }
            } else {
                if (elevation >
                    targetElevationDeg
                ) {
                    low = mid
                } else {
                    high = mid
                }
            }
        }

        return (low + high) / 2L
    }

    /**
     * Refines a visibility transition.
     *
     * Visibility is a Boolean function, so ordinary bisection is
     * appropriate once the scan has bracketed a transition.
     */
    private fun refineVisibilityTransition(
        t1: Long,
        t2: Long,
        userLatDeg: Double,
        userLonDeg: Double,
        tle: TLEData,
        entering: Boolean
    ): Long {

        var low =
            minOf(t1, t2)

        var high =
            maxOf(t1, t2)

        repeat(ROOT_MAX_ITERATIONS) {

            if (high - low <=
                ROOT_TIME_TOLERANCE_MS
            ) {
                return (low + high) / 2L
            }

            val mid =
                (low + high) / 2L

            val visible =
                checkSampleVisibility(
                    mid,
                    userLatDeg,
                    userLonDeg,
                    tle
                )

            if (entering) {

                if (visible) {
                    high = mid
                } else {
                    low = mid
                }

            } else {

                if (visible) {
                    low = mid
                } else {
                    high = mid
                }
            }
        }

        return (low + high) / 2L
    }

    /**
     * Finds the exact maximum elevation inside one complete pass.
     *
     * Uses a bounded golden-section search.
     */
    private fun refinePeakElevation(
        startMs: Long,
        endMs: Long,
        userLatDeg: Double,
        userLonDeg: Double,
        tle: TLEData
    ): Pair<Long, TopocentricPosition> {

        var a =
            startMs.toDouble()

        var b =
            endMs.toDouble()

        val goldenRatio =
            (sqrt(5.0) - 1.0) / 2.0

        var c =
            b -
                goldenRatio *
                (b - a)

        var d =
            a +
                goldenRatio *
                (b - a)

        var fc =
            calculateTopocentricPos(
                c.toLong(),
                userLatDeg,
                userLonDeg,
                940.0,
                tle
            ).elevationDeg

        var fd =
            calculateTopocentricPos(
                d.toLong(),
                userLatDeg,
                userLonDeg,
                940.0,
                tle
            ).elevationDeg

        repeat(PEAK_MAX_ITERATIONS) {

            if (b - a <=
                PEAK_TIME_TOLERANCE_MS
            ) {
                return@repeat
            }

            if (fc < fd) {

                a = c
                c = d
                fc = fd

                d =
                    a +
                        goldenRatio *
                        (b - a)

                fd =
                    calculateTopocentricPos(
                        d.toLong(),
                        userLatDeg,
                        userLonDeg,
                        940.0,
                        tle
                    ).elevationDeg

            } else {

                b = d
                d = c
                fd = fc

                c =
                    b -
                        goldenRatio *
                        (b - a)

                fc =
                    calculateTopocentricPos(
                        c.toLong(),
                        userLatDeg,
                        userLonDeg,
                        940.0,
                        tle
                    ).elevationDeg
            }
        }

        val bestTime =
            ((a + b) / 2.0).toLong()

        val bestPosition =
            calculateTopocentricPos(
                bestTime,
                userLatDeg,
                userLonDeg,
                940.0,
                tle
            )

        return Pair(
            bestTime,
            bestPosition
        )
    }

    /**
     * Refines a shadow transition.
     */
    private fun refineShadowTransition(
        t1: Long,
        t2: Long,
        userLatDeg: Double,
        userLonDeg: Double,
        tle: TLEData
    ): Long {

        var low =
            minOf(t1, t2)

        var high =
            maxOf(t1, t2)

        val initialState =
            calculateTopocentricPos(
                low,
                userLatDeg,
                userLonDeg,
                940.0,
                tle
            ).isSunlit

        repeat(ROOT_MAX_ITERATIONS) {

            if (high - low <=
                ROOT_TIME_TOLERANCE_MS
            ) {
                return (low + high) / 2L
            }

            val mid =
                (low + high) / 2L

            val state =
                calculateTopocentricPos(
                    mid,
                    userLatDeg,
                    userLonDeg,
                    940.0,
                    tle
                ).isSunlit

            if (state ==
                initialState
            ) {
                low = mid
            } else {
                high = mid
            }
        }

        return (low + high) / 2L
    }

    /**
     * Constructs the final pass object.
     */
    private fun buildPass(
        startMs: Long,
        maxMs: Long,
        endMs: Long,
        maxPosition: TopocentricPosition,
        startPosition: TopocentricPosition,
        endPosition: TopocentricPosition,
        sunAltitudeAtMax: Double,
        estimatedMagnitude: Double,
        shadowEntryMs: Long?,
        shadowExitMs: Long?,
        isVisible: Boolean,
        visibilityIntervals: List<VisibilityInterval>
    ): ISSPass {

        val isObserverInDarkness =
            sunAltitudeAtMax <=
                MAX_SUN_ALTITUDE_FOR_VISIBILITY_DEG

        val classification =
            classifyPass(
                maxElevationDeg =
                    maxPosition.elevationDeg,
                sunAltitudeDeg =
                    sunAltitudeAtMax,
                isSunlitAtMax =
                    maxPosition.isSunlit,
                estimatedMagnitude =
                    estimatedMagnitude,
                isVisible =
                    isVisible
            )

        val score =
            calculateVisibilityScore(
                maxElevationDeg =
                    maxPosition.elevationDeg,
                sunAltitudeDeg =
                    sunAltitudeAtMax,
                isSunlit =
                    maxPosition.isSunlit,
                isVisible =
                    isVisible
            )

        val reasonsEn =
            mutableListOf<String>()

        val reasonsFa =
            mutableListOf<String>()

        if (sunAltitudeAtMax <= -18.0) {

            reasonsEn.add(
                "Observer is in astronomical darkness."
            )

            reasonsFa.add(
                "ناظر در تاریکی کامل نجومی قرار دارد."
            )

        } else if (sunAltitudeAtMax <= -12.0) {

            reasonsEn.add(
                "Observer is in nautical twilight."
            )

            reasonsFa.add(
                "ناظر در گرگ‌ومیش دریایی قرار دارد."
            )

        } else if (sunAltitudeAtMax <= -6.0) {

            reasonsEn.add(
                "Civil twilight has ended."
            )

            reasonsFa.add(
                "گرگ‌ومیش شهری به پایان رسیده است."
            )

        } else {

            reasonsEn.add(
                "The sky is too bright for the selected visibility criterion."
            )

            reasonsFa.add(
                "آسمان برای معیار رویت انتخاب‌شده بیش از حد روشن است."
            )
        }

        if (maxPosition.isSunlit) {

            reasonsEn.add(
                "ISS is illuminated by the Sun at maximum elevation."
            )

            reasonsFa.add(
                "ایستگاه در زمان بیشینه ارتفاع در نور خورشید قرار دارد."
            )

        } else {

            reasonsEn.add(
                "ISS is inside Earth's umbra at maximum elevation."
            )

            reasonsFa.add(
                "ایستگاه در زمان بیشینه ارتفاع در سایه زمین قرار دارد."
            )
        }

        reasonsEn.add(
            "Maximum elevation: ${
                String.format(
                    Locale.US,
                    "%.1f",
                    maxPosition.elevationDeg
                )
            }°."
        )

        reasonsFa.add(
            "بیشینه ارتفاع: ${
                String.format(
                    Locale.US,
                    "%.1f",
                    maxPosition.elevationDeg
                )
            } درجه."
        )

        if (visibilityIntervals.isNotEmpty()) {

            reasonsEn.add(
                "At least one continuous portion of the pass satisfies all visibility criteria."
            )

            reasonsFa.add(
                "حداقل بخشی پیوسته از گذر تمام معیارهای رویت را برآورده می‌کند."
            )
        }

        if (estimatedMagnitude <= 4.5) {

            reasonsEn.add(
                "Estimated brightness is compatible with naked-eye observation."
            )

            reasonsFa.add(
                "روشنایی تخمینی با مشاهده با چشم غیرمسلح سازگار است."
            )

        } else {

            reasonsEn.add(
                "Estimated brightness is faint; atmospheric conditions may affect visibility."
            )

            reasonsFa.add(
                "روشنایی تخمینی کم است و شرایط جوی می‌تواند رویت را دشوار کند."
            )
        }

        val summaryEn =
            when (classification) {

                PassClassification.OUTSTANDING ->
                    "Excellent high-elevation ISS pass under dark skies."

                PassClassification.EXCELLENT ->
                    "Strong naked-eye ISS pass."

                PassClassification.VERY_GOOD ->
                    "Very good illuminated ISS pass."

                PassClassification.GOOD ->
                    "Good potentially visible ISS pass."

                PassClassification.MARGINAL ->
                    "Marginal ISS pass; elevation or illumination is limited."

                PassClassification.POOR ->
                    "Poor ISS pass."

                PassClassification.NOT_VISIBLE ->
                    "Pass does not satisfy the selected visibility conditions."

                PassClassification.INVISIBLE_SHADOW ->
                    "ISS is significantly affected by Earth's shadow."

                PassClassification.DAYLIGHT_ONLY ->
                    "The observer's sky is too bright."
            }

        val summaryFa =
            when (classification) {

                PassClassification.OUTSTANDING ->
                    "گذر بسیار عالی ایستگاه در ارتفاع بالا و آسمان تاریک."

                PassClassification.EXCELLENT ->
                    "گذر بسیار مناسب ایستگاه برای مشاهده با چشم غیرمسلح."

                PassClassification.VERY_GOOD ->
                    "گذر بسیار خوب و روشن ایستگاه."

                PassClassification.GOOD ->
                    "گذر خوب و بالقوه قابل مشاهده ایستگاه."

                PassClassification.MARGINAL ->
                    "گذر حاشیه‌ای؛ ارتفاع یا روشنایی محدود است."

                PassClassification.POOR ->
                    "گذر ضعیف ایستگاه."

                PassClassification.NOT_VISIBLE ->
                    "گذر معیارهای رویت انتخاب‌شده را برآورده نمی‌کند."

                PassClassification.INVISIBLE_SHADOW ->
                    "ایستگاه تحت تأثیر سایه زمین قرار دارد."

                PassClassification.DAYLIGHT_ONLY ->
                    "آسمان ناظر بیش از حد روشن است."
            }

        val durationSec =
            maxOf(
                0L,
                (endMs - startMs) / 1000L
            )

        return ISSPass(
            startTimeMs = startMs,
            maxTimeMs = maxMs,
            endTimeMs = endMs,

            maxElevationDeg =
                maxPosition.elevationDeg,

            maxAltitudeKm =
                maxPosition.satAltKm,

            maxAzimuthDeg =
                maxPosition.azimuthDeg,

            startAzimuthDeg =
                startPosition.azimuthDeg,

            endAzimuthDeg =
                endPosition.azimuthDeg,

            estimatedMagnitude =
                estimatedMagnitude,

            passDurationSec =
                durationSec,

            sunAltitudeDegAtMax =
                sunAltitudeAtMax,

            isObserverInDarkness =
                isObserverInDarkness,

            isIssSunlitAtMax =
                maxPosition.isSunlit,

            shadowEntryMs =
                shadowEntryMs,

            shadowExitMs =
                shadowExitMs,

            classification =
                classification,

            visibilityScore =
                score,

            summaryReasonEn =
                summaryEn,

            summaryReasonFa =
                summaryFa,

            detailedReasonsEn =
                reasonsEn,

            detailedReasonsFa =
                reasonsFa,

            isVisible =
                isVisible
        )
    }

    /**
     * Pass classification.
     *
     * Visibility itself has already been determined by the full
     * temporal scan. Classification is descriptive and does not
     * override that determination.
     */
    private fun classifyPass(
        maxElevationDeg: Double,
        sunAltitudeDeg: Double,
        isSunlitAtMax: Boolean,
        estimatedMagnitude: Double,
        isVisible: Boolean
    ): PassClassification {

        if (!isVisible) {

            return when {

                sunAltitudeDeg >
                    MAX_SUN_ALTITUDE_FOR_VISIBILITY_DEG ->
                    PassClassification.DAYLIGHT_ONLY

                !isSunlitAtMax ->
                    PassClassification.INVISIBLE_SHADOW

                estimatedMagnitude > 6.0 ->
                    PassClassification.NOT_VISIBLE

                else ->
                    PassClassification.MARGINAL
            }
        }

        return when {

            maxElevationDeg >= 60.0 &&
                sunAltitudeDeg <= -12.0 &&
                isSunlitAtMax ->
                PassClassification.OUTSTANDING

            maxElevationDeg >= 45.0 &&
                isSunlitAtMax ->
                PassClassification.EXCELLENT

            maxElevationDeg >= 30.0 ->
                PassClassification.VERY_GOOD

            maxElevationDeg >= 20.0 ->
                PassClassification.GOOD

            maxElevationDeg >= 10.0 ->
                PassClassification.MARGINAL

            else ->
                PassClassification.POOR
        }
    }

    /**
     * Calculates a descriptive score.
     *
     * This is NOT a physical probability of observation.
     */
    private fun calculateVisibilityScore(
        maxElevationDeg: Double,
        sunAltitudeDeg: Double,
        isSunlit: Boolean,
        isVisible: Boolean
    ): Int {

        if (!isVisible) {
            return 0
        }

        var score = 0

        score += when {

            maxElevationDeg >= 60.0 -> 40
            maxElevationDeg >= 45.0 -> 35
            maxElevationDeg >= 30.0 -> 30
            maxElevationDeg >= 20.0 -> 22
            maxElevationDeg >= 10.0 -> 15
            else -> 0
        }

        score += when {

            sunAltitudeDeg <= -18.0 -> 35
            sunAltitudeDeg <= -12.0 -> 30
            sunAltitudeDeg <= -9.0 -> 25
            sunAltitudeDeg <= -6.0 -> 20
            else -> 0
        }

        if (isSunlit) {
            score += 25
        }

        return score.coerceIn(0, 100)
    }

    /**
     * Conservative descriptive apparent-magnitude estimate.
     *
     * This is deliberately NOT used as the hard visibility gate.
     *
     * A proper ISS photometric model would need phase angle,
     * attitude/configuration, solar illumination geometry,
     * observer-satellite-Sun geometry and atmospheric extinction.
     */
    private fun estimateMagnitude(
        rangeKm: Double,
        elevationDeg: Double,
        standardMagnitude: Double
    ): Double {

        val rangeFactor =
            5.0 *
                log10(
                    maxOf(
                        0.1,
                        rangeKm / 400.0
                    )
                )

        val lowElevationPenalty =
            when {

                elevationDeg < 15.0 -> 1.0
                elevationDeg < 25.0 -> 0.5
                else -> 0.0
            }

        return standardMagnitude +
            rangeFactor +
            lowElevationPenalty
    }

    /**
     * Converts WGS-84 geodetic coordinates to ECEF.
     */
    private fun geodeticToEcef(
        latitudeDeg: Double,
        longitudeDeg: Double,
        altitudeMeters: Double
    ): EcefPosition {

        val latitudeRad =
            Math.toRadians(latitudeDeg)

        val longitudeRad =
            Math.toRadians(longitudeDeg)

        val altitudeKm =
            altitudeMeters / 1000.0

        val sinLat =
            sin(latitudeRad)

        val cosLat =
            cos(latitudeRad)

        val sinLon =
            sin(longitudeRad)

        val cosLon =
            cos(longitudeRad)

        val primeVerticalRadius =
            EARTH_EQUATORIAL_RADIUS_KM /
                sqrt(
                    1.0 -
                        EARTH_ECCENTRICITY_SQUARED *
                        sinLat *
                        sinLat
                )

        val x =
            (primeVerticalRadius +
                altitudeKm) *
                cosLat *
                cosLon

        val y =
            (primeVerticalRadius +
                altitudeKm) *
                cosLat *
                sinLon

        val z =
            (
                primeVerticalRadius *
                    (1.0 -
                        EARTH_ECCENTRICITY_SQUARED) +
                    altitudeKm
                ) *
                sinLat

        return EcefPosition(
            xKm = x,
            yKm = y,
            zKm = z
        )
    }

    /**
     * Resolves the TLE that should actually be used.
     *
     * Priority:
     *
     * 1. A valid explicit TLE supplied by the caller.
     * 2. The application's custom TLE repository resolver.
     * 3. A valid engine cache.
     *
     * A fabricated/empty/default ISS TLE is NEVER silently accepted.
     */
    private fun resolveTleForCalculation(
        requestedTle: TLEData
    ): TLEData {

        /*
         * First determine whether this is actually a valid TLE.
         */
        if (isValidTle(requestedTle)) {

            /*
             * For ISS calculations, if the caller supplied a known
             * current TLE, preserve it exactly. This is essential
             * for pass prediction because the entire prediction must
             * use one immutable snapshot.
             */
            return requestedTle
        }

        /*
         * If the supplied TLE is missing/invalid, ask the application's
         * authoritative repository.
         */
        val resolver =
            SatelliteEngine.customTleResolver

        if (resolver != null) {

            val resolved =
                resolver.invoke(ISS_NORAD_ID)

            if (resolved != null &&
                isValidTle(resolved)
            ) {

                cachedTLE = resolved

                logTleSelection(
                    "repository-resolver",
                    resolved
                )

                return resolved
            }
        }

        /*
         * Finally, use our last known validated TLE if one exists.
         */
        if (isValidTle(cachedTLE)) {

            if (tleAgeDays(cachedTLE) <=
                MAX_CURRENT_TLE_AGE_DAYS
            ) {

                logTleSelection(
                    "validated-cache",
                    cachedTLE
                )

                return cachedTLE
            }

            Log.w(
                TAG,
                "Cached ISS TLE is older than " +
                    "$MAX_CURRENT_TLE_AGE_DAYS days."
            )
        }

        throw IllegalStateException(
            "No valid current ISS TLE is available. " +
                "The application must obtain a valid TLE before " +
                "attempting ISS propagation."
        )
    }

    /**
     * Parses the public TLE text returned by CelesTrak.
     */
    private fun parseTleText(
        text: String
    ): TLEData {

        val lines =
            text.lines()
                .map { it.trimEnd() }
                .filter { it.isNotBlank() }

        val line1 =
            lines.firstOrNull {
                it.startsWith("1 25544")
            }

        val line2 =
            lines.firstOrNull {
                it.startsWith("2 25544")
            }

        require(
            line1 != null &&
                line2 != null
        ) {
            "CelesTrak response did not contain a valid ISS TLE."
        }

        val name =
            lines.firstOrNull {
                !it.startsWith("1 ") &&
                    !it.startsWith("2 ")
            }?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: "ISS (ZARYA)"

        return TLEData(
            name = name,
            line1 = line1,
            line2 = line2
        )
    }

    /**
     * Validates structural correctness and TLE checksums.
     */
    private fun validateTle(
        tle: TLEData
    ) {

        require(
            isValidTle(tle)
        ) {
            "Invalid ISS TLE."
        }
    }

    /**
     * Structural + checksum validation.
     */
    private fun isValidTle(
        tle: TLEData
    ): Boolean {

        if (tle.line1.length < 69 ||
            tle.line2.length < 69
        ) {
            return false
        }

        if (!tle.line1.startsWith("1 25544") ||
            !tle.line2.startsWith("2 25544")
        ) {
            return false
        }

        if (!validateChecksum(tle.line1) ||
            !validateChecksum(tle.line2)
        ) {
            return false
        }

        /*
         * Basic epoch validation.
         */
        val epoch =
            try {
                tle.line1
                    .substring(18, 32)
                    .trim()
                    .toDouble()
            } catch (_: Exception) {
                return false
            }

        if (!epoch.isFinite()) {
            return false
        }

        /*
         * Mean motion must be physically plausible for ISS.
         */
        val meanMotion =
            try {
                tle.line2
                    .substring(52, 63)
                    .trim()
                    .toDouble()
            } catch (_: Exception) {
                return false
            }

        if (!meanMotion.isFinite() ||
            meanMotion <= 0.0
        ) {
            return false
        }

        return true
    }

    /**
     * NORAD TLE checksum:
     *
     * Sum all numeric characters.
     * Add 1 for each minus sign.
     * Ignore all other characters.
     * Last digit must match column 69.
     */
    private fun validateChecksum(
        line: String
    ): Boolean {

        if (line.length < 69) {
            return false
        }

        val expected =
            line[68]
                .digitToIntOrNull()
                ?: return false

        var sum = 0

        for (i in 0 until 68) {

            val c = line[i]

            when {

                c.isDigit() ->
                    sum += c.digitToInt()

                c == '-' ->
                    sum += 1
            }
        }

        return (sum % 10) == expected
    }

    /**
     * Computes TLE age in days.
     */
    private fun tleAgeDays(
        tle: TLEData
    ): Double {

        return try {

            val epochText =
                tle.line1
                    .substring(18, 32)
                    .trim()

            val yy =
                epochText
                    .substring(0, 2)
                    .toInt()

            val year =
                if (yy < 57) {
                    2000 + yy
                } else {
                    1900 + yy
                }

            val day =
                epochText
                    .substring(2)
                    .toDouble()

            val calendar =
                Calendar.getInstance(
                    TimeZone.getTimeZone("UTC")
                ).apply {
                    clear()
                    set(
                        Calendar.YEAR,
                        year
                    )
                    set(
                        Calendar.DAY_OF_YEAR,
                        1
                    )
                }

            val epochMs =
                calendar.timeInMillis +
                    ((day - 1.0) *
                        86_400_000.0)
                        .toLong()

            (
                System.currentTimeMillis() -
                    epochMs
                ) /
                86_400_000.0

        } catch (_: Exception) {

            Double.POSITIVE_INFINITY
        }
    }

    /**
     * Logs the exact orbital source being used.
     */
    private fun logTleSelection(
        source: String,
        tle: TLEData
    ) {

        val age =
            tleAgeDays(tle)

        Log.i(
            TAG,
            "TLE source=$source " +
                "name=${tle.name} " +
                "epoch=${extractEpoch(tle)} " +
                "ageDays=${
                    if (age.isFinite()) {
                        String.format(
                            Locale.US,
                            "%.3f",
                            age
                        )
                    } else {
                        "UNKNOWN"
                    }
                }"
        )
    }

    private fun extractEpoch(
        tle: TLEData
    ): String {

        return try {
            tle.line1
                .substring(18, 32)
                .trim()
        } catch (_: Exception) {
            "UNKNOWN"
        }
    }

    /**
     * Converts the external TLE into the internal SGP4 representation.
     *
     * TLE columns follow the NORAD two-line format.
     */
    private fun parseToSgp4(
        tle: TLEData
    ): SGP4Propagator.TLEData {

        require(
            isValidTle(tle)
        ) {
            "Cannot propagate an invalid ISS TLE."
        }

        val epochYear =
            tle.line1
                .substring(18, 20)
                .trim()
                .toInt()

        val epochDay =
            tle.line1
                .substring(20, 32)
                .trim()
                .toDouble()

        val bStar =
            parseBStar(
                tle.line1
            )

        val inclination =
            tle.line2
                .substring(8, 16)
                .trim()
                .toDouble()

        val raan =
            tle.line2
                .substring(17, 25)
                .trim()
                .toDouble()

        val eccentricity =
            (
                "0." +
                    tle.line2
                        .substring(26, 33)
                        .trim()
                ).toDouble()

        val argumentOfPerigee =
            tle.line2
                .substring(34, 42)
                .trim()
                .toDouble()

        val meanAnomaly =
            tle.line2
                .substring(43, 51)
                .trim()
                .toDouble()

        val meanMotion =
            tle.line2
                .substring(52, 63)
                .trim()
                .toDouble()

        return SGP4Propagator.TLEData(
            epochYear = epochYear,
            epochDay = epochDay,
            inclinationDeg = inclination,
            raanDeg = raan,
            eccentricity = eccentricity,
            argPerigeeDeg = argumentOfPerigee,
            meanAnomalyDeg = meanAnomaly,
            meanMotion = meanMotion,
            bStar = bStar
        )
    }

    /**
     * Parses the implied-decimal TLE B* field.
     *
     * Example:
     *
     * 10100-3
     *
     * means:
     *
     * 0.10100 × 10^-3
     */
    private fun parseBStar(
        line1: String
    ): Double {

        if (line1.length < 61) {
            return 0.0
        }

        return try {

            val field =
                line1
                    .substring(53, 61)

            val trimmed =
                field.trim()

            if (trimmed.isEmpty()) {
                return 0.0
            }

            var sign =
                1.0

            var value =
                trimmed

            if (value.startsWith("-")) {

                sign = -1.0
                value = value.substring(1)

            } else if (
                value.startsWith("+")
            ) {

                value = value.substring(1)
            }

            if (value.length < 2) {
                return 0.0
            }

            val exponentIndex =
                value.indexOfFirst {
                    it == '-' ||
                        it == '+'
                }

            if (exponentIndex <= 0) {
                return 0.0
            }

            val mantissa =
                (
                    "0." +
                        value.substring(
                            0,
                            exponentIndex
                        )
                    ).toDouble()

            val exponent =
                value
                    .substring(
                        exponentIndex
                    )
                    .toInt()

            sign *
                mantissa *
                10.0.pow(
                    exponent.toDouble()
                )

        } catch (_: Exception) {

            0.0
        }
    }

    /**
     * Normalizes degrees into [0, 360).
     */
    private fun normalizeDegrees(
        degrees: Double
    ): Double {

        var result =
            degrees % 360.0

        if (result < 0.0) {
            result += 360.0
        }

        return result
    }

    /**
     * Normalizes radians into [0, 2π).
     */
    private fun normalizeRadians(
        radians: Double
    ): Double {

        var result =
            radians % (2.0 * PI)

        if (result < 0.0) {
            result += 2.0 * PI
        }

        return result
    }
}
