package com.hitster.mobile.data

import android.content.Context
import java.util.UUID

/** Small persistent settings: identity for reconnects, last address, last session. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("hitster", Context.MODE_PRIVATE)

    val playerId: String
        get() = sp.getString("playerId", null) ?: UUID.randomUUID().toString().also { sp.edit().putString("playerId", it).apply() }

    var name: String
        get() = sp.getString("name", "") ?: ""
        set(v) = sp.edit().putString("name", v).apply()

    var color: String
        get() = sp.getString("color", PALETTE.first()) ?: PALETTE.first()
        set(v) = sp.edit().putString("color", v).apply()



    /** Last host address typed manually ("ip:port"). */
    var lastAddress: String?
        get() = sp.getString("lastAddress", null)
        set(v) = sp.edit().putString("lastAddress", v).apply()

    var lastRoom: String?
        get() = sp.getString("lastRoom", null)
        set(v) = sp.edit().putString("lastRoom", v).apply()

    companion object {
        val PALETTE = listOf("#FF2D8F", "#FF6B2B", "#FFD23F", "#23C36B", "#00E5FF", "#2D7DF6", "#8E44FF", "#FF4757", "#F78FB3", "#7BED9F")
    }
}
