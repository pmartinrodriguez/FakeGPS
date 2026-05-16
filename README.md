# FakeGPS — Q•3 Software
## Guía de instalación y compilación

---

## Requisitos previos

- **Android Studio** (Ladybug 2024.2+ recomendado)
  → https://developer.android.com/studio
- **JDK 17** (incluido en Android Studio)
- **Android SDK 26+** (Android 8.0 mínimo)
- Tu móvil con **opciones de desarrollador activadas**

---

## Pasos de configuración en Android Studio

### 1. Crear el proyecto
- Abre Android Studio → New Project
- Selecciona **Empty Views Activity**
- Nombre: `FakeGPS`
- Package: `com.q3software.fakegps`
- Language: **Kotlin**
- Min SDK: **API 26**
- Haz clic en Finish

### 2. Reemplazar archivos
Sustituye los archivos generados por los del proyecto:

```
app/
├── build.gradle.kts          ← reemplazar
├── src/main/
│   ├── AndroidManifest.xml   ← reemplazar
│   ├── java/com/q3software/fakegps/
│   │   ├── MainActivity.kt   ← reemplazar
│   │   └── MockLocationService.kt  ← crear nuevo
│   └── res/
│       ├── layout/activity_main.xml  ← reemplazar
│       ├── values/colors.xml         ← reemplazar
│       ├── values/strings.xml        ← reemplazar
│       ├── values/themes.xml         ← reemplazar
│       └── drawable/bottom_sheet_bg.xml  ← crear nuevo
```

### 3. Agregar íconos vectoriales
En Android Studio: res/ → clic derecho → New → Vector Asset

Crea estos dos íconos:
- `ic_location` → busca "location_on" → color #2979FF
- `ic_stop` → busca "stop" → color #FFFFFF

### 4. Verificar libs.versions.toml
En `gradle/libs.versions.toml` asegúrate de tener:
```toml
[versions]
agp = "8.7.0"
kotlin = "2.0.21"
coreKtx = "1.13.1"
appcompat = "1.7.0"
material = "1.12.0"
constraintlayout = "2.1.4"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-appcompat = { group = "androidx.appcompat", name = "appcompat", version.ref = "appcompat" }
material = { group = "com.google.android.material", name = "material", version.ref = "material" }
androidx-constraintlayout = { group = "androidx.constraintlayout", name = "constraintlayout", version.ref = "constraintlayout" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
```

### 5. Compilar
- Conecta tu móvil por USB (o usa emulador)
- Haz clic en **▶ Run** o `Shift+F10`

---

## Configuración en el móvil

### Antes de usar la app:
1. **Opciones de desarrollador** → activas
2. **Seleccionar app de ubicación simulada** → elige **FakeGPS**

### Uso:
1. Toca el mapa para seleccionar punto
2. Opcionalmente escribe coordenadas: `40.4168, -3.7038`
3. Ajusta el timer (0 = sin límite)
4. Pulsa **▶ Activar Mock**
5. Todas las apps verán esa ubicación

---

## Funcionalidades

| Función | Descripción |
|---|---|
| Mapa OSMDroid | Sin API key, tiles de OpenStreetMap |
| Tap en mapa | Selecciona coordenadas visualmente |
| Input manual | Introduce lat/lng directamente |
| Timer | Auto-desactivación en X minutos |
| Favoritos | Guarda ubicaciones con nombre |
| Notificación | Muestra coordenadas activas, botón detener |
| Servicio | Corre en segundo plano aunque cierres la app |

---

## Notas técnicas

- Inyecta ubicación en `GPS_PROVIDER` y `NETWORK_PROVIDER`
- Frecuencia de actualización: **500ms**
- Servicio en primer plano (`ForegroundService`) para evitar que Android lo mate
- Datos guardados en `SharedPreferences` (favoritos + caché OSM)
- Sin root requerido en Android 6+

---

*Q•3 Software & INC. — FakeGPS v1.0*
