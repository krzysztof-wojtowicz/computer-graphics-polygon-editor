import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    // Natywne biblioteki Skii dla pozostałych systemów – dzięki temu uber-JAR
    // zbudowany na Macu uruchomi się też na Windowsie i Linuksie.
    implementation(compose.desktop.windows_x64)
    implementation(compose.desktop.linux_x64)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "com.example.polygoneditor.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "PolygonEditor"
            packageVersion = "1.0.0"
        }
    }
}