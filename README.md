# Pembuat Aplikasi (AndroLua 5.4.8)

Aplikasi Pembuat Aplikasi berbasis mesin AndroLua (Lua 5.4.8).

## Isi
- `app/src/main/assets/main.lua` — bootstrap
- `app/src/main/assets/garuda/` — system.lua, decoder.lua, executor.lua, ui.lua

## Build
Push ke GitHub, jalankan workflow **Kompilasi APK Pembuat Aplikasi**.

Atau lokal (butuh NDK):
```
./gradlew :app:assembleDebug
```
