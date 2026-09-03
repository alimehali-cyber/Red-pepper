package com.zig.gravity.physics

/**
 * ZIG Gravity Sandbox — Constants Charter (Roadmap v5.0, §3.2).
 *
 * PHYSICAL CONSTANTS (G, C_LIGHT, masses, radii, AU): real SI values.
 *   Never change. Never expose in the UI.
 *
 * NUMERICAL-STABILITY PARAMETERS (EPS_SOFT, DT, BASE, MAX_SUBSTEPS,
 * REFINEMENT_*): tunable numerics, NOT physics. Tunable only by the owner
 *   through a roadmap revision. Never expose in the UI.
 *
 * All engine math is Double, SI. Float is allowed only at the draw boundary.
 */
object EngineConstants {

    // ---- Physical constants (CODATA 2018 / educational roundings) ----
    const val G: Double = 6.67430e-11            // m^3 kg^-1 s^-2 (CODATA 2018)
    const val C_LIGHT: Double = 2.99792458e8     // m/s (exact)

    const val M_SUN: Double = 1.989e30           // kg
    const val R_SUN: Double = 6.957e8            // m
    const val M_EARTH: Double = 5.972e24         // kg
    const val R_EARTH: Double = 6.371e6          // m
    const val M_MOON: Double = 7.348e22          // kg
    const val R_MOON: Double = 1.737e6           // m

    const val AU: Double = 1.496e11              // m

    // ---- Reference values (presets / teaching copy) ----
    const val EARTH_ORBITAL_SPEED: Double = 29.78e3    // m/s  (= sqrt(G*M_SUN/AU))
    const val MOON_ORBIT_RADIUS: Double = 3.844e8      // m
    const val MOON_PERIOD_SECONDS: Double = 27.32 * 24.0 * 3600.0
    const val MOON_ORBITAL_SPEED: Double = 1.022e3     // m/s

    // ---- Numerical-stability parameters (NOT physics; never in UI) ----
    const val EPS_SOFT: Double = 1.0e6           // m — Plummer softening length
    const val DT: Double = 3600.0                // s — fixed simulation timestep (never scaled)
    const val BASE_SIM_SECONDS_PER_REAL_SECOND: Double = 1.0e6
    const val MAX_SUBSTEPS: Int = 96             // per rendered frame, incl. refined inner steps
    const val MAX_BODIES: Int = 20               // hard cap
    const val VELOCITY_HARD_CAP: Double = 1000.0e3   // m/s — engine-level clamp
    const val WORMHOLE_COOLDOWN_SIM_SECONDS: Double = 5.0e5
    const val REFINEMENT_TRIGGER_FRACTION: Double = 0.2
    const val REFINEMENT_MAX_DEPTH: Int = 3
    const val MARBLE_RESTITUTION: Double = 0.4

    // ---- Scene scale (single source of truth) ----
    const val VIEWPORT_WIDTH_AU: Double = 3.0    // visible width of the scene
}
