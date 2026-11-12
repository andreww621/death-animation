# CinematicDeath (Paper 1.21.4+, Java 21)

Alur: mati -> kedip + jatuh -> buka mata, kamera orbit -> badan naik + partikel -> void (10 menit)
-> bangkit: badan turun dari langit + partikel -> hidup di respawn point.

## Build
- Lokal:   `mvn package`  -> `target/CinematicDeath.jar`
- Tanpa install apa2: upload folder ini ke GitHub, tab Actions -> "Build plugin" -> download artifact `CinematicDeath-jar`.

## Setup
1. Taruh jar di `plugins/`, restart.
2. Berdiri di tempat void (harus ada platform) -> `/cdeath setvoid`
3. (opsional) `/cdeath setrespawn` kalau mau titik bangkit tetap.
4. Tes: `/cdeath test <nama>`.  Admin revive: `/cdeath revive <nama>`.
Semua teks/durasi ada di `plugins/CinematicDeath/config.yml`.
