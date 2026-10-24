# Polygon Editor

A desktop polygon editor written in Kotlin with Compose Multiplatform.

Features:

- editing polygons by dragging vertices, edges, control points and whole polygons; inserting and deleting vertices,
- edges shaped as line segments, cubic Bézier curves or circular arcs,
- constraints on edges (fixed length and perpendicularity), kept satisfied by an iterative constraint solver,
- continuity classes G0 / G1 / C1 at vertices,
- line drawing with the library or a custom Bresenham algorithm,
- built-in documentation in English and Polish (**F1**).

## Requirements

- JDK 17 or newer (tested with JDK 21), with `JAVA_HOME` pointing to it or `java` on the `PATH`.

Gradle does not need to be installed: the Gradle wrapper (`gradlew`) downloads it on first use. An internet connection is needed for the first build.

## Running

From the project root:

```bash
# macOS / Linux
./gradlew :desktopApp:run

# Windows
gradlew.bat :desktopApp:run
```

The first run takes a few minutes while Gradle downloads its dependencies. After that the application window opens with a demo scene.

You can also open the project in IntelliJ IDEA and use the `desktopApp` run configuration.

## Project structure

- `shared/` – the application: geometry, model, constraint solver, editor and UI. The documentation shown in the app lives in `shared/src/commonMain/composeResources/files/` (`help_ang.md`, `help_pl.md`).
- `desktopApp/` – the desktop entry point (`main.kt`).

---

**Author:** Krzysztof Wójtowicz<br>
**Course:** Computer Graphics (Grafika Komputerowa) @ MiNI WUT