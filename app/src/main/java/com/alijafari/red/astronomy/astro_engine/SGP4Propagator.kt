package com.alijafari.red.astronomy.astro_engine

import kotlin.math.*

/**
 * SGP4 near-Earth orbital propagator for TLE data.
 *
 * This implementation follows the standard SGP4 formulation described in:
 *
 *   Hoots & Roehrich (1980), Spacetrack Report No. 3
 *   Vallado et al. (2006), AIAA 2006-6753
 *
 * Important:
 * - SGP4's canonical gravitational constants are WGS-72.
 * - Input TLE angles are degrees.
 * - Mean motion is revolutions/day.
 * - B* is in inverse Earth radii.
 * - Output position is TEME in km.
 * - Output velocity is TEME in km/s.
 *
 * This implementation targets the near-Earth SGP4 branch used by the ISS.
 * It intentionally does not silently substitute fabricated orbital elements
 * when a TLE is supplied.
 */
class SGP4Propagator {

    companion object {

        private const val MINUTES_PER_DAY = 1440.0
        private const val SECONDS_PER_MINUTE = 60.0

        private const val TWO_PI = 2.0 * Math.PI
        private const val DEG2RAD = Math.PI / 180.0
        private const val RAD2DEG = 180.0 / Math.PI

        /*
         * Canonical SGP4 WGS-72 constants.
         *
         * Do NOT replace these with WGS-84 constants.
         * SGP4 TLE propagation is conventionally defined using WGS-72.
         */
        private const val EARTH_RADIUS_KM = 6378.135
        private const val MU_KM3_S2 = 398600.8

        private const val J2 = 0.001082616
        private const val J3 = -0.00000253881
        private const val J4 = -0.00000165597

        private const val CK2 = J2 / 2.0
        private const val CK4 = -3.0 * J4 / 8.0
        private const val XJ3 = J3

        /*
         * XKE is the canonical SGP4 value:
         * sqrt(GM) expressed in Earth-radii^(3/2) / minute.
         */
        private const val XKE = 0.0743669161

        private const val QOMS2T = 1.8802791590152706e-9
        private const val S = 1.0122292801892716

        private const val E6A = 1.0e-6
        private const val X2O3 = 2.0 / 3.0

        /*
         * TLE B* is expressed in inverse Earth radii.
         */
    }

    /**
     * TLE representation required by the existing RED astronomy engine.
     */
    data class TLEData(
        val epochYear: Int,
        val epochDay: Double,
        val inclinationDeg: Double,
        val raanDeg: Double,
        val eccentricity: Double,
        val argPerigeeDeg: Double,
        val meanAnomalyDeg: Double,
        val meanMotion: Double,
        val bStar: Double
    )

    /**
     * TEME position and velocity.
     */
    data class TemEState(
        val xKm: Double,
        val yKm: Double,
        val zKm: Double,
        val vxKmS: Double,
        val vyKmS: Double,
        val vzKmS: Double
    )

    /**
     * Propagate a TLE to UTC epoch.
     *
     * @param tle parsed TLE orbital elements
     * @param targetTimeMs target UTC time in milliseconds
     */
    fun propagate(
        tle: TLEData,
        targetTimeMs: Long
    ): TemEState {

        validateTle(tle)

        val epochMs = tleEpochToMs(
            tle.epochYear,
            tle.epochDay
        )

        val tsinceMinutes =
            (targetTimeMs - epochMs) / 60000.0

        return sgp4(
            tle = tle,
            tsinceMinutes = tsinceMinutes
        )
    }

