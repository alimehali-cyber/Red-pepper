package com.zig.gravity.sim

import kotlinx.serialization.Serializable

@Serializable
data class BodySave(
    val typeOrdinal: Int,
    val massKg: Double,
    val radiusDp: Double,
    val x: Double,
    val y: Double,
    val vx: Double,
    val vy: Double,
    val userSized: Boolean,
    val userMass: Boolean,
    val partnerIndex: Int = -1
)

@Serializable
data class SimSave(
    val version: Int = 4,
    val presetKey: String?,
    val simTime: Double,
    val speed: Double,
    val trailsEnabled: Boolean,
    val marbleBounce: Boolean,
    val bodies: List<BodySave>,
    val tableSurface: String = "midnight",
    val theme: String = "dark",
    val language: String? = null
)
