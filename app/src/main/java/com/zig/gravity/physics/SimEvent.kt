package com.zig.gravity.physics

sealed class SimEvent {
    data class BodyAdded(val id: Long) : SimEvent()
    data class BodyRemoved(val id: Long) : SimEvent()
    object NumericalFailure : SimEvent()
    data class BodyMerged(val idKept: Long, val idGone: Long, val mergedMassKg: Double, val blackHole: Boolean) : SimEvent()
    data class WormholeTraversal(val bodyId: Long, val fromMouthId: Long, val toMouthId: Long) : SimEvent()
}