    /**
     * Core near-Earth SGP4 propagation.
     *
     * The implementation follows the standard initialization,
     * secular-drag propagation, Kepler solution, short-period
     * corrections, and TEME conversion sequence.
     */
    private fun sgp4(
        tle: TLEData,
        tsinceMinutes: Double
    ): TemEState {

        val inclo = tle.inclinationDeg * DEG2RAD
        val nodeo = tle.raanDeg * DEG2RAD
        val argpo = tle.argPerigeeDeg * DEG2RAD
        val mo = tle.meanAnomalyDeg * DEG2RAD

        val ecco = tle.eccentricity.coerceIn(
            1.0e-8,
            0.999999
        )

        val bstar = tle.bStar

        /*
         * Mean motion in radians/minute.
         */
        val noKozai =
            tle.meanMotion * TWO_PI / MINUTES_PER_DAY

        require(noKozai > 0.0) {
            "Invalid TLE mean motion: ${tle.meanMotion}"
        }

        /*
         * ----------------------------------------------------------------
         * 1. Recover un-Kozai mean motion.
         * ----------------------------------------------------------------
         */

        val cosio = cos(inclo)
        val cosio2 = cosio * cosio

        val omeosq = 1.0 - ecco * ecco
        val rteosq = sqrt(omeosq)

        val ak =
            (XKE / noKozai).pow(X2O3)

        val d1 =
            0.75 *
                    J2 *
                    (3.0 * cosio2 - 1.0) /
                    (rteosq * omeosq)

        val delPrime =
            d1 / (ak * ak)

        val ao =
            ak *
                    (1.0 -
                            delPrime *
                            (1.0 / 3.0 +
                                    delPrime *
                                    (1.0 +
                                            delPrime *
                                            134.0 / 81.0)))

        val delo =
            0.75 *
                    J2 *
                    (3.0 * cosio2 - 1.0) /
                    (rteosq * omeosq * ao * ao)

        val noUnkozai =
            noKozai / (1.0 + delo)

        val aodp =
            (XKE / noUnkozai).pow(X2O3)

        /*
         * ----------------------------------------------------------------
         * 2. Determine perigee and atmospheric model parameters.
         * ----------------------------------------------------------------
         */

        val perigeeEarthRadii =
            aodp * (1.0 - ecco)

        val perigeeKm =
            (perigeeEarthRadii - 1.0) *
                    EARTH_RADIUS_KM

        var sfour = S
        var qzms24 = QOMS2T

        if (perigeeKm < 156.0) {

            sfour =
                perigeeEarthRadii - 78.0 / EARTH_RADIUS_KM

            if (perigeeKm < 98.0) {
                sfour =
                    20.0 / EARTH_RADIUS_KM + 1.0
            }

            qzms24 =
                ((120.0 - sfour * EARTH_RADIUS_KM) /
                        EARTH_RADIUS_KM).pow(4.0)
        }

        val pinvsq =
            1.0 /
                    (aodp * aodp *
                            omeosq * omeosq)

        val tsi =
            1.0 /
                    (aodp - sfour)

        val eta =
            aodp * ecco * tsi

        val etasq =
            eta * eta

        val eeta =
            ecco * eta

        /*
         * ----------------------------------------------------------------
         * 3. Atmospheric drag coefficients.
         * ----------------------------------------------------------------
         */

        val coef =
            qzms24 *
                    tsi.pow(4.0)

        val coef1 =
            coef * noUnkozai

        val c2 =
            coef *
                    aodp *
                    (
                            1.0 +
                                    1.5 * etasq +
                                    eeta *
                                    (4.0 + etasq)
                            +
                                    0.375 *
                                    CK2 *
                                    tsi /
                                    omeosq *
                                    (3.0 * cosio2 - 1.0) *
                                    (
                                            8.0 +
                                                    3.0 * etasq *
                                                    (8.0 + etasq)
                                            )
                            )

        val c1 =
            bstar * c2

        val c3 =
            if (ecco > E6A) {
                coef *
                        tsi *
                        XJ3 *
                        sin(inclo) /
                        (CK2 * ecco)
            } else {
                0.0
            }

        val c4 =
            2.0 *
                    noUnkozai *
                    coef *
                    aodp *
                    omeosq *
                    (
                            eta *
                                    (2.0 + 0.5 * etasq)
                            +
                                    ecco *
                                    (0.5 + 2.0 * etasq)
                            -
                                    2.0 *
                                    CK2 *
                                    tsi /
                                    (aodp * omeosq) *
                                    (
                                            3.0 *
                                                    (1.0 - 3.0 * cosio2) *
                                                    (
                                                            1.0 +
                                                                    1.5 * etasq -
                                                                    2.0 * eeta -
                                                                    0.5 * eeta * etasq
                                                            )
                                            +
                                                    0.75 *
                                                    (1.0 - cosio2) *
                                                    (
                                                            2.0 * etasq -
                                                                    eeta -
                                                                    eeta * etasq
                                                            ) *
                                                    cos(2.0 * argpo)
                                            )
                            )

        val c5 =
            2.0 *
                    coef *
                    aodp *
                    omeosq *
                    (
                            1.0 +
                                    2.75 *
                                    (etasq + eeta) +
                                    eeta * etasq
                            )

        /*
         * ----------------------------------------------------------------
         * 4. Secular rates.
         * ----------------------------------------------------------------
         */

        val temp1 =
            3.0 *
                    CK2 *
                    pinvsq *
                    noUnkozai

        val temp2 =
            temp1 *
                    CK2 *
                    pinvsq

        val temp3 =
            1.25 *
                    CK4 *
                    pinvsq *
                    pinvsq *
                    noUnkozai

        val xmdot =
            noUnkozai +
                    0.5 *
                    temp1 *
                    rteosq *
                    (3.0 * cosio2 - 1.0) +
                    0.0625 *
                    temp2 *
                    rteosq *
                    (
                            13.0 -
                                    78.0 * cosio2 +
                                    137.0 * cosio2 * cosio2
                            )

        val argpdot =
            -0.5 *
                    temp1 *
                    (1.0 - 5.0 * cosio2) +
                    0.0625 *
                    temp2 *
                    (
                            7.0 -
                                    114.0 * cosio2 +
                                    395.0 * cosio2 * cosio2
                            ) +
                    temp3 *
                    (
                            3.0 -
                                    36.0 * cosio2 +
                                    49.0 * cosio2 * cosio2
                            )

        val nodedot =
            -temp1 * cosio +
                    0.125 *
                    temp2 *
                    (
                            4.0 * cosio -
                                    19.0 * cosio * cosio2
                            ) +
                    2.0 *
                    temp3 *
                    cosio *
                    (
                            3.0 -
                                    7.0 * cosio2
                            )

        /*
         * ----------------------------------------------------------------
         * 5. Drag secular coefficients.
         * ----------------------------------------------------------------
         */

        val cc1 =
            c1

        val cc2 =
            c2

        val cc3 =
            c3

        val cc4 =
            c4

        val cc5 =
            c5

        val d2 =
            4.0 *
                    aodp *
                    tsi *
                    cc1 *
                    cc1

        val d3 =
            (4.0 / 3.0) *
                    aodp *
                    tsi *
                    tsi *
                    (17.0 * aodp + sfour) *
                    cc1 *
                    cc1 *
                    cc1

        val d4 =
            (2.0 / 3.0) *
                    aodp *
                    tsi.pow(3.0) *
                    (221.0 * aodp + 31.0 * sfour) *
                    cc1.pow(4.0)

        val t2cof =
            1.5 * cc1

        val t3cof =
            d2 +
                    2.0 * cc1 * cc1

        val t4cof =
            0.25 *
                    (
                            3.0 * d3 +
                                    12.0 * cc1 * d2 +
                                    3.0 * cc1.pow(3.0)
                            )

        val t5cof =
            0.2 *
                    (
                            3.0 * d4 +
                                    12.0 * cc1 * d3 +
                                    6.0 * d2 * d2 +
                                    12.0 * cc1 * cc1 * d2 +
                                    2.0 * cc1.pow(4.0)
                            )

        val omgcof =
            bstar *
                    cc3 *
                    cos(argpo)

        val xmcof =
            if (ecco > E6A) {
                -2.0 / 3.0 *
                        coef *
                        bstar /
                        eeta
            } else {
                0.0
            }

        val xnodcf =
            3.5 *
                    omeosq *
                    temp1 *
                    cosio *
                    cc1

        /*
         * ----------------------------------------------------------------
         * 6. Secular propagation.
         * ----------------------------------------------------------------
         */

        val t = tsinceMinutes

        val xmdf =
            mo +
                    xmdot * t

        val argpdf =
            argpo +
                    argpdot * t

        val nodedf =
            nodeo +
                    nodedot * t

        val t2 = t * t
        val t3 = t2 * t
        val t4 = t3 * t

        var tempa =
            1.0 -
                    cc1 * t -
                    d2 * t2 -
                    d3 * t3 -
                    d4 * t4

        var tempe =
            bstar *
                    cc4 *
                    t

        var templ =
            t2cof * t2 +
                    t3cof * t3 +
                    t4 *
                    (t4cof + t * t5cof)

        var mm =
            xmdf

        var argpm =
            argpdf

        var nodem =
            nodedf +
                    xnodcf * t2

        /*
         * Apply the additional long-period drag terms for
         * normal-perigee satellites.
         */
        if (perigeeKm >= 156.0) {

            val delomg =
                omgcof * t

            val delm =
                xmcof *
                        (
                                (1.0 + eta * cos(xmdf)).pow(3.0) -
                                        (1.0 + eta * cos(mo)).pow(3.0)
                                )

            val temp =
                delomg + delm

            mm =
                xmdf + temp

            argpm =
                argpdf - temp

            tempe +=
                bstar *
                        cc5 *
                        (
                                sin(mm) -
                                        sin(mo)
                                )
        }

        /*
         * Updated semi-major axis and eccentricity.
         */
        val am =
            aodp * tempa * tempa

        val em =
            (ecco - tempe)
                .coerceIn(
                    1.0e-7,
                    0.999999
                )

        /*
         * Mean longitude.
         *
         * This is the critical quantity that must be used to
         * construct the Kepler equation. The argument of perigee
         * must not be dropped.
         */
        val axn =
            em * cos(argpm)

        val ayn =
            em * sin(argpm)

        /*
         * Long-period J3 corrections.
         *
         * These are part of the standard SGP4 long-period
         * transformation used before solving Kepler's equation.
         */
        val betaSq =
            1.0 - em * em

        val tempLp =
            1.0 /
                    (am * betaSq)

        val aycof =
            -0.25 *
                    (XJ3 / CK2) *
                    sin(inclo)

        val xlcofDenominator =
            1.0 + cosio

        val xlcof =
            if (abs(xlcofDenominator) > 1.0e-12) {
                -0.125 *
                        (XJ3 / CK2) *
                        sin(inclo) *
                        (
                                3.0 +
                                        5.0 * cosio
                                ) /
                        xlcofDenominator
            } else {
                0.0
            }

        val aynCorrected =
            ayn +
                    tempLp * aycof

        val xll =
            tempLp *
                    xlcof *
                    axn

        /*
         * Standard SGP4 mean longitude.
         *
         * xlt = M + omega + Omega + drag correction + J3 term.
         */
        val xl =
            mm +
                    argpm +
                    nodem +
                    noUnkozai * templ +
                    xll

        /*
         * u = mean longitude - node.
         *
         * This retains M + omega rather than using M alone.
         */
        var u =
            normalizeRadians(
                xl - nodem
            )

        /*
         * ----------------------------------------------------------------
         * 7. Solve Kepler's equation.
         * ----------------------------------------------------------------
         *
         * Equation:
         *
         *   E - axn sin(E) + ayn cos(E) = u
         *
         * where:
         *
         *   axn = e cos(omega)
         *   ayn = e sin(omega) + J3 correction
         */
        var eo1 = u

        for (i in 0 until 10) {

            val sineo1 =
                sin(eo1)

            val coseo1 =
                cos(eo1)

            val f =
                eo1 -
                        axn * sineo1 +
                        aynCorrected * coseo1 -
                        u

            val fp =
                1.0 -
                        axn * coseo1 -
                        aynCorrected * sineo1

            val delta =
                f / fp

            eo1 -= delta

            if (abs(delta) < 1.0e-12) {
                break
            }
        }

        /*
         * ----------------------------------------------------------------
         * 8. Recover short-period quantities.
         * ----------------------------------------------------------------
         */

        val sinepw =
            sin(eo1)

        val cosepw =
            cos(eo1)

        val ecose =
            axn * cosepw +
                    aynCorrected * sinepw

        val esine =
            axn * sinepw -
                    aynCorrected * cosepw

        val el2 =
            axn * axn +
                    aynCorrected * aynCorrected

        val pl =
            am * (1.0 - el2)

        require(pl > 0.0) {
            "Invalid propagated orbital parameter: p=$pl"
        }

        val r =
            am * (1.0 - ecose)

        require(r > 0.0) {
            "Invalid propagated radius: r=$r"
        }

        val rdot =
            sqrt(am) *
                    esine /
                    r

        val rfdot =
            sqrt(pl) /
                    r

        val betal =
            sqrt(
                max(
                    1.0e-15,
                    1.0 - el2
                )
            )

        val sinu =
            (am / r) *
                    (
                            sinepw -
                                    aynCorrected -
                                    axn *
                                    esine /
                                    (1.0 + betal)
                            )

        val cosu =
            (am / r) *
                    (
                            cosepw -
                                    axn +
                                    aynCorrected *
                                    esine /
                                    (1.0 + betal)
                            )

        val uk =
            atan2(
                sinu,
                cosu
            )

        val sin2u =
            sin(2.0 * uk)

        val cos2u =
            cos(2.0 * uk)

        val temp =
            1.0 / pl

        val temp1 =
            CK2 * temp

        val temp2 =
            temp1 * temp

        /*
         * Short-period corrections.
         */
        val rk =
            r *
                    (
                            1.0 -
                                    1.5 *
                                    temp2 *
                                    betal *
                                    (3.0 * cosio2 - 1.0)
                            ) +
                    0.5 *
                    temp1 *
                    (1.0 - cosio2) *
                    cos2u

        val ukCorrected =
            uk -
                    0.25 *
                    temp2 *
                    (7.0 * cosio2 - 1.0) *
                    sin2u

        val xnodek =
            nodem +
                    1.5 *
                    temp2 *
                    cosio *
                    sin2u

        val xinck =
            inclo +
                    1.5 *
                    temp2 *
                    cosio *
                    sin(inclo) *
                    cos2u

        /*
         * SGP4 velocity correction.
         */
        val n0Canonical =
            noUnkozai / XKE

        val rdotk =
            rdot -
                    temp1 *
                    n0Canonical *
                    (1.0 - cosio2) *
                    sin2u

        val rfdotk =
            rfdot +
                    temp1 *
                    n0Canonical *
                    (
                            (1.0 - cosio2) *
                                    cos2u +
                                    1.5 *
                                    (3.0 * cosio2 - 1.0)
                            )

        /*
         * ----------------------------------------------------------------
         * 9. Convert orbital-frame state to TEME.
         * ----------------------------------------------------------------
         */

        val sinuk =
            sin(ukCorrected)

        val cosuk =
            cos(ukCorrected)

        val sinik =
            sin(xinck)

        val cosik =
            cos(xinck)

        val sinnok =
            sin(xnodek)

        val cosnok =
            cos(xnodek)

        /*
         * Unit vectors in the TEME orbital plane.
         */
        val ux =
            cosnok * cosuk -
                    sinnok * cosik * sinuk

        val uy =
            sinnok * cosuk +
                    cosnok * cosik * sinuk

        val uz =
            sinik * sinuk

        val vx =
            -cosnok * sinuk -
                    sinnok * cosik * cosuk

        val vy =
            -sinnok * sinuk +
                    cosnok * cosik * cosuk

        val vz =
            sinik * cosuk

        /*
         * Position in Earth radii -> km.
         */
        val xKm =
            rk *
                    ux *
                    EARTH_RADIUS_KM

        val yKm =
            rk *
                    uy *
                    EARTH_RADIUS_KM

        val zKm =
            rk *
                    uz *
                    EARTH_RADIUS_KM

        /*
         * Velocity:
         *
         * Earth radius × XKE / 60
         *
         * converts Earth-radii/minute to km/s.
         */
        val velocityFactor =
            EARTH_RADIUS_KM *
                    XKE /
                    SECONDS_PER_MINUTE

        val vxKmS =
            (
                    rdotk * ux +
                            rfdotk * vx
                    ) *
                    velocityFactor

        val vyKmS =
            (
                    rdotk * uy +
                            rfdotk * vy
                    ) *
                    velocityFactor

        val vzKmS =
            (
                    rdotk * uz +
                            rfdotk * vz
                    ) *
                    velocityFactor

        return TemEState(
            xKm = xKm,
            yKm = yKm,
            zKm = zKm,
            vxKmS = vxKmS,
            vyKmS = vyKmS,
            vzKmS = vzKmS
        )
    }

