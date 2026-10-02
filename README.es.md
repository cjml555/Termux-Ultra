<img width="5643" height="2790" alt="AATUltra" src="https://github.com/user-attachments/assets/d9205d38-6acf-4e9e-8b95-28969322bad1" />

[![License: AGPL-3.0](https://img.shields.io/badge/License-AGPL--3.0-blue.svg)](./LICENSE)
[![Platform: Android](https://img.shields.io/badge/Platform-Android%2010.0%2B-green.svg)]()
[![Based on Termux v0.119.0](https://img.shields.io/badge/Base-Termux%20v0.119.0-orange.svg)](https://github.com/termux/termux-app/releases/tag/v0.119.0-beta.3)
[![v2.1.0.R5](https://img.shields.io/badge/v2.1.0.R5-stable-brightgreen.svg)](https://github.com/TiG-Kira/Termux-Ultra/releases)

[![Build status](https://github.com/TiG-Kira/Termux-Ultra/actions/workflows/ci.yml/badge.svg)](https://github.com/TiG-Kira/Termux-Ultra/actions)
[![Docs](https://img.shields.io/badge/Docs-GitHub%20Pages-blue.svg)](https://tig-kira.github.io/Termux-Ultra/)

<div align="center">

[ <a href="./README.es.md">**Español**</a> · <a href="./README.md">**中文**</a> · <a href="https://tig-kira.github.io/Termux-Ultra/es/">Docs</a> ]

</div>

## Descripción de ramas

| Rama | Base | Versión | Estado |
|------|------|------|------|
| **`main`** 🎯 | Termux upstream `v0.119.0-beta.3` | 3.x.x.R7 | **Línea principal estable**, recibe el desarrollo de funcionalidades, correcciones de errores y optimizaciones de arquitectura |
| `release/r1-r4` | Termux upstream `v0.118.3` | 1.8.0.R4 | 📦 Instantánea histórica de v1.8.x, **sin actualizaciones de funcionalidades**, solo correcciones urgentes de errores bloqueantes |
| `archived/corebump/2.x` | Termux upstream `v0.119.0-beta.3` | 2.0.0.R5 | 📂 Instantánea archivada, conserva los pasos de desarrollo interno de 2.x |

> ✅ **La versión estable 2.1.0.R5 se publicó el 2026-09-19**, basada en Termux upstream v0.119.0, e incluye el motor libterminal, la barra de navegación de cristal, el sistema de plugins, el asistente de IA y otras funcionalidades centrales.
> Dado que upstream no actualiza sus releases desde hace mucho tiempo, la Beta de upstream que superamos las pruebas internas es estable y se usará directamente como base 0.119.0.



**Termux Ultra** es un emulador de terminal y entorno Linux para Android desarrollado en segundo nivel sobre [Termux](https://github.com/termux/termux-app). Mantiene las capacidades nativas de terminal de Termux e incorpora mejoras como escritorio remoto VNC, gestión de conexiones SSH, gestor de archivos, contenedores Linux (proot), máquinas virtuales QEMU, despliegue de recursos con un clic, asistente de IA y sistema de plugins; además, incorporates 5 plugins de Termux (API, Boot, Styling, Tasker, Widget) como herramientas integradas que se activan o desactivan, sin necesidad de instalaciones adicionales. La interfaz de usuario está construida con Jetpack Compose + el lenguaje de diseño Miuix.

> Este repositorio contiene la aplicación en sí (interfaz de usuario, emulación de terminal y funcionalidades extendidas). Para los paquetes instalables dentro de la aplicación, consulta [termux/termux-packages](https://github.com/termux/termux-packages).

***

## Actualizaciones recientes

### Termux Ultra 2.1.0.R5 — 🎉 Versión estable

> 📅 **Publicación estable: 2026-09-19**

- **Motor LibTerminal 3.0.0**: nuevo motor central de terminal, con grandes mejoras de rendimiento y compatibilidad
- **Barra de navegación de cristal / luz suave / flotante**: reproducción del estilo visual nativo de HyperOS, con indicadores que se pueden arrastrar para cambiar de página; el efecto de cristal es más blanco en modo claro y más oscuro en modo oscuro
- **Sistema de plugins v2.0.0**: soporte completo de plugins de terceros, que incluye páginas nativas con Compose JSON DSL, puente de Action del host, sesiones persistentes y gestión de permisos
- **Asistente AI Termux**: asistente de IA integrado, con configuración de múltiples modelos (OpenAI / modelos locales / LLama integrado), sistema de habilidades, visualización del razonamiento profundo y entrenamiento de modelos locales
- **Adaptación al tema de HyperOS**: TopAppBar y barra inferior flotante reproducen al 100 % el estilo y las animaciones nativas de HyperOS, con detección automática de la versión del sistema
- **Optimización integral de UI/UX**: página de ajustes con estilo Miuix, TabRow unificado, animaciones de transición de página, retroceso predictivo y gestos de deslizamiento horizontal para cambiar de página
- **VNC/SSH/Gestión de archivos**: interfaz de búsqueda unificada en las páginas de gestión remota, VNC basado en AVNC + libvncserver y SSH basado en connectbot
- **Despliegue con un clic desde la página de recursos**: scripts de instalación con un clic para contenedores Ubuntu/Debian, máquinas virtuales QEMU, panel Zhuque, entornos Python y más
- **Notificaciones en tiempo real con LiveUpdate**: progreso de descarga por segmentos, estado de reflexión del Agent y optimización de las notificaciones de gestión de paquetes

### Termux Ultra 2.0.0.R5 — 🚀 Gran actualización de la base upstream

> 📅 **Fusionado en `main` el 2026-09-19**, con un único commit grande que cubrió por completo el contenido de la rama 2.x en la línea principal; todo el historial de v1.8.x se conserva.

- **El núcleo de Termux upstream se actualizó de v0.118.3 a v0.119.0-beta.3**: emulador de terminal, TermuxService, sistema de notificaciones y bibliotecas nativas se sincronizaron con el código más reciente de upstream
- **Corrección de las notificaciones en tiempo real de LiveUpdate**: la base v0.119 introdujo la nueva API `POST_PROMOTED_NOTIFICATIONS` (Android 15+), con una capa de compatibilidad para la API antigua (recurso a notificaciones normales cuando `sdk_int < 36`), lo que resolvió el problema de que las notificaciones no aparecieran en la rama 2.x
- **Adaptación completa de canales y permisos de notificación**: se completó la declaración del permiso `POST_PROMOTED_NOTIFICATIONS` en `AndroidManifest.xml` y la construcción de notificaciones en `TermuxService` pasó a usar la nueva API
- **Actualización del trío de Kotlin mediante parches**: compose 2.3.10→2.3.21, serialization 2.3.10→2.3.21, ksp 2.3.10→2.3.12, abriendo camino a la posterior actualización mayor a 2.4.x
- **Simplificación del workflow de CI**: GitHub Actions pasó a usar el `ci.yml` de main (`ubuntu-26.04` + `checkout@v7` + `setup-java@v4`), eliminando la antigua y compleja construcción con matrix
- **Reconfiguración de Dependabot**: se añadió el seguimiento de versiones del ecosistema Gradle, con configuración de ignorados para evitar que el trío de Kotlin suba de versión mayor por separado y quede inconsistente
- **Reorganización de la arquitectura de ramas**: `main` recibe la línea principal de 2.0 beta, `release/r1-r4` guarda la instantánea histórica de v1.8.0 (sin actualizaciones de funcionalidades) y `archived/corebump/2.x` archiva los pasos de desarrollo interno de 2.x

### VorteX Guard Engine (v1.7.0)

> ⚠️ **El módulo de protección reforzada original fue rediseñado y actualizado por completo a VorteX Guard Engine (VGE)**, con una arquitectura de hooks de shell reescrita, detecciones más precisas y cero interferencia en la terminal

- **Reconstrucción del núcleo de VorteX Guard Engine**: la arquitectura de hooks de shell se reescribió por completo, lo que resolvió de raíz problemas como la terminal sin prompt tras ejecuciones largas, la entrada sin eco y los temas de oh-my-bash rotos
- **Aislamiento de la comunicación TCP**: toda la comunicación con el servidor se ejecuta en subshells, sin modificar ningún fd del proceso padre, lo que evita que el termios del PTY se altere por accidente
- **Endurecimiento del trap DEGUB**: extdebug ya no está activo siempre de forma global, solo se activa al instante cuando se omite un comando DENY, y se cierra de forma preventiva en `PROMPT_COMMAND`
- **Sobrescritura de funciones en lugar de traps**: los comandos peligrosos con palabras como su/sudo/dd/mkfs se interceptan sobrescribiendo funciones, sin interferir con el comportamiento interno de bash
- **Periodo de gracia de inicialización**: los scripts de inicialización de OMB/OMZ se dejan pasar automáticamente mientras se carga el módulo seguro, evitando que la inicialización del framework sea interceptada
- **Corrección de termios del PTY**: la capa JNI establece explícitamente ECHO|ICANON|ISIG, lo que resuelve el problema de que el `stty` de toybox en Android no surte efecto en algunos dispositivos
- **Adaptación a OMB/OMZ**: detección automática de oh-my-bash / oh-my-zsh y registro mediante sus interfaces nativas preexec/precmd, sin interferencia de traps DEBUG
- **Doble confirmación para comandos peligrosos**: el modo reforzado admite cuatro modos: OFF (desactivado), WARN_ONLY (solo advertencia), AUTO_BLOCK (bloqueo automático) y WARN_VERIFY (advertencia con diálogo de verificación)
- **Los ajustes surten efecto en tiempo real**: al cambiar el modo reforzado, los hooks y el SecuritySocketServer se reinician de inmediato, sin necesidad de reiniciar la aplicación
- **Cobertura de detección de scripts**: ejecución de scripts bash/sh/zsh/ksh/dash/fish + ejecución directa de ./script.sh + combinaciones de argumentos peligrosos como rm -rf / o chmod 777 /
- **Corrección de UTF-8 BOM**: resuelve el problema de que bash muestre errores al hacer `source` de scripts de shell escritos en Windows con BOM añadido automáticamente

### Sistema de plugins (actualización a v2.0.0)
- **Páginas con Compose JSON DSL**: `pages[].type = "compose"` declara una página de UI renderizada de forma nativa por el `ComposeRenderer` del host (sin WebView), con soporte para nodos column/row/text/card/listItem/switch/button/slider/divider/lazyColumn
- **Puente de Action del host (HostActionRegistry)**: punto de entrada unificado en tres lugares — `action.hostActionId` de las tarjetas de recursos, `onClick: "action:xxx"` de Compose y `hostAction()` de H5 — que permite llamar a páginas nativas del host como `open_vnc_settings`/`open_termux_styling`/`open_termux_tasker`/`open_termux_widget`/`open_plugin_center`/`open_system_settings`
- **API de navegación entre páginas**: dentro de H5, `bridge.navigate(pageId)` salta a cualquier subpágina compose/h5 del mismo plugin
- **Protocolo unificado de cadenas action**: `ActionExecutor` admite cuatro prefijos, `shell:`/`action:`/`nav:`/URL directa; los campos `onClick`/`onChange` de los nodos Compose los usan directamente y el marcador `{value}` se sustituye por el valor actual del interruptor
- **Sesiones persistentes de plugin (PluginPersistentSession)**: la API de Kotlin del lado del host ofrece un shell residente que permite lectura y escritura incremental del transcript, enviar Ctrl+C/EOF y consultar cwd/pid/exitCode; se registra automáticamente en `TermuxService` y `PluginPersistentSessionRegistry` lo limpia al salir
- **minHostVersion sube por defecto a 2.0.0**: los plugins antiguos deben declarar explícitamente `minHostVersion: "1.2.0"` para poder instalarse en hosts de versiones anteriores

### Sistema de plugins (v1.2.0.RB)
- Sistema de plugins totalmente nuevo, con instalación de paquetes de plugin en formato ZIP/TUP
- Los plugins pueden extender: tarjetas de recursos, elementos de ajustes, Agent Skills e interfaces H5 multipágina
- Los plugins pueden bloquear: funcionalidades del sistema, elementos de ajustes y páginas de navegación
- Gestión de permisos de los plugins: ejecución ROOT, acceso a sesiones, lectura y escritura de archivos, interacción entre aplicaciones
- Interfaz de plugins para el Agent: añadir/modificar/sobrescribir el System Prompt, Skills personalizadas
- Puente de interacción entre aplicaciones: Broadcast, ContentProvider, Webhook
- Interfaz H5 multipágina: WebView + JavaScript Bridge (`window.TermuxUltra`)
- Múltiples entradas H5: página principal (h5Home) + subpáginas (array pages), mostradas por separado en el centro de plugins

### Asistente de IA (v118.3.63)
- Asistente de IA integrado, que permite interactuar con la terminal mediante lenguaje natural
- Compatible con APIs compatibles con OpenAI y con la configuración de endpoints personalizados
- Sistema de habilidades: crear/cerrar sesiones, ejecutar comandos, operaciones de archivos, conexiones VNC/SSH y gestión de máquinas virtuales QEMU
- Detección de operaciones peligrosas con mecanismo de doble confirmación
- Visualización del contenido del razonamiento profundo (cuando el modelo lo soporta)

### Mejoras en la gestión de archivos
- Compatible con servidor SFTP, para transferencias de archivos en la red local
- Selección múltiple de archivos y operaciones por lotes
- Iconos por tipo de archivo y panel de detalles optimizado

### Gestión de sesiones de terminal
- Actualización del estado de las sesiones en tiempo real (sondeo cada 400 ms)
- Conservación y consulta de la información de las sesiones finalizadas
- Búsqueda optimizada y tarjeta de bienvenida

***

## Índice

- [Características](#características)
- [Aplicación y plugins](#aplicación-y-plugins)
- [Requisitos del sistema](#requisitos-del-sistema)
- [Instalación](#instalación)
- [Desinstalación](#desinstalación)
- [Estructura del proyecto](#estructura-del-proyecto)
- [Compilación](#compilación)
- [Stack tecnológico](#stack-tecnológico)
- [Guía de desarrollo de plugins](#guía-de-desarrollo-de-plugins)
- [Depuración](#depuración)
- [Mantenedores y colaboradores](#mantenedores-y-colaboradores)
- [Agradecimientos](#agradecimientos)
- [Licencia de código abierto](#licencia-de-código-abierto)

## Características

### Terminal
- Gestión de múltiples sesiones: crear, renombrar, cerrar y cambiar de sesión
- Filtrado en tiempo real desde el cuadro de búsqueda (se busca por título; si no hay resultados se muestra "no encontrado")
- Detección del estado del servicio: monitorea continuamente el estado de ejecución de la terminal, con soporte de Wake Lock para mantenerla viva
- Monitoreo y protección de la memoria: cuando se supera el límite de memoria se congela la sesión, evitando la pérdida de datos
- Aviso de mantenimiento de sesión: en Android 12+ se usa tmux para lograr persistencia en segundo plano

### Gestión de herramientas integradas
Las 5 herramientas de Termux ya vienen integradas en la aplicación, sin necesidad de instalar APKs independientes, y se activan según necesidad en `Ajustes`:
- **Termux:API** — llamadas a funcionalidades del sistema Android (sensores, notificaciones, TTS, etc.)
- **Termux:Boot** — ejecuta automáticamente al arrancar los scripts de `~/.termux/boot/`
- **Termux:Styling** — esquemas de color del terminal y gestión de fuentes (usa el nombre de paquete fusionado `com.termux`)
- **Termux:Tasker** — integración con la automatización de Tasker
- **Termux:Widget** — accesos directos del escritorio y widgets

> Las herramientas están desactivadas por defecto; al activarlas, el componente correspondiente se habilita dinámicamente mediante `PackageManager.setComponentEnabledSetting()` y se deshabilita al desactivarlas. Si el dispositivo ya tiene instalado el APK oficial independiente, el interruptor se deshabilita automáticamente y se avisa del conflicto.

### Gestión de archivos
- Operaciones completas de archivos y carpetas: crear, copiar, cortar, pegar, eliminar y renombrar
- Múltiples formas de abrir: ver el contenido (cat), editar (vi), ejecutar (bash) y copiar la ruta
- Panel de detalles de archivo adaptado al modo oscuro
- Servidor FTP integrado: compatible con transferencias de archivos en la red local
- Deslizar para actualizar

### Gestión remota
- **Escritorio remoto VNC**: basado en AVNC + libvncserver, con zoom por gestos, varios modos de entrada, teclas especiales, configuración de formatos de color y escaneo automático de los puertos VNC locales
- **Gestión de conexiones SSH**: basado en connectbot sshlib, permite guardar, editar y eliminar varias configuraciones de conexión, e instala automáticamente `ssh`/`sshpass`
- **Túneles SSH**: compatible con reenvío de puerto local, verificación de claves de host y reintento con múltiples IP
- Interfaz de búsqueda unificada y gestión por tarjetas

### Contenedores Linux y máquinas virtuales
- **Contenedores Linux**: basados en proot, instala con un clic un entorno Ubuntu (Noble/Jammy) o Debian (Bookworm), compartiendo el directorio home de Termux
- **Máquinas virtuales QEMU**: compatible con instalar QEMU dentro del contenedor o dentro de Termux, ofreciendo virtualización completa del sistema
- **QEMU sobre VNC**: inicia la máquina virtual con QEMU y muestra el escritorio por VNC, con configuración de VM personalizada (CPU, memoria, disco, ISO)
- **Seed ISO**: genera automáticamente el seed ISO para la configuración inicial de la máquina virtual

### Página de recursos (despliegue con un clic)
Scripts de instalación con un clic para entornos y servicios comunes, divididos en un centro de herramientas prácticas y un centro de recursos de terceros:
- Instalación de contenedores Linux (Ubuntu / Debian)
- Instalación de QEMU (dentro del contenedor / dentro de Termux)
- QEMU sobre VNC (máquina virtual + escritorio VNC)
- Panel Zhuque (LightPanel) — despliegue con un clic del panel de administración web
- Despliegue de entornos Python
- tmux (mantiene vivos los contenedores y los proyectos)
- Centro de recursos de terceros: recursos ampliados mantenidos por la comunidad

### Sistema de plugins (v2.0.0)
- **Entrada a los plugins**: página de recursos → centro de plugins
- **Formato de plugin**: empaquetado ZIP (sufijo `.tup`), con validación del manifest y de la existencia de todos los archivos entry al instalar
- **Capacidades del plugin**:
  - Añadir una entrada de tarjeta en la página de recursos (cuatro tipos de action: SHELL_COMMAND / OPEN_URL / HOST_ACTION / CUSTOM)
  - Añadir o modificar elementos de ajustes
  - Proporcionar Agent Skills personalizadas
  - Proporcionar una interfaz H5 multipágina (página principal + subpáginas)
  - Proporcionar **páginas nativas con Compose JSON DSL** (`pages[].type = "compose"`, sin WebView)
  - Bloquear o deshabilitar funcionalidades del sistema
  - Modificar el System Prompt del Agent (APPEND/MODIFY/OVERWRITE)
  - Interacción entre aplicaciones
- **Puente de Action del host**: las tarjetas de recursos, `onClick` de Compose y `hostAction()` de H5 invocan de forma unificada las entradas nativas del host (ajustes de VNC, Styling, Tasker, Widget, centro de plugins, ajustes del sistema, etc.)
- **API de navegación entre páginas**: `navigate(pageId)` dentro de H5 salta a cualquier subpágina compose/h5 del mismo plugin
- **Protocolo unificado de action**: cuatro prefijos, `shell:` / `action:` / `nav:` / URL directa, con marcador `{value}`
- **Sesiones persistentes**: `PluginPersistentSession` del lado del host ofrece un shell residente con lectura y escritura incremental y envío de Ctrl+C/EOF
- **Sistema de permisos**: ejecución ROOT, acceso a sesiones, lectura y escritura de archivos, interacción entre aplicaciones, WebView H5, acceso a red y modificación del Agent
- **Interfaz de gestión**: instalación de plugins, activación/desactivación, visualización de la configuración y desinstalación, con doble confirmación en modo OVERWRITE
- **H5 Bridge**: objeto `window.TermuxUltra`, con APIs como exec/getConfig/setConfig/readFile/hostAction/navigate

### Panel y ajustes
- Tarjeta de información de red: actualiza en tiempo real la IP pública y el país al que pertenece
- Información del dispositivo: modelo, versión de Android y versión del kernel
- Copia de seguridad y restauración de los datos de Termux
- Panel de interruptores de herramientas integradas (incluye detección de conflictos con APKs independientes)
- Autenticación biométrica (desbloqueo con huella)
- Soporte multiidioma (chino / inglés, 100 % de cobertura en chino)
- Adaptación a modo oscuro / claro
- Página de ajustes con estilo Miuix (suite ArrowPreference)

### Asistente de IA
- Interacción en lenguaje natural: conversa con la terminal, el sistema de archivos y las conexiones remotas
- Sistema de habilidades: crear/cerrar sesiones, ejecutar comandos, leer y escribir archivos, conexiones VNC/SSH, gestión de máquinas virtuales QEMU, etc.
- Soporte de múltiples modelos: compatible con la API de OpenAI y endpoints personalizados, con parámetros configurables como temperature
- Mecanismos de seguridad: detección de operaciones peligrosas (rm -rf, dd, fork bomb, etc.) con doble confirmación
- Contexto consciente: puede obtener datos en tiempo real como información de sesión, listas de archivos y resultados de ejecución
- Extensión mediante plugins: los plugins pueden añadir Skills personalizadas y modificar el System Prompt

### Interacción y animaciones
- Gestos de deslizamiento horizontal en la página de inicio para cambiar de página (terminal → archivos → remoto → recursos)
- Animaciones superpuestas y de deslizamiento lateral al cambiar de página, con soporte de retroceso predictivo
- Unificación de las esquinas redondeadas de las tarjetas y del recorte de la respuesta al toque
- Ajuste de márgenes y evitación de la barra de navegación inferior para evitar toques accidentales
- Efectos de barra de navegación de cristal / luz suave / flotante

## Aplicación y plugins

Termux Ultra integra el código fuente de las siguientes 5 herramientas de Termux en la aplicación principal (en `vendor/termux-addons/`), como herramientas integradas que se activan o desactivan, sin necesidad de instalar APKs independientes:

- [Termux:API](https://github.com/termux/termux-api) — integrado
- [Termux:Boot](https://github.com/termux/termux-boot) — integrado
- [Termux:Styling](https://github.com/termux/termux-styling) — integrado (usa el nombre de paquete fusionado `com.termux`)
- [Termux:Tasker](https://github.com/termux/termux-tasker) — integrado
- [Termux:Widget](https://github.com/termux/termux-widget) — integrado

> Las herramientas integradas están desactivadas por defecto y se activan según necesidad en `Ajustes` → `Herramientas integradas`. Si el dispositivo ya tiene instalado el APK oficial correspondiente, el interruptor se deshabilita automáticamente para evitar conflictos.

Desde Termux Ultra v2.0.0 los usuarios pueden instalar plugins de terceros (formato ZIP/TUP); consulta la [Guía de desarrollo de plugins](#guía-de-desarrollo-de-plugins).

## Requisitos del sistema

- Android `>= 8.0` (API 26)
- targetSdk `28`, compileSdk `37`
- Arquitecturas compatibles: `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`

## Instalación

Termux Ultra comparte `sharedUserId` (`com.termux`) con el Termux original y con todos sus plugins, por lo que esta aplicación y todos los APKs de plugins instalados en el dispositivo **deben usar el mismo origen de firma**; de lo contrario no podrán trabajar en conjunto y aparecerán errores como `INSTALL_FAILED_SHARED_USER_INCOMPATIBLE` o `signatures do not match` al instalar.

- No mezcles orígenes (por ejemplo, instala uno desde F-Droid y otro desde GitHub).
- Si necesitas cambiar de origen, **desinstala primero todos los APKs de Termux y sus plugins** que tengas instalados y luego instálalos todos desde el mismo origen nuevo. Antes de desinstalar, conviene consultar [Backing up Termux](https://wiki.termux.com/wiki/Backing_up_Termux) para respaldar los datos.

> "bootstrap" designa el conjunto mínimo de paquetes incluido en `termux-app` para arrancar un entorno shell mínimo; su zip se construye y publica en las releases de [termux/termux-packages](https://github.com/termux/termux-packages/releases).

### Orígenes de los APK
Los canales de distribución son los siguientes:

| <img src="https://avatars.githubusercontent.com/in/15368?s=64&v=4" width = "30" height = "30" alt="LOGO"/> | [GitHub CI](https://github.com/TiG-Kira/Termux-Ultra/actions/workflows/ci.yml) | Construcción automática de CI (versiones de prueba), se construye automáticamente en cada commit; ideal para probar novedades y PR, y requiere iniciar sesión en GitHub para descargar los Artifacts. |
|------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|---------------|

| <img src="https://avatars.githubusercontent.com/in/15368?s=64&v=4" width = "30" height = "30" alt="LOGO"/> | [GitHub Releases](https://github.com/TiG-Kira/Termux-Ultra/releases) | Versión oficial (estable), con los APK de cada arquitectura en `Assets` de la página de publicación.|
|------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------|-----------|

- La versión Debug solo genera el APK universal (`termux-ultra_debug_universal.apk`); el paquete más el bootstrap ocupan unos `~180MB`.
- La versión Release genera un APK independiente por arquitectura; el paquete por arquitectura ocupa unos `~120MB`.
- Los APK de origen GitHub son todos `debuggable`, compatibles entre sí, pero incompatibles con otros orígenes.

### Acerca de Google Play (descontinuado)

El Termux original y sus plugins dejaron de actualizarse en Play Store debido al [problema de Android 10](https://github.com/termux/termux-packages/wiki/Termux-and-Android-10); la última versión fue `v0.101`.
>**Se recomienda encarecidamente no instalar más aplicaciones de la familia Termux desde Play Store**; migra al origen GitHub o F-Droid.

## Desinstalación

Para desinstalar por completo, es necesario desinstalar **todos** los APKs de Termux o de sus plugins que haya en el dispositivo (consulta [Aplicación y plugins](#aplicación-y-plugins)).

Entra en `Ajustes de Android` → `Aplicaciones`, busca `termux` y desinstálalos uno por uno. Incluso si nunca instalaste plugins, conviene volver a revisar la lista de aplicaciones.

## Estructura del proyecto

```
Termux-Ultra/
├── app/                        # 主应用模块
│   ├── bootstrap/              # 各架构 bootstrap zip，首启动时在线下载
│   ├── src/main/
│   │   ├── assets/             # 容器与部署脚本
│   │   ├── cpp/                # CMake 原生构建（PTY / native-crash-handler）
│   │   ├── cpp_avnc/           # AVNC 原生 VNC 客户端
│   │   ├── java/com/termux/    # 应用 Kotlin/Java 源码
│   │   │   ├── app/            # 核心逻辑（TermuxActivity、TermuxService 等）
│   │   │   ├── app/compose/    # Jetpack Compose UI（主页、文件、远程、资源、设置、AI 助手等）
│   │   │   │   ├── AiTermuxActivity.kt   # AI 助手界面
│   │   │   │   ├── AiTermuxEngine.kt     # AI 引擎与技能执行器
│   │   │   │   └── AiTermuxModels.kt     # AI 数据模型与配置
│   │   │   ├── app/plugin/     # 插件系统（v2.0.0）
│   │   │   │   ├── PluginManager.kt              # 插件管理器核心（启停/权限/会话）
│   │   │   │   ├── PluginManifest.kt             # 插件清单数据模型
│   │   │   │   ├── PluginTypes.kt                # 插件类型 / 权限 / 风险等级枚举
│   │   │   │   ├── PluginLoader.kt               # 插件 ZIP 加载/校验/状态持久化
│   │   │   │   ├── PluginSecurity.kt             # 插件安全校验与权限文案
│   │   │   │   ├── ActionExecutor.kt             # 统一 action 字符串执行器（shell:/action:/nav:/URL）
│   │   │   │   ├── HostActionRegistry.kt         # 宿主原生入口注册表（open_vnc_settings 等）
│   │   │   │   ├── ComposeUiNode.kt              # Compose JSON DSL 数据模型与解析器
│   │   │   │   ├── ComposeRenderer.kt           # Compose JSON 节点渲染器
│   │   │   │   ├── PluginComposeActivity.kt     # Compose 插件页面宿主 Activity
│   │   │   │   ├── PluginWebViewActivity.kt     # H5 插件页面宿主 Activity + JS Bridge
│   │   │   │   ├── PluginCenterActivity.kt      # 插件中心列表 / 安装 / 卸载 UI
│   │   │   │   ├── PluginPersistentSession.kt        # 插件持久化 shell 会话
│   │   │   │   └── PluginPersistentSessionRegistry.kt # 会话 handle → sessionId 反查表
│   │   │   ├── app/vnc/        # VNC 连接管理
│   │   │   ├── app/ssh/        # SSH 连接管理
│   │   │   ├── app/remote/     # 远程管理综合页
│   │   │   ├── app/ftp/        # 内置 FTP 服务器
│   │   │   └── app/activities/ # 各子页面 Activity
│   │   ├── jniLibs/            # 预编译 .so 库
│   │   └── res/                # 资源（布局、drawable、strings、xml 偏好）
│   ├── extern/                 # 第三方原生库源码
│   └── CMakeLists.txt          # 原生构建配置
├── vendor/termux-addons/       # 集成的 Termux 插件源码
├── termux-shared/              # 共享常量与工具库
├── art/                        # 图标与宣传图脚本
├── demo-plugin/                # 示例插件（ZIP 打包示例）
├── build.gradle
├── settings.gradle
└── gradle.properties
```

## Compilación

### Requisitos del entorno

- JDK 8
- Android SDK, compileSdk 37
- NDK `22.1.7171670`
- CMake `3.22.1`

### Comandos de compilación

Para evitar que los espacios en la ruta provoquen problemas de compilación con el NDK, accede al proyecto mediante una ruta de hard link sin espacios (por ejemplo `D:\KiTerminal-UX`).

```bash
# Debug 版本（仅输出 universal APK）
./gradlew assembleDebug

# Release 版本（输出各架构 APK）
./gradlew assembleRelease
```

Resultado de la compilación:
- Debug: `app/build/outputs/apk/debug/termux-ultra_debug_universal.apk`
- Release: los APK de cada arquitectura en `app/build/outputs/apk/release/` (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`)

Objetivos de compilación nativa (CMake): `native-vnc`, `vncclient`, `turbojpeg-static`, `wolfssl`, `termux`

> El entorno de bootstrap ya no se incrusta en el APK; en el primer arranque lo descarga en línea `BootstrapDownloader` según la arquitectura del dispositivo (con fallback entre varios mirrors y verificación SHA-256).

> No uses el parámetro `-q` al compilar, para poder observar el progreso de la compilación.

### Firma

El proyecto incluye la configuración de firma `ki-terminal-release.jks` (alias: `ki-terminal`), y tanto Debug como Release usan esa firma.

## Stack tecnológico

| Categoría | Tecnología |
| --- | --- |
| Lenguajes | Kotlin, Java, C/C++ |
| UI | Jetpack Compose 1.8.3, Material 3 1.3.0, Miuix KMP 0.9.3 (ui / icons / preference) |
| Componentes de arquitectura | AndroidX, Lifecycle 2.8.5, ViewModel, Navigation, Room 2.7.2, DataBinding |
| Terminal | libterminal (Maven Central), TermuxTerminalSession integrado |
| VNC | AVNC, libvncserver, libjpeg-turbo, wolfssl |
| SSH | connectbot sshlib 2.2.36 |
| Carga de imágenes | Coil Compose 2.7.0 |
| Biométrica | AndroidX Biometric 1.2.0-alpha05 |
| Serialización | Gson 2.10.1, kotlinx-serialization 1.9.0 |
| Asistente de IA | API compatible con OpenAI, endpoints personalizados, sistema de habilidades |
| Sistema de plugins | Empaquetado ZIP, configuración JSON, WebView Bridge, puente con Broadcast |
| Compilación | Gradle, CMake 3.22.1, NDK 22.1.7171670 |
| Plugins integrados | termux-api, termux-boot, termux-styling, termux-tasker, termux-widget |
| Nombre de paquete | `com.termux` (sharedUserId) |

## Guía de desarrollo de plugins

### Descripción general

El sistema de plugins de Termux Ultra v2.0.0 permite a desarrolladores de terceros ampliar la funcionalidad de la aplicación. Los plugins se empaquetan en **formato ZIP** (sufijo `.tup`) y se instalan desde la página de recursos → centro de plugins.

Comparado con v1.2.0, v2.0.0 añade estas capacidades:

- **Páginas con Compose JSON DSL**: `pages[].type = "compose"` declara una UI renderizada de forma nativa por el `ComposeRenderer` del host (sin WebView)
- **Puente de Action del host (HostActionRegistry)**: `action.hostActionId` de las tarjetas de recursos, `onClick: "action:xxx"` de Compose y `hostAction()` de H5 invocan de forma unificada las entradas nativas del host
- **API de navegación entre páginas**: `navigate(pageId)` dentro de H5 salta a cualquier subpágina compose/h5 del mismo plugin
- **Protocolo unificado de cadenas action**: `ActionExecutor` admite cuatro prefijos, `shell:`/`action:`/`nav:`/URL directa, que los nodos Compose usan directamente en `onClick`/`onChange`
- **Sesiones persistentes de plugin**: `PluginPersistentSession` del lado del host ofrece un shell residente con lectura y escritura incremental y envío de Ctrl+C/EOF (API de Kotlin del lado del host, no un JS Bridge)
- **minHostVersion sube por defecto a 2.0.0**

### Inicio rápido

1. Crea la estructura de directorios del plugin
2. Escribe el `manifest.json`
3. Añade el código de funcionalidad (H5 / Compose / Skill / tarjetas de recursos)
4. Empaqueta en ZIP (cambia el nombre a `.tup`)
5. Instálalo y pruébalo en el centro de plugins

### Estructura de directorios del plugin

```
my-plugin/
├── manifest.json          # 必须：插件清单
├── icon.png               # 建议：192x192 PNG 图标
├── web/                   # 可选：H5 页面目录（所有 H5 文件放在此）
│   ├── index.html         # 主页（h5Home.entry 指定）
│   ├── about.html         # H5 子页面（pages[].entry 指定）
│   └── settings.html
├── compose/               # 可选：Compose JSON DSL 页面
│   ├── home.json          # Compose 子页面（pages[].entry 指定，type="compose"）
│   └── adb_list.json
└── skills/                # 可选：自定义 Skill（JSON 定义）
    └── my_skill.json
```

> **Norma sobre la ubicación de los archivos de página**: todos los archivos de página (HTML/CSS/JS/imágenes/Compose JSON) deben empaquetarse en el directorio raíz del plugin, y el campo `entry` del `manifest.json` usa una ruta relativa al directorio raíz del plugin (como `web/index.html` o `compose/home.json`). Al instalar el plugin se valida que todos los archivos apuntados por `entry` existan; si falta alguno, se muestra un error.

### Resumen de los campos de manifest.json

```json
{
  "id": "com.example.myplugin",
  "name": "我的插件",
  "version": "1.0.0",
  "minHostVersion": "2.0.0",
  "description": "插件功能简介",
  "author": "开发者名",
  "icon": "icon.png",
  "permissions": ["H5_WEBVIEW", "TERMUX_SESSION_ACCESS", "FILE_SYSTEM_READ"],
  "entryPoints": {
    "resourceCards": [...],
    "settingItems": [...],
    "agentSkills": [...],
    "h5Home": {...},
    "pages": [...]
  },
  "systemPrompt": {...}
}
```

| Campo | Tipo | Obligatorio | Por defecto | Descripción |
|------|------|------|------|------|
| `id` | string | Sí | — | ID único del plugin, en formato de dominio invertido `^[a-zA-Z][a-zA-Z0-9_.]*$` |
| `name` | string | Sí | — | Nombre visible del plugin |
| `version` | string | Sí | — | Versión semántica |
| `minHostVersion` | string | No | `2.0.0` | Versión mínima del host; los hosts de versiones anteriores rechazan la instalación |
| `description` | string | No | `""` | Descripción breve |
| `author` | string | No | `""` | Autor |
| `icon` | string | No | `null` | Ruta relativa del icono |
| `permissions` | string[] | No | `[]` | Lista de permisos |
| `entryPoints` | object | No | `null` | Configuración de puntos de entrada (tarjetas de recursos / elementos de ajustes / Skills / página principal H5 / subpáginas) |
| `systemPrompt` | object | No | `null` | Estrategia de modificación del System Prompt |

### Declaración de permisos

Los plugins pueden declarar los permisos que necesitan; el sistema solicitará la autorización del usuario al usarlos:

| Permiso | Descripción | Nivel de riesgo |
|------|------|----------|
| `TERMUX_SESSION_ACCESS` | Leer y escribir sesiones de terminal / abrir sesiones persistentes | Medio |
| `ROOT_EXECUTE` | Ejecutar comandos con permisos ROOT | Alto |
| `FILE_SYSTEM_READ` | Leer el sistema de archivos | Medio |
| `FILE_SYSTEM_WRITE` | Escribir en el sistema de archivos | Alto |
| `AGENT_MODIFY` | Modificar el comportamiento del Agent y el System Prompt | Alto |
| `H5_WEBVIEW` | Cargar la página principal H5 | Bajo |
| `CROSS_APP_BRIDGE` | Interacción de mensajes entre aplicaciones | Medio |
| `INTERNET_ACCESS` | Acceso a la red | Bajo |

### 1. Tarjetas de recursos (resourceCards)

Declara las tarjetas de recursos del plugin en `entryPoints.resourceCards` del manifest.json; al hacer clic en una tarjeta se ejecuta su `action`. Se admiten cuatro tipos de action:

```json
{
  "entryPoints": {
    "resourceCards": [
      {
        "id": "my_shell",
        "title": "执行命令",
        "description": "运行 pkg install",
        "action": { "type": "SHELL_COMMAND", "command": "pkg install git -y" }
      },
      {
        "id": "my_url",
        "title": "打开链接",
        "description": "跳转外部浏览器",
        "action": { "type": "OPEN_URL", "url": "https://example.com" }
      },
      {
        "id": "my_host",
        "title": "打开 VNC 设置",
        "description": "调用宿主原生入口（v2.0.0 新增）",
        "action": { "type": "HOST_ACTION", "hostActionId": "open_vnc_settings" }
      },
      {
        "id": "my_custom",
        "title": "自定义",
        "description": "由宿主扩展处理",
        "action": { "type": "CUSTOM" }
      }
    ]
  }
}
```

| action.type | Campos obligatorios | Descripción |
|-------------|----------|------|
| `SHELL_COMMAND` | `command` | Ejecuta el comando en el shell de Termux (requiere `TERMUX_SESSION_ACCESS` o `ROOT_EXECUTE`) |
| `OPEN_URL` | `url` | Abre el enlace en el navegador externo |
| `HOST_ACTION` | `hostActionId` | Invoca una entrada nativa registrada en el `HostActionRegistry` del host (consulta [Lista de Actions integradas del host](#7-lista-de-actions-integradas-del-host)) |
| `CUSTOM` | — | Tipo personalizado, procesado por extensiones del host |

> El `id` debe ser único dentro del mismo plugin; el ID final expuesto de la tarjeta es `{pluginId}.{cardId}`.

### 2. Agent Skills personalizadas (agentSkills)

Declara las Skills personalizadas en `entryPoints.agentSkills` del manifest.json; el asistente de IA inyectará estas Skills en la tarjeta de habilidades del System Prompt:

```json
{
  "entryPoints": {
    "agentSkills": [
      {
        "id": "demo_check_env",
        "name": "检查环境",
        "description": "检查 Termux 运行环境是否正常",
        "category": "系统",
        "handler": "echo 'Checking environment...' && which bash && which pkg && echo 'OK'",
        "requiresClick": false,
        "hasOutput": true,
        "riskLevel": "NONE"
      },
      {
        "id": "demo_root_op",
        "name": "ROOT 操作",
        "description": "高风险操作示例",
        "category": "高危",
        "handler": "su -c 'mount | grep system'",
        "requiresClick": true,
        "hasOutput": true,
        "riskLevel": "HIGH"
      }
    ]
  }
}
```

| Campo | Tipo | Por defecto | Descripción |
|------|------|------|------|
| `id` | string | — | ID único de la Skill, expuesto como `{pluginId}.{skillId}` |
| `name` | string | — | Nombre visible de la tarjeta de habilidad |
| `description` | string | — | Descripción de la habilidad; la IA la usa para decidir cuándo invocarla |
| `category` | string | — | Categoría de la habilidad |
| `handler` | string | — | Lógica de manejo; actualmente, un comando de shell |
| `requiresClick` | boolean | `true` | Si requiere que el usuario haga clic para confirmar antes de ejecutarse |
| `hasOutput` | boolean | `false` | Si muestra salida en pantalla |
| `riskLevel` | string | `"NONE"` | Nivel de riesgo: `NONE`/`LOW`/`MEDIUM`/`HIGH`/`CRITICAL` |
| `cardFormat` | object | `null` | Formato de renderizado personalizado de la tarjeta (solo hace falta si el plugin modificó la lógica de las tarjetas del Prompt) |

> `cardFormat` es un campo opcional. Si el plugin no modificó la lógica del formato de tarjetas del System Prompt, el sistema usa el renderizado por defecto.

### 3. Extensión del System Prompt (systemPrompt)

Declara la estrategia de modificación del System Prompt en `systemPrompt` del manifest.json; el asistente de IA ajusta el Prompt central en consecuencia:

```json
{
  "systemPrompt": {
    "mode": "APPEND",
    "content": "## 插件「我的插件」附加指令\n你可以使用以下额外功能：\n- 使用技能 demo_check_env 检查环境\n- 使用技能 demo_hello 打招呼\n- 资源页的演示卡片可快速执行命令",
    "cardFormat": null
  }
}
```

| Campo | Tipo | Por defecto | Descripción |
|------|------|------|------|
| `mode` | string | `"APPEND"` | Modo de modificación: `APPEND`/`MODIFY`/`OVERWRITE` |
| `content` | string | — | Texto en línea, escrito directamente en el System Prompt (no admite referencias a rutas de archivo) |
| `cardFormat` | object | `null` | Formato de tarjeta personalizado (opcional) |

Descripción de los modos de modificación:
- `APPEND`: añade el contenido al final del Prompt central (riesgo bajo, surte efecto directamente al activarlo)
- `MODIFY`: reemplaza un párrafo concreto (riesgo medio)
- `OVERWRITE`: sobrescribe por completo las reglas centrales (**riesgo muy alto**; al activarlo, el sistema muestra un diálogo de advertencia de doble confirmación `PluginOverwriteDialog` y solo surte efecto cuando el usuario confirma)

> **Atención**: el campo `content` es texto en línea y se escribe directamente en el System Prompt; no se admiten referencias a rutas de archivo. En modo OVERWRITE, el System Prompt del plugin reemplaza por completo el System Prompt existente, y la entrada de System Prompt personalizado en los ajustes pasa a ser "Restaurar el System Prompt".

### 4. Interfaz H5 multipágina (h5Home + pages)

Los plugins admiten una **interfaz H5 multipágina**, configurada mediante `entryPoints` del `manifest.json`. `pages[].type` puede ser `"h5"` (HTML cargado en WebView) o `"compose"` (consulta [5. Páginas con Compose JSON DSL](#5-páginas-con-compose-json-dsl-nuevas-en-v200)).

```json
{
  "entryPoints": {
    "h5Home": {
      "enabled": true,
      "entry": "web/index.html",
      "title": "插件主页标题"
    },
    "pages": [
      {
        "id": "page_about",
        "title": "关于",
        "type": "h5",
        "entry": "web/about.html"
      },
      {
        "id": "page_settings",
        "title": "设置",
        "type": "h5",
        "entry": "web/settings.html"
      },
      {
        "id": "page_compose_home",
        "title": "原生页面",
        "type": "compose",
        "entry": "compose/home.json"
      }
    ]
  }
}
```

| Campo | Tipo | Obligatorio | Descripción |
|------|------|------|------|
| `h5Home.enabled` | boolean | Sí | Si se habilita la página principal H5 |
| `h5Home.entry` | string | Sí | Ruta del archivo de entrada de la página principal (relativa al directorio raíz del plugin, por defecto `web/index.html`) |
| `h5Home.title` | string | No | Nombre visible de la página principal (si se deja vacío, se usa el nombre del plugin) |
| `pages[].id` | string | Sí | Identificador único de la subpágina |
| `pages[].title` | string | Sí | Nombre visible de la subpágina |
| `pages[].icon` | string | No | Ruta relativa del icono de la subpágina |
| `pages[].type` | string | Sí | `h5` (WebView) o `compose` (renderizado nativo) |
| `pages[].entry` | string | Sí* | Ruta del archivo de entrada (obligatoria para el tipo compose) |

**Navegación entre páginas H5**: dentro del WebView, la navegación se hace con rutas relativas (en el mismo directorio):

```html
<a href="about.html">关于</a>
<a href="settings.html">设置</a>
```

> El WebView resuelve las rutas relativas tomando como base el directorio del archivo HTML actual; los enlaces externos `http://`/`https://` se interceptan y se abren en el navegador del sistema.

#### API del JS Bridge

Las páginas H5 acceden a las capacidades nativas mediante el objeto `window.TermuxUltra`. Lista completa de la API (incluidas las novedades de v2.0.0):

| API | Devuelve | Descripción | Versión |
|-----|------|------|------|
| `getPluginInfo()` | Cadena JSON | Metadatos del plugin (id/name/version/enabled/permissions) | v1.2.0 |
| `getConfig()` | Cadena JSON | Mapa de configuración del plugin | v1.2.0 |
| `setConfig(key, value)` | boolean | Guarda un elemento de configuración en SharedPreferences | v1.2.0 |
| `exec(command)` | Cadena JSON | Ejecuta un comando de terminal `{success, output\|error}` | v1.2.0 |
| `readFile(path)` | Cadena JSON | Lee un archivo del paquete del plugin `{success, content\|error}` | v1.2.0 |
| `openUrl(url)` | void | Abre el enlace en el navegador externo | v1.2.0 |
| `toast(message)` | void | Muestra un aviso Toast | v1.2.0 |
| `getDeviceInfo()` | Cadena JSON | Información del dispositivo (model/brand/androidVersion/sdkVersion/termuxVersion/rootAvailable) | v1.2.0 |
| `finishPage()` | void | Cierra la página actual del plugin | v1.2.0 |
| `hostAction(actionId)` | Cadena JSON | Invoca una entrada nativa registrada por el HostAction del host | v2.0.0 nuevo |
| `navigate(pageId)` | Cadena JSON | Salta a otra página del mismo plugin (compose o h5) | v2.0.0 nuevo |

**Ejemplo**:

```javascript
var bridge = window.TermuxUltra;

// 1. 基础调用
var info = JSON.parse(bridge.getPluginInfo());
var result = JSON.parse(bridge.exec('echo Hello from ' + info.name));
bridge.toast('命令执行完成');

// 2. 配置持久化
bridge.setConfig('last_open', new Date().toISOString());
var cfg = JSON.parse(bridge.getConfig());
bridge.toast('上次打开: ' + cfg.last_open);

// 3. 调用宿主原生入口（v2.0.0 新增）
bridge.hostAction('open_vnc_settings');     // 跳到 AVNC 设置页
bridge.hostAction('open_plugin_center');    // 跳到插件中心
bridge.hostAction('open_system_settings');  // 跳到 Termux Ultra 系统设置

// 4. 跳转到同插件的其他页面（v2.0.0 新增）
bridge.navigate('page_about');        // 跳到 pages[].id = "page_about" 的 H5 页面
bridge.navigate('page_compose_home'); // 即使目标是 compose 页面也行
```

> `hostAction` / `navigate` devuelven `{"success": true/false}`; devuelven `false` cuando el host no tiene registrado ese actionId o no encuentra el pageId.

### 5. Páginas con Compose JSON DSL (nuevas en v2.0.0)

El plugin puede omitir el HTML y describir directamente en JSON una página que el `ComposeRenderer` del host renderiza de forma nativa. En `pages[]`, establece `type` en `"compose"` y apunta `entry` a un archivo JSON:

```json
{
  "entryPoints": {
    "pages": [
      {
        "id": "page_compose_home",
        "title": "原生页面",
        "type": "compose",
        "entry": "compose/home.json"
      }
    ]
  }
}
```

`compose/home.json` es un árbol de `ComposeUiNode`, con la estructura `{ "type", "props", "children" }`:

```json
{
  "type": "column",
  "props": { "padding": 12 },
  "children": [
    {
      "type": "card",
      "props": { "title": "设备状态" },
      "children": [
        {
          "type": "listItem",
          "props": {
            "title": "打开 VNC 设置",
            "subtitle": "跳转到 AVNC 设置页",
            "onClick": "action:open_vnc_settings"
          }
        },
        {
          "type": "listItem",
          "props": {
            "title": "查看 uname",
            "subtitle": "执行 shell 命令",
            "onClick": "shell:uname -a"
          }
        },
        {
          "type": "listItem",
          "props": {
            "title": "关于页",
            "subtitle": "跳转到 H5 关于页",
            "onClick": "nav:page_about"
          }
        }
      ]
    },
    {
      "type": "switch",
      "props": {
        "stateKey": "auto_refresh",
        "label": "自动刷新",
        "onChange": "shell:echo auto_refresh={value} >> ~/.termux/plugin.cfg"
      }
    },
    {
      "type": "button",
      "props": {
        "text": "打开官网",
        "onClick": "https://example.com"
      }
    },
    {
      "type": "lazyColumn",
      "props": {
        "itemsSource": { "type": "shell", "command": "adb devices" },
        "itemTemplate": {
          "type": "listItem",
          "props": {
            "title": "{serial}",
            "subtitle": "{status}",
            "onClick": "shell:adb -s {serial} shell echo hi"
          }
        }
      }
    }
  ]
}
```

#### Tipos de nodos Compose compatibles

| type | props principales | Descripción |
|------|------------|------|
| `column` | `padding` | Disposición vertical; los nodos hijos van en `children` |
| `row` | — | Disposición horizontal |
| `text` | `text`, `fontSize`, `fontWeight` ("Bold") | Texto |
| `card` | `title` | Contenedor de tarjeta; los nodos hijos van en `children` |
| `listItem` | `title`, `subtitle`, `onClick` | Elemento de lista; al tocarlo se dispara el action |
| `switch` | `stateKey`, `label`, `onChange` | Interruptor; el valor se persiste automáticamente en la configuración del plugin |
| `button` | `text`, `onClick` | Botón |
| `slider` | `stateKey` | Deslizador; el valor se guarda en stateStore |
| `spacer` | `height` | Espaciador (dp) |
| `divider` | — | Línea divisoria horizontal |
| `lazyColumn` | `itemsSource`, `itemTemplate` | Lista dinámica, compatible con fuentes de datos de shell |

> `lazyColumn.itemsSource` por ahora solo admite `{ "type": "shell", "command": "..." }`; la salida de shell se intenta interpretar primero como un array JSON y, si falla, como líneas separadas por espacios (los campos se mapean a `serial`/`status`/`raw`). Los props de `itemTemplate` admiten marcadores `{key}`.

#### Protocolo de cadenas action (onClick / onChange)

Los campos `onClick`/`onChange` de los nodos Compose usan el protocolo unificado de `ActionExecutor`:

| Prefijo | Ejemplo | Descripción |
|------|------|------|
| `shell:` | `shell:uname -a` | Ejecuta el comando en el shell del plugin (requiere `TERMUX_SESSION_ACCESS` o `ROOT_EXECUTE`) |
| `action:` | `action:open_vnc_settings` | Invoca un HostAction del host (consulta [7. Lista de Actions integradas del host](#7-lista-de-actions-integradas-del-host)) |
| `nav:` | `nav:page_about` | Salta a otra página del mismo plugin (se busca por `pages[].id`; tanto compose como h5) |
| `http://`/`https://` | `https://example.com` | Abre en el navegador externo |
| Marcador `{value}` | `shell:echo {value}` | En `switch.onChange` se sustituye por el valor booleano actual (`true`/`false`) |

> Las cadenas que no coinciden con ningún prefijo se ignoran (devuelven `false`).

### 6. Puente de Action del host (HostActionRegistry)

`HostActionRegistry` es un singleton de Kotlin del lado del host que mantiene el mapeo `actionId → handler`, disponible para tres puntos de invocación unificados:

1. **Tarjetas de recursos**: `action.type = "HOST_ACTION"` + `action.hostActionId`
2. **Nodos Compose**: `onClick: "action:xxx"`
3. **H5 JS Bridge**: `bridge.hostAction("xxx")`

Al arrancar el host, `HostActionRegistry.registerDefaults()` registra todas las entradas integradas; los autores de plugins **solo pueden referenciar actionIds ya registrados y no pueden registrar nuevos** (las extensiones personalizadas del host requieren modificar el código fuente del host).

Tabla de equivalencias de invocación:

| Lugar de invocación | Código |
|----------|------|
| Tarjeta de recursos en manifest.json | `{"action": {"type":"HOST_ACTION","hostActionId":"open_vnc_settings"}}` |
| onClick de nodo Compose | `"onClick": "action:open_vnc_settings"` |
| H5 JS Bridge | `bridge.hostAction("open_vnc_settings")` |

### 7. Lista de Actions integradas del host

`HostActionRegistry.registerDefaults()` registra los siguientes `hostActionId` al arrancar el host:

| hostActionId | Descripción |
|--------------|------|
| `open_vnc_settings` | Abre la página de ajustes de AVNC |
| `open_termux_styling` | Abre Termux:Styling (colores/fuentes) |
| `open_termux_tasker` | Abre Termux:Tasker |
| `open_termux_widget` | Abre Termux:Widget |
| `open_plugin_center` | Abre el centro de plugins |
| `open_system_settings` | Abre los ajustes del sistema de Termux Ultra |

> El host puede añadir más entradas nativas en `HostActionRegistry.registerDefaults()` (cada nueva página nativa solo requiere una línea `register(...)`).

### 8. Sesiones persistentes de plugin (API de Kotlin del lado del host)

`PluginPersistentSession` es una sesión de shell residente que el lado del host ofrece al código Kotlin (como handlers de Agent Skills personalizadas, extensiones de renderizado Compose y Módulos nativos), y **no se expone a través del JS Bridge**.

A diferencia de `PluginManager.executeShellCommand()` (que crea un proceso nuevo cada vez), la sesión persistente permanece viva hasta que el plugin la cierra explícitamente o hasta que `TermuxService` se destruye.

```kotlin
// 1. 打开会话（需要 TERMUX_SESSION_ACCESS 权限）
val session = PluginManager.openPersistentSession(
    context,
    pluginId = "com.example.myplugin",
    sessionName = "My Plugin Session"
) ?: return  // 权限不足或创建失败返回 null

// 2. 写入命令并读取增量输出
session.writeln("ls -la /data")
Thread.sleep(200)
val output = session.readNew()  // 读取自上次以来的新增 transcript

// 3. 控制信号
session.interrupt()  // Ctrl+C，中断前台程序
session.sendEof()    // Ctrl+D

// 4. 状态查询
val running = session.isRunning
val cwd = session.cwd           // 当前工作目录（可能为 null）
val pid = session.pid           // 进程 PID（0=未启动, >0=运行中, -1=已结束）
val exit = session.exitCode     // 退出码（仅已结束时有效）

// 5. 关闭会话（幂等，可重复调用）
PluginManager.closePersistentSession(session.sessionId, "com.example.myplugin")
```

#### Resumen de la API de PluginPersistentSession

| Miembro | Descripción |
|------|------|
| `sessionId` | ID único de la sesión (`${pluginId}::${primeros 8 dígitos del uuid}`) |
| `pluginId` | ID del plugin propietario |
| `sessionName` | Nombre de la sesión (se muestra en la página de terminal) |
| `isRunning` | Si sigue viva |
| `cwd` | Directorio de trabajo actual del shell (puede ser null) |
| `pid` | PID del proceso |
| `exitCode` | Código de salida |
| `write(data)` | Escribe bytes crudos (stdin) |
| `writeln(line)` | Escribe una línea de texto (añade el salto de línea automáticamente) |
| `executeCommand(cmd)` | Equivale a `writeln` |
| `readNew(mark=true)` | Lectura incremental de la nueva salida (por defecto avanza el cursor; con `mark=false` se mira sin moverlo) |
| `readAll()` | Lee todo el transcript (sin actualizar el cursor) |
| `resetReadCursor()` | Restablece el cursor al final, ignorando la salida histórica |
| `interrupt()` | Envía Ctrl+C (0x03) |
| `sendEof()` | Envía EOF (0x04) |
| `close()` | Finaliza la sesión (SIGKILL, idempotente) |

> La sesión se registra automáticamente en `TermuxService.mTermuxSessions`, por lo que la página de terminal también puede gestionarla; cuando la sesión termina, `PluginPersistentSessionRegistry` busca el sessionId mediante `TerminalSession.mHandle` y limpia el registro de `PluginManager`. La accesibilidad entre plugins se valida mediante `PluginPersistentSession.pluginId`: el acceso entre plugins distintos genera un warn y devuelve null.

### Empaquetado e instalación

1. Organiza los archivos del plugin según la estructura
2. Comprímelos en un archivo ZIP y cámbiale el nombre a `.tup`
3. Sube el archivo `.tup` al dispositivo
4. Abre Termux Ultra → página de recursos → centro de plugins → instalar desde archivo

```bash
# 打包命令示例
cd my-plugin
zip -r ../my-plugin.tup .
adb push ../my-plugin.tup /sdcard/Download/
```

### Consejos de depuración

- Ver los registros de carga de plugins: Ajustes → Depuración → establece el nivel de registro en Verbose
- Depuración de la página principal H5: usa la depuración remota de Chrome DevTools para el WebView
- Pruebas de permisos: revoca los permisos en la página de gestión de plugins y vuelve a llamar a la API para comprobar el flujo de autorización

### Plugin de ejemplo

El directorio `demo-plugin/` del proyecto contiene un plugin de ejemplo completo (versión v1.1.0), que muestra:
- Interfaz H5 multipágina (página principal + página de información + página de ajustes), configurada con `h5Home` + array `pages`
- Ejemplos de uso completo de la API del JS Bridge (objeto `window.TermuxUltra`)
- Definición de tarjetas de recursos (tipo SHELL_COMMAND)
- Definición de Agent Skills (handler personalizado)
- Adición al System Prompt (modo APPEND)
- Persistencia de la configuración del plugin (setConfig / getConfig)
- El flujo completo de empaquetado a .tup

## Depuración

Puedes configurar el nivel de registro de `logcat` en `Ajustes` → `Depuración` de la aplicación (requiere versión de la aplicación `>= 0.118.0`). El nivel de registro por defecto es `Normal`, y `Verbose` registra información adicional. Al terminar la depuración, restablece `Normal` para evitar que datos sensibles se escriban en logcat y para no perder rendimiento.

Ver los registros:

```bash
# 终端内实时查看（Ctrl+c 停止）
logcat

# 导出日志快照
logcat -d > logcat.txt
```

También puedes mantener pulsado el menú del terminal `More` → `Report Issue` para generar automáticamente la información de estado y una instantánea de logcat, lo que facilita informar de problemas. Al informar, adjunta el informe completo (puedes eliminar la información sensible); los informes con solo capturas de pantalla suelen cerrarse.

### Niveles de registro

- `Off` — no registra nada
- `Normal` — registra error / warn / info y las trazas de pila
- `Debug` — registra información de depuración
- `Verbose` — registra información detallada

## Mantenedores y colaboradores

Termux Ultra es desarrollado y mantenido por **Kira** ([@TiG-Kira](https://github.com/TiG-Kira)), sobre la base del trabajo de los autores originales de Termux (@termux).

La biblioteca `termux-shared` define las constantes y clases de utilidad compartidas entre la aplicación y los plugins; las constantes principales están en [`TermuxConstants`](termux-shared/src/main/java/com/termux/shared/termux/TermuxConstants.java). Al enviar código, sigue estas reglas:

- Define las constantes y utilidades compartidas en `termux-shared`; **no se permiten rutas hardcodeadas**, de lo contrario el PR no se aceptará.
- La activación y desactivación de las herramientas integradas se gestiona de forma unificada mediante el singleton `IntegratedTools`; no se permite llamar directamente a `PackageManager.setComponentEnabledSetting()`, hay que usar `IntegratedTools.setEnabled()` + `IntegratedTools.applyComponentState()`.
- Los componentes Android de las herramientas integradas deben declararse en `AndroidManifest.xml` con `android:enabled="false"` y registrarse en `IntegratedTools.componentsFor()`.
- La lógica central del sistema de plugins está en `app/src/main/java/com/termux/app/plugin/`; para añadir funcionalidades de plugin, sigue las definiciones de interfaces existentes.
- Los mensajes de commit siguen la especificación de [Conventional Commits](https://www.conventionalcommits.org) (como `Added: nueva funcionalidad`, `Fixed: corrección de problema`, `Changed!: cambio incompatible`); debe haber un espacio después de los dos puntos.
- `versionName` sigue [Versionado Semántico 2.0.0](https://semver.org/spec/v2.0.0.html), con el formato `major.minor.patch(-prerelease)(+buildmetadata)`, por ejemplo `v0.1.0`.

### Notas para los forks

- Si cambias el nombre de paquete, debes recompilar el bootstrap zip correspondiente al `$PREFIX`; consulta [Building Packages](https://github.com/termux/termux-packages/wiki/Building-packages).
- Los componentes de los plugins integrados (`vendor/termux-addons/`) se declaran en `AndroidManifest.xml` con `android:enabled="false"` por defecto y se activan y desactivan dinámicamente en tiempo de ejecución mediante `IntegratedTools.applyComponentState()`; mantén este patrón al hacer un fork.
- Termux:Styling usa el nombre de paquete fusionado `com.termux` en lugar del `com.termux.styling` original; al cambiar el nombre de paquete, actualiza de forma sincronizada el mapeo de componentes en `IntegratedTools.kt`.

## Agradecimientos

- [Termux](https://github.com/termux/termux-app) — base del emulador de terminal y del entorno Linux
- [AVNC](https://github.com/gujjwal00/avnc) — cliente VNC para Android
- [libvncserver](https://github.com/LibVNC/libvncserver) — biblioteca VNC
- [wolfSSL](https://github.com/wolfSSL/wolfssl) — biblioteca TLS embebida
- [libjpeg-turbo](https://github.com/libjpeg-turbo/libjpeg-turbo) — códec JPEG
- [connectbot sshlib](https://github.com/connectbot/sshlib) — biblioteca SSH
- [Miuix KMP](https://github.com/miuix-kotlin-multiplatform/miuix) — componentes de diseño de UI
- [LightPanel](https://github.com/MyUI0/lightpanel) — panel de administración web Zhuque
- [Termux Add-ons](https://github.com/termux) — código fuente de los plugins API, Boot, Styling, Tasker y Widget

## Licencia de código abierto

Este proyecto se publica bajo la [GNU Affero General Public License v3.0](./LICENSE). El uso, la modificación y la distribución deben cumplir los términos de esa licencia y conservar la atribución al autor original.

Los derechos de autor del proyecto original de Termux pertenecen a sus autores originales, y este proyecto solo desarrolla funcionalidades sobre su base. Las bibliotecas nativas de terceros (libvncserver, wolfssl, libjpeg-turbo, etc.) tienen sus propias licencias.
