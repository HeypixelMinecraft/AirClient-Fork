package net.ccbluex.liquidbounce.features.module.modules.music

import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module

object PreviousTrack : Module("上一首", Category.MUSIC, canBeEnabled = false) {

    override fun onEnable() {
        super.onEnable()
        MusicPlayer.playPrevious()
    }
}
