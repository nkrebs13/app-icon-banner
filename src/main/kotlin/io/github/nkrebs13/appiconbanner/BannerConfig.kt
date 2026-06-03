package io.github.nkrebs13.appiconbanner

/**
 * A resolved banner for one Android variant or one iOS configuration: the ribbon background
 * [color] (`#RRGGBB` hex, e.g. `#0288D1`) and the [label] text drawn on top.
 *
 * [textSizePct] overrides the default 55% text-size-to-band-height ratio when non-null.
 * [bold] prefers bold system font variants when `true` (Android only).
 */
data class BannerConfig(
    val color: String,
    val label: String,
    val textSizePct: Int? = null,
    val bold: Boolean = false,
)
