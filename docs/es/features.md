---
title: Características
lang: es
ref: features
description: Arquitectura de Termux Ultra, motor de terminal, interfaz Miuix, mecanismo de herramientas integradas, matriz de capacidades y modelo de permisos de plugins, asistente de IA, motor de seguridad VorteX Guard, internals de VNC/SSH, y stack tecnológico.
---

## 1. Panorama general de la arquitectura

Termux Ultra conserva la terminal nativa de Termux y le añade encima un conjunto de subsistemas mejorados:

| Subsistema | Ubicación | Responsabilidad |
|-----------|----------|----------------|
| Núcleo de la terminal | `libterminal` | Emulación de terminal y renderizado de pantalla |
| Shell de la app | `app/src/main/java/com/termux/app/` | `TermuxActivity`, `TermuxService`, sistema de notificaciones |
| UI en Compose | `app/.../app/compose/` | Inicio, archivos, remoto, recursos, ajustes, asistente de IA |
| Sistema de plugins | `app/.../app/plugin/` | Carga de plugins, permisos, puente de acciones, renderizado en Compose |
| VNC | `app/.../app/vnc/`, `app/src/main/cpp_avnc/` | Cliente AVNC y libvncserver |
| SSH | `app/.../app/ssh/` | Gestión de conexiones con la sshlib de connectbot |
| FTP | `app/.../app/ftp/` | Servidor FTP integrado |
| Plugins integrados | `vendor/termux-addons/` | Fuentes de API / Boot / Styling / Tasker / Widget |
| Biblioteca compartida | `termux-shared/` | Constantes y utilidades entre módulos (`TermuxConstants`) |

## 2. Motor de la terminal

**LibTerminal** es el motor central de la terminal y se encarga de la emulación de terminal y del renderizado de pantalla. La versión actual es **3.1.1** (evolucionada a partir de 3.0.0). Destaca en el desplazamiento, la salida masiva y el manejo de secuencias de escape.

## 3. UI: Jetpack Compose + Miuix

La interfaz está hecha enteramente con Jetpack Compose y usa el lenguaje de diseño **Miuix (HyperOS)**:

- **TopAppBar y barra inferior flotante** replican el estilo y las animaciones nativas de HyperOS, con detección automática de la versión del sistema
- **Barra de navegación de cristal / luz suave / flotante**: más blanca en modo claro, más oscura en modo oscuro, con un indicador de página arrastrable
- **TabRow unificado** con animaciones de transición entre páginas, **retroceso predictivo** y gestos de deslizamiento horizontal
- **Pantallas de ajustes** construidas sobre la suite ArrowPreference de Miuix, con radios de tarjeta consistentes y recorte del feedback de pulsación
- Adaptación a modo oscuro / claro y varios idiomas (chino / inglés, con cobertura total del chino)

Versiones de dependencias: Jetpack Compose `1.8.3`, Material 3 `1.3.0`, Miuix KMP `0.9.4` (ui / icons / preference).

## 4. Mecanismo de herramientas integradas

Las fuentes de cinco plugins de Termux viven en `vendor/termux-addons/` como **herramientas integradas que se pueden activar y desactivar**; no hacen falta APKs aparte.

Notas de implementación:

- Cada componente integrado se declara en `AndroidManifest.xml` con `android:enabled="false"` por defecto.
- En tiempo de ejecución, el singleton `IntegratedTools` gestiona la activación/desactivación mediante `setEnabled()` + `applyComponentState()`, que por debajo llama a `PackageManager.setComponentEnabledSetting()`.
- `IntegratedTools.componentsFor()` registra el mapeo de componentes; **nunca llames a PackageManager directamente**: pasa siempre por el singleton.
- Antes de activar, la app comprueba si el APK oficial independiente correspondiente ya está instalado; si hay conflicto, desactiva el interruptor y te avisa.
- Termux:Styling usa el **nombre de paquete fusionado `com.termux`** en lugar del original `com.termux.styling`.

## 5. Sistema de plugins

### 5.1 Matriz de capacidades

| Capacidad | Descripción |
|------------|-------------|
| Tarjetas en la página de recursos | Agregar entradas respaldadas por acciones `SHELL_COMMAND` / `OPEN_URL` / `HOST_ACTION` / `CUSTOM` |
| Elementos de ajustes | Agregar o modificar entradas de ajustes |
| Habilidades del agente | Inyectar habilidades personalizadas en el asistente de IA |
| UI en H5 | Interfaz WebView de varias páginas (inicio + subpáginas) |
| Páginas en Compose | Páginas JSON-DSL renderizadas de forma nativa, **sin WebView** |
| Bloqueo de funciones | Deshabilitar funciones del sistema, entradas de ajustes o páginas de navegación |
| Prompt del sistema | Estrategias `APPEND` / `MODIFY` / `OVERWRITE` |
| Puente entre apps | Broadcast, ContentProvider, Webhook |

