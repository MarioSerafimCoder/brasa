package com.brasa.tv.core.playback

/** UI render resolution is only a fallback, never a ceiling for a known physical mode. */
fun videoOutputSize(uiWidth: Int, uiHeight: Int, physicalWidth: Int, physicalHeight: Int): Pair<Int, Int> =
    if (physicalWidth > 0 && physicalHeight > 0) maxOf(physicalWidth, physicalHeight) to minOf(physicalWidth, physicalHeight)
    else maxOf(uiWidth, uiHeight) to minOf(uiWidth, uiHeight)
