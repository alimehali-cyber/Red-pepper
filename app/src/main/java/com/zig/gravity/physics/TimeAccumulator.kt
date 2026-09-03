package com.zig.gravity.physics

/** Fixed-timestep accumulator (pure, deterministic, JVM-testable).
 *  Speed changes HOW MUCH sim-time accrues per real second — DT never changes. */
class TimeAccumulator(
    private val dt: Double = EngineConstants.DT,
    private val base: Double = EngineConstants.BASE_SIM_SECONDS_PER_REAL_SECOND,
    private val maxSteps: Int = EngineConstants.MAX_SUBSTEPS,
) {
    private var accumulator = 0.0
    var speedMultiplier: Double = 1.0

    fun onFrame(frameDeltaSeconds: Double) {
        accumulator += minOf(frameDeltaSeconds, 0.1) * base * speedMultiplier
    }

    /** Runs base steps while budget remains. [stepFn] must execute ONE base
     *  engine step of size dt and return the inner-step count it consumed.
     *  Inner refined steps charge the SAME budget. Returns base steps run. */
    fun pump(stepFn: (Double) -> Int): Int {
        var baseSteps = 0
        while (accumulator >= dt && baseSteps < maxSteps) {
            val inner = stepFn(dt)
            accumulator -= dt
            baseSteps += 1
            if (inner > 1) {
                // refined steps charge the budget: count them as extra base steps
                val extra = inner - 1
                if (extra > 0) {
                    baseSteps += extra
                    if (baseSteps >= maxSteps) break
                }
            }
        }
        if (baseSteps >= maxSteps) accumulator = 0.0   // discard debt, never spiral
        return baseSteps
    }

    fun reset() {
        accumulator = 0.0
    }
}
