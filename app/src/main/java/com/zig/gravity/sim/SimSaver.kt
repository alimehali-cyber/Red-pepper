package com.zig.gravity.sim

interface SimSaver {
    fun save(json: String?)
    fun load(): String?
}
