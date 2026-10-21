package org.torproject.vpn.ui.iconsetting.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import org.torproject.vpn.R

sealed interface Launcher {
    val id: String

    @get:DrawableRes
    val drawableForeground: Int

    @get:DrawableRes
    val drawableBackground: Int

    @get:StringRes
    val label: Int

    companion object {
        val entries: List<Launcher>
            get() = listOf(LauncherDefault, LauncherWeather, LauncherGarden, LauncherPingPong)
    }
}

data object LauncherDefault : Launcher {
    override val id: String = "Default"
    override val drawableForeground: Int = R.drawable.ic_launcher_foreground
    override val drawableBackground: Int = R.drawable.ic_launcher_background
    override val label: Int = R.string.app_name
}

data object LauncherGarden : Launcher {
    override val id: String = "Garden"
    override val drawableForeground: Int = R.drawable.ic_launcher_garden_foreground
    override val drawableBackground: Int = R.drawable.ic_launcher_garden_background
    override val label: Int = R.string.app_name_garden
}

data object LauncherWeather : Launcher {
    override val id: String = "Weather"
    override val drawableForeground: Int = R.drawable.ic_launcher_weather_foreground
    override val drawableBackground: Int = R.drawable.ic_launcher_weather_background
    override val label: Int = R.string.app_name_weather
}

data object LauncherPingPong : Launcher {
    override val id: String = "PingPong"
    override val drawableForeground: Int = R.drawable.ic_launcher_pingpong_foreground
    override val drawableBackground: Int = R.drawable.ic_launcher_pingpong_background
    override val label: Int = R.string.app_name_pingpong
}