### 5.2 Modelo de permisos

| Permiso | Descripción | Riesgo |
|------------|-------------|------|
| `TERMUX_SESSION_ACCESS` | Leer/escribir sesiones de terminal, abrir sesiones persistentes | Medio |
| `ROOT_EXECUTE` | Ejecutar comandos con privilegios ROOT | Alto |
| `FILE_SYSTEM_READ` | Leer el sistema de archivos | Medio |
| `FILE_SYSTEM_WRITE` | Escribir en el sistema de archivos | Alto |
| `AGENT_MODIFY` | Modificar el comportamiento del agente y el prompt del sistema | Alto |
| `H5_WEBVIEW` | Cargar la página de inicio en H5 | Bajo |
| `CROSS_APP_BRIDGE` | Mensajería entre aplicaciones | Medio |
| `INTERNET_ACCESS` | Acceso a la red | Bajo |

### 5.3 Política de seguridad

- La instalación valida `manifest.json` y **la existencia de cada archivo referenciado por una `entry`**; los archivos faltantes abortan la instalación.
- `minHostVersion` tiene como valor predeterminado `2.0.0`; los hosts más antiguos rechazan la instalación.
- El modo de prompt del sistema `OVERWRITE` es de **riesgo extremadamente alto** y muestra una confirmación `PluginOverwriteDialog`.
- La interfaz de gestión permite instalar, activar/desactivar, inspeccionar la configuración y desinstalar.

### 5.4 Protocolo unificado de acciones

`ActionExecutor` entiende cuatro prefijos, que se usan directamente en `onClick` / `onChange` de los nodos de Compose:

| Prefijo | Ejemplo | Significado |
|--------|---------|---------|
| `shell:` | `shell:uname -a` | Ejecutar un comando en el shell del plugin |
| `action:` | `action:open_vnc_settings` | Llamar a una entrada del `HostActionRegistry` del host |
| `nav:` | `nav:page_about` | Navegar a otra página del mismo plugin (por `pages[].id`) |
| `http(s)://` | `https://example.com` | Abrir en el navegador externo |
| `{value}` | `shell:echo {value}` | En `switch.onChange`, se reemplaza por el booleano actual |

### 5.5 Sesiones persistentes

`PluginPersistentSession` ofrece un shell de larga duración (a diferencia de `PluginManager.executeShellCommand()`, que crea un proceso nuevo cada vez):

- Lecturas incrementales de la transcripción (`readNew()` / `readAll()` / `resetReadCursor()`)
- Enviar Ctrl+C (`interrupt()`) y EOF (`sendEof()`)
- Consultar `cwd` / `pid` / `exitCode` / `isRunning`
- Registrada automáticamente en `TermuxService.mTermuxSessions`, así que la página de terminal también puede gestionarla
- Al salir, `PluginPersistentSessionRegistry` resuelve el id de sesión mediante `TerminalSession.mHandle` y hace la limpieza
- El acceso entre plugins está bloqueado por la comprobación de `pluginId`

## 6. asistente de IA

- **Interacción en lenguaje natural**: maneja la terminal, el sistema de archivos y las conexiones remotas conversando
- **Sistema de habilidades**: crear/cerrar sesiones, ejecutar comandos, leer/escribir archivos, conexiones VNC/SSH, gestión de máquinas virtuales QEMU
- **Varios modelos**: API compatible con OpenAI o endpoints propios, con `temperature` configurable
- **Seguridad**: detección de operaciones peligrosas (`rm -rf`, `dd`, bombas de fork, …) con confirmación
- **Contexto**: lee información de sesiones activas, listados de archivos y resultados de comandos
- **Visualización del pensamiento profundo**: muestra el razonamiento cuando el modelo lo soporta
- **Extensión por plugins**: los plugins pueden agregar habilidades y modificar el prompt del sistema

## 7. Seguridad: VorteX Guard Engine (VGE)

El antiguo módulo de protección mejorada se reescribió como **VorteX Guard Engine**: una arquitectura de shell hooks nueva, con detección más precisa y cero interferencia en la terminal.