    /**
     * Validate the essential physical ranges of a TLE.
     *
     * This does NOT validate the TLE checksum because this class receives
     * already-parsed orbital elements rather than raw TLE text.
     */
    private fun validateTle(
        tle: TLEData
    ) {
        require(tle.epochDay >= 1.0) {
            "Invalid TLE epoch day: ${tle.epochDay}"
        }

        require(
            tle.epochDay < 367.0
        ) {
            "Invalid TLE epoch day: ${tle.epochDay}"
        }

        require(
            tle.inclinationDeg in 0.0..180.0
        ) {
            "Invalid inclination: ${tle.inclinationDeg}"
        }

        require(
            tle.eccentricity >= 0.0 &&
                    tle.eccentricity < 1.0
        ) {
            "Invalid eccentricity: ${tle.eccentricity}"
        }

        require(
            tle.meanMotion > 0.0
        ) {
            "Invalid mean motion: ${tle.meanMotion}"
        }

        require(
            tle.bStar.isFinite()
        ) {
            "Invalid B* drag term: ${tle.bStar}"
        }
    }

    /**
     * Convert TLE epoch year/day-of-year to UTC milliseconds.
     *
     * TLE years are two digits:
     *
     *   00..56 -> 2000..2056
     *   57..99 -> 1957..1999
     *
     * The TLE epoch day is one-based.
     */
    private fun tleEpochToMs(
        year: Int,
        dayFrac: Double
    ): Long {

        val fullYear =
            when {
                year < 57 -> 2000 + year
                year < 100 -> 1900 + year
                else -> year
            }

        val calendar =
            java.util.Calendar
                .getInstance(
                    java.util.TimeZone
                        .getTimeZone("UTC")
                )
                .apply {

                    clear()

                    set(
                        java.util.Calendar.YEAR,
                        fullYear
                    )

                    set(
                        java.util.Calendar.MONTH,
                        java.util.Calendar.JANUARY
                    )

                    set(
                        java.util.Calendar.DAY_OF_MONTH,
                        1
                    )

                    set(
                        java.util.Calendar.HOUR_OF_DAY,
                        0
                    )

                    set(
                        java.util.Calendar.MINUTE,
                        0
                    )

                    set(
                        java.util.Calendar.SECOND,
                        0
                    )

                    set(
                        java.util.Calendar.MILLISECOND,
                        0
                    )
                }

        val jan1Ms =
            calendar.timeInMillis

        val offsetMs =
            ((dayFrac - 1.0) *
                    86400000.0)
                .roundToLong()

        return jan1Ms + offsetMs
    }

    /**
     * Normalize angle into [0, 2π).
     */
    private fun normalizeRadians(
        value: Double
    ): Double {

        var result =
            value % TWO_PI

        if (result < 0.0) {
            result += TWO_PI
        }

        return result
    }
}
