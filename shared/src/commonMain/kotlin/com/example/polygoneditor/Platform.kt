package com.example.polygoneditor

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform