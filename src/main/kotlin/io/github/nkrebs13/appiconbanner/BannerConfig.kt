package io.github.nkrebs13.appiconbanner

/**
 * A resolved banner for one Android variant or one iOS configuration: the ribbon background
 * [color] (`#RRGGBB` hex, e.g. `#0288D1`) and the [label] text drawn on top.
 */
data class BannerConfig(
    val color: String,
    val label: String,
)
