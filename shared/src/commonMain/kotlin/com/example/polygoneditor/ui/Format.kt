package com.example.polygoneditor.ui

import kotlin.math.round

// One decimal place; String.format is JVM-only, so it can't be used in commonMain
internal fun Double.fmt() = (round(this * 10) / 10).toString()
