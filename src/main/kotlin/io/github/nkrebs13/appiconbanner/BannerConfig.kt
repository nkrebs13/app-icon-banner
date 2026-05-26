package io.github.nkrebs13.appiconbanner

import java.io.Serializable

/**
 * A resolved banner for one Android variant or one iOS configuration: the ribbon background
 * [color] (`#RRGGBB` or `#AARRGGBB`) and the [label] text drawn on top.
 */
data class BannerConfig(
    val color: String,
    val label: String,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}
