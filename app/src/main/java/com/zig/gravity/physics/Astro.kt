package com.zig.gravity.physics

/** True Schwarzschild radius r_s = 2GM/c². Self-consistent with the charter constants. */
fun schwarzschildRadius(massKg: Double): Double =
    2.0 * EngineConstants.G * massKg / (EngineConstants.C_LIGHT * EngineConstants.C_LIGHT)
