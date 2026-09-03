package com.zig.gravity.ui

import android.content.Context
import com.zig.gravity.sim.SimSaver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class FileSimSaver(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : SimSaver {

    private val saveFile: File
        get() = File(context.filesDir, "zig_gravity_save.json")

    override fun save(json: String?) {
        scope.launch(Dispatchers.IO) {
            try {
                if (json == null) {
                    if (saveFile.exists()) {
                        saveFile.delete()
                    }
                } else {
                    saveFile.writeText(json)
                }
            } catch (_: Exception) {
                // Ignore IO errors gracefully
            }
        }
    }

    override fun load(): String? {
        return try {
            if (saveFile.exists()) {
                saveFile.readText()
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