- **Aislamiento de la comunicación TCP**: toda la comunicación con el servidor se ejecuta en un subshell; el proceso padre no ve ningún cambio de descriptores de archivo, así que el termios del PTY nunca se corrompe
- **Trampa DEBUG segura**: `extdebug` ya no se habilita globalmente: se activa solo momentáneamente cuando una denegación omite un comando, y se fuerza a desactivar antes de `PROMPT_COMMAND`
- **Sobrescritura de funciones en lugar de trampas**: `su` / `sudo` / `dd` / `mkfs` y otros comandos de palabras riesgosas se interceptan mediante sobrescrituras de funciones, dejando intactos los internos de bash
- **Período de gracia de inicialización**: los scripts de inicio de OMB/OMZ pasan sin interferencia mientras carga el módulo de seguridad
- **Corrección de termios del PTY**: la capa JNI establece explícitamente `ECHO|ICANON|ISIG`, evitando que el `stty` de toybox de Android sea ineficaz en algunos dispositivos
- **Adaptación a OMB/OMZ**: detecta automáticamente oh-my-bash / oh-my-zsh y se registra mediante sus hooks nativos `preexec`/`precmd`, sin interferir con la trampa DEBUG
- **Confirmación de comandos riesgosos**: cuatro modos: `OFF`, `WARN_ONLY`, `AUTO_BLOCK`, `WARN_VERIFY`
- **Ajustes en vivo**: cambiar de modo reinicia de inmediato los hooks y `SecuritySocketServer`, sin necesidad de reiniciar la app
- **Cobertura de detección de scripts**: scripts de `bash`/`sh`/`zsh`/`ksh`/`dash`/`fish`, ejecución directa de `./script.sh` y combinaciones peligrosas de argumentos como `rm -rf /` o `chmod 777`
- **Corrección del BOM UTF-8**: maneja scripts escritos en Windows que llevan BOM, que antes rompían `bash source`

## 8. Internals de VNC / SSH

| Módulo | Dependencias | Notas |
|--------|--------------|-------|
| VNC | AVNC, libvncserver, libjpeg-turbo, wolfSSL | Zoom con pellizco, varios modos de entrada, teclas especiales, configuración de formato de color, escaneo automático de puertos locales |
| SSH | sshlib de connectbot `2.2.36` | Gestión de varios perfiles, instalación automática de `ssh`/`sshpass`, reenvío de puertos local, verificación de la clave del host, reintento con varias IP |

Objetivos nativos de CMake: `native-vnc`, `vncclient`, `turbojpeg-static`, `wolfssl`, `termux`.

El entorno de bootstrap ya no viene incrustado en el APK: en la primera ejecución se descarga el zip correspondiente según la ABI del dispositivo (con respaldo de múltiples mirrors y verificación SHA-256) y se extrae, ahorrando unos 28 MB en cada paquete por ABI.

## 9. Notificaciones LiveUpdate

- Progreso de descarga mostrado en **segmentos**
- Visualización del estado de razonamiento del agente
- Mejoras en las notificaciones del gestor de paquetes
- La base v0.119 introduce `POST_PROMOTED_NOTIFICATIONS` (Android 15+), con una capa de compatibilidad que recurre a las notificaciones normales en `sdk_int < 36`

## 10. Stack tecnológico

| Categoría | Tecnología |
| --- | --- |
| Lenguajes | Kotlin, Java, C/C++ |
| UI | Jetpack Compose 1.8.3, Material 3 1.3.0, Miuix KMP 0.9.4 (ui / icons / preference) |
| Arquitectura | AndroidX, Lifecycle 2.8.5, ViewModel, Navigation, Room 2.7.2, DataBinding |
| Terminal | libterminal |
| VNC | AVNC, libvncserver, libjpeg-turbo, wolfssl |
| SSH | sshlib de connectbot 2.2.36 |
| Carga de imágenes | Coil Compose 2.7.0 |
| Biométricos | AndroidX Biometric 1.2.0-alpha05 |
| Serialización | Gson 2.10.1, kotlinx-serialization 1.9.0 |
| Asistente de IA | API compatible con OpenAI, endpoints propios, sistema de habilidades |
| Sistema de plugins | Empaquetado ZIP, configuración JSON, puente WebView, puente Broadcast |
| Compilación | Gradle, CMake 3.22.1, NDK 22.1.7171670 |
| Plugins integrados | termux-api, termux-boot, termux-styling, termux-tasker, termux-widget |
| Nombre de paquete | `com.termux` (sharedUserId) |

## 11. Compilar este proyecto

Requisitos: JDK 21, Android SDK (compileSdk 37), NDK `22.1.7171670`, CMake `3.22.1`.

> Para evitar problemas de NDK causados por espacios en las rutas, trabaja mediante una **ruta con enlaces duros y sin espacios** (por ejemplo `D:\KiTerminal-UX`).

```bash
# Debug build (universal APK only)
./gradlew assembleDebug

# Release build (per-ABI APKs)
./gradlew assembleRelease
```

Salida:

- Debug: `app/build/outputs/apk/debug/termux-ultra_debug_universal.apk`
- Release: APKs por ABI en `app/build/outputs/apk/release/`

Firma: el proyecto incluye `ki-terminal-release.jks` (alias `ki-terminal`), usado tanto para Debug como para Release.

> No pases `-q` al compilar, así puedes ver el progreso.
