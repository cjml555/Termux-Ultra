---
title: Manual de usuario
lang: es
ref: manual
description: Instalación de Termux Ultra, primera ejecución, sesiones de terminal, gestor de archivos, VNC/SSH, contenedores y máquinas virtuales, ajustes y copias de seguridad, asistente de IA, registros y preguntas frecuentes.
---

## 1. Lee antes de instalar: la firma debe coincidir

Termux Ultra comparte el `sharedUserId` (`com.termux`) con el Termux original y con todos sus plugins. Por lo tanto, **la app y cada APK de plugin presente en el dispositivo deben provenir de la misma fuente de firma**; de lo contrario no pueden cooperar y la instalación falla con:

- `INSTALL_FAILED_SHARED_USER_INCOMPATIBLE`
- `signatures do not match`

Reglas:

- **Nunca mezcles fuentes** (por ejemplo, un APK de F-Droid y otro de GitHub).
- Para cambiar de fuente, **desinstala primero todos los APKs de Termux y de plugins instalados**, y luego reinstala todo desde la única fuente nueva.
- Haz antes una copia de seguridad de tus datos: consulta [Backing up Termux](https://wiki.termux.com/wiki/Backing_up_Termux).

> "bootstrap" se refiere al conjunto mínimo de paquetes que distribuye `termux-app` para arrancar un entorno de shell mínimo. Su zip lo compila y publica [termux-packages releases](https://github.com/termux/termux-packages/releases).

## 2. Requisitos del sistema

| Elemento | Requisito |
|------|-------------|
| Android | `>= 8.0` (API 26) |
| targetSdk / compileSdk | `28` / `37` |
| ABIs compatibles | `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64` |

## 3. Descarga e instalación

| Canal | Notas |
|---------|-------|
| [GitHub Releases](https://github.com/TiG-Kira/Termux-Ultra/releases) | **Versiones estables**; los APKs por ABI aparecen en `Assets` |
| [GitHub CI](https://github.com/TiG-Kira/Termux-Ultra/actions/workflows/ci.yml) | Compilaciones automatizadas de CI (beta); se disparan en cada commit y requieren una cuenta de GitHub para descargar los artefactos |

Referencia de tamaños:

- Las compilaciones debug solo producen un APK universal (`termux-ultra_debug_universal.apk`), de unos `~180MB` incluyendo el bootstrap.
- Las compilaciones release producen APKs por ABI, de unos `~120MB` cada uno.
- Los APKs de GitHub son todos `debuggable` y compatibles entre sí, pero incompatibles con otras fuentes.

### Acerca de Google Play Store (obsoleto)

El Termux original y sus plugins dejaron de actualizarse en Play Store por el [problema de Android 10](https://github.com/termux/termux-packages/wiki/Termux-and-Android-10); la última versión allí es `v0.101`. **No instales apps de la familia Termux desde Play Store**: migra a GitHub o F-Droid.

## 4. Primera ejecución (OOBE)

El primer arranque abre el asistente de bienvenida (OOBE), que te lleva a través de:

1. Pantalla de bienvenida e información de versión
2. Solicitud del permiso de almacenamiento
3. Solicitud del permiso de notificaciones (requerido por LiveUpdate)
4. Inicialización de la terminal: extracción del bootstrap y creación del árbol de directorios base
5. Opcional: iniciar sesión o saltar, y leer las Notas de la versión actual

No mates el proceso durante la inicialización. Si se queda detenido, pon el nivel de registro en `Verbose` en `Settings → Debug`, reinicia y captura el logcat (consulta la sección 15).

## 5. Recorrido por la interfaz

La barra inferior tiene cuatro pestañas y también puedes **deslizar horizontalmente** entre ellas:

| Pestaña | Contenido |
|-----|----------|
| **Terminal** | Gestión de varias sesiones, búsqueda, crear/cerrar/renombrar |
| **Files** | Gestor de archivos integrado, servidor FTP |
| **Remote** | Escritorio remoto VNC, gestión de conexiones SSH, túneles SSH |
| **Resources** | Scripts de despliegue con un toque, centro de recursos de terceros, centro de plugins |

Detalles de interacción:

- Las transiciones de página usan animaciones apiladas y horizontales, con soporte de **retroceso predictivo**.
- Los radios de las esquinas de las tarjetas y el recorte del feedback de pulsación son consistentes.
- La barra inferior tiene lógica de evasión y correcciones de márgenes para evitar toques accidentales, y admite efectos de cristal / luz suave / flotante.
- El indicador de navegación se puede arrastrar para cambiar de página.

## 6. Sesiones de terminal

### Conceptos básicos

- **Nueva sesión**: toca `+` en la lista de sesiones
- **Cambiar**: toca un elemento de la lista
- **Renombrar / cerrar**: mantén pulsado, o usa el menú a la derecha del elemento de sesión
- **Buscar**: el cuadro de búsqueda superior filtra en tiempo real (coincide con los títulos; muestra "not found" cuando no hay resultados)

### Mantenimiento activo y protección

- **Detección del estado del servicio**: monitorea continuamente el estado de la terminal, con Wake Lock para mantenerla activa.
- **Monitoreo y protección de la memoria**: congela las sesiones cuando la presión de memoria es alta, evitando la pérdida de datos.
- **Indicaciones de sesión persistente**: en Android 12+ esto funciona junto con `tmux` para la persistencia en segundo plano.

### Motor de la terminal

**LibTerminal** es el motor central de la terminal y se encarga de la emulación de terminal y del renderizado de pantalla, con un rendimiento y una compatibilidad sustancialmente mejores.

### Apariencia de la terminal

Tras habilitar la herramienta integrada `Termux:Styling`, los esquemas de color y las fuentes se pueden gestionar desde los ajustes.

## 7. Interruptores de herramientas integradas

Cinco plugins de Termux vienen integrados en la app (las fuentes están en `vendor/termux-addons/`), así que **no hacen falta APKs aparte**. Actívalos en `Settings → Integrated tools`:

| Herramienta | Propósito |
|------|---------|
| **Termux:API** | Expone funciones del sistema Android (sensores, notificaciones, TTS, …) |
| **Termux:Boot** | Ejecuta scripts de `~/.termux/boot/` al arrancar |
| **Termux:Styling** | Esquemas de color y gestión de fuentes de la terminal (usa el nombre de paquete fusionado `com.termux`) |
| **Termux:Tasker** | Integración con la automatización de Tasker |
| **Termux:Widget** | Accesos directos y widgets de la pantalla de inicio |

> Las herramientas están **desactivadas por defecto**. Al activar se llama a `PackageManager.setComponentEnabledSetting()` para habilitar los componentes correspondientes; al desactivar se hace lo contrario. **Si el APK oficial independiente está instalado, el interruptor se desactiva automáticamente con una advertencia de conflicto**: desinstala primero el APK independiente.

## 8. Gestión de archivos

- Operaciones completas de archivos / carpetas: crear, copiar, cortar, pegar, eliminar, renombrar
- Múltiples acciones al abrir: ver el contenido (`cat`), editar (`vi`), ejecutar (`bash`), copiar la ruta
- Panel de detalles de archivo adaptado al modo oscuro
- **Servidor FTP integrado** para transferencias por LAN
- Deslizar para refrescar, selección múltiple con operaciones por lotes e iconos de tipo de archivo

## 9. Gestión remota

### Escritorio remoto VNC

Basado en AVNC + libvncserver, con soporte para:

- Gestos de zoom con pellizco
- Varios modos de entrada
- Envío de teclas especiales
- Configuración del formato de color
- **Escaneo automático de puertos VNC locales**

### Gestión de conexiones SSH

Basado en la sshlib de connectbot:

- Guardar / editar / eliminar varios perfiles de conexión
- Instala automáticamente `ssh` / `sshpass`
- **Túneles SSH**: reenvío de puertos local, verificación de la clave del host, reintento con varias IP

La página de remoto usa una interfaz de búsqueda unificada con gestión basada en tarjetas.

## 10. Contenedores Linux y máquinas virtuales (despliegue con un toque)

La página de recursos se divide en un **centro de utilidades** y un **centro de recursos de terceros**:

| Entrada | Descripción |
|-------|-------------|
| Contenedor Linux | Instalación con un toque de Ubuntu (Noble/Jammy) o Debian (Bookworm) basada en proot, compartiendo el directorio home de Termux |
| Instalación de QEMU | Instala QEMU dentro de un contenedor o directamente en Termux para la virtualización completa de sistemas |
| QEMU sobre VNC | Arranca una VM con QEMU y la muestra sobre VNC, con CPU / memoria / disco / ISO personalizados |
| Seed ISO | Genera automáticamente una ISO seed para la configuración inicial de la VM |
| LightPanel | Despliegue con un toque de un panel de administración web |
| Entorno de Python | Despliegue con un toque del runtime de Python |
| tmux | Mantiene vivos los contenedores y los proyectos |
| Centro de recursos de terceros | Recursos de extensión mantenidos por la comunidad |

## 11. Centro de plugins

Entrada: **página de Resources → centro de plugins**.

- Instalar: instala un paquete de plugin `.tup` (formato ZIP) desde un archivo
- Gestionar: activar / desactivar, ver la configuración, desinstalar
- Permisos: ejecución como ROOT, acceso a sesiones, lectura/escritura de archivos, puente entre apps, H5 WebView, acceso a internet, modificación del agente
- Los cambios de prompt del sistema con `OVERWRITE` muestran un diálogo de confirmación

Consulta la [documentación de desarrollo de plugins]({{ '/en/plugins/' | relative_url }}) para escribir plugins.

## 12. Ajustes y panel

| Función | Descripción |
|---------|-------------|
| Tarjeta de información de red | IP pública y país en vivo |
| Información del dispositivo | Modelo, versión de Android, versión del kernel |
| Copia de seguridad / restauración | Respalda y restaura los datos de Termux |
| Panel de herramientas integradas | Incluye la detección de conflictos con APKs independientes |
| Autenticación biométrica | Desbloqueo con huella dactilar |
| Idiomas | Chino / inglés (cobertura total del chino) |
| Oscuro / claro | Seguir al sistema o elegir manualmente |
| Ajustes con estilo Miuix | Suite ArrowPreference |

## 13. Asistente de IA

Un asistente integrado que habla con la terminal, el sistema de archivos y las conexiones remotas en lenguaje natural.

Configúralo en `AI assistant → Settings`: completa la `Base URL` compatible con OpenAI, la `API Key` y el nombre del modelo, y opcionalmente ajusta `temperature`. También se admiten endpoints de modelos locales.

Capacidades:

- **Sistema de habilidades**: crear/cerrar sesiones, ejecutar comandos, leer/escribir archivos, conexiones VNC/SSH, gestión de máquinas virtuales QEMU
- **Seguridad**: detección de operaciones peligrosas (`rm -rf`, `dd`, bombas de fork, …) con confirmación
- **Contexto**: lee información de sesiones activas, listados de archivos y resultados de comandos
- **Visualización del pensamiento profundo**: muestra el razonamiento cuando el modelo lo soporta
- **Extensión por plugins**: los plugins pueden agregar habilidades y modificar el prompt del sistema

## 14. Desinstalación

Para una desinstalación limpia debes eliminar **todos** los APKs de Termux o de plugins presentes en el dispositivo.

Abre `Android Settings → Apps`, busca `termux` y desinstálalos uno por uno. Aunque nunca hayas instalado ningún plugin, revisa la lista de apps con cuidado.

## 15. Depuración y registros

Configura el nivel de `logcat` en `Settings → Debug` (requiere la versión de la app `>= 0.118.0`):

| Nivel | Descripción |
|-------|-------------|
| `Off` | No se registra nada |
| `Normal` | error / warn / info más trazas de pila (predeterminado) |
| `Debug` | Mensajes de depuración |
| `Verbose` | Mensajes detallados |

Ver los registros:

```bash
# Follow logs inside the terminal (Ctrl+c to stop)
logcat

# Export a log snapshot
logcat -d > logcat.txt
```

También puedes **mantener pulsado el menú de la terminal → More → Report Issue** para generar automáticamente información de estado y una captura del logcat. Al reportar un problema, adjunta el informe completo (limpiando datos sensibles si hace falta): **los reportes solo con captura de pantalla suelen cerrarse**.

> Restaura el nivel de registro a `Normal` cuando termines, para no escribir datos sensibles en el logcat y mantener el rendimiento.

## 16. Preguntas frecuentes

**P: La instalación falla por un desajuste de firma / `INSTALL_FAILED_SHARED_USER_INCOMPATIBLE`.**
R: Hay un APK de Termux o de un plugin proveniente de otra fuente. Desinstala todos los APKs de la familia Termux y reinstala desde una sola fuente.

**P: El interruptor de una herramienta integrada aparece desactivado.**
R: El APK oficial independiente está instalado y entra en conflicto. Desinstálalo y el interruptor quedará disponible.

**P: Las sesiones mueren tras estar mucho tiempo en segundo plano.**
R: En Android 12+ usa `tmux` para persistir las sesiones; además desactiva la optimización de batería para la app, permite la actividad en segundo plano y fíjala en Recientes.

**P: Las notificaciones no aparecen.**
R: Comprueba que el permiso de notificaciones esté concedido. En Android 15+ se requiere `POST_PROMOTED_NOTIFICATIONS`; la app la declara y recurre a la API antigua en versiones anteriores.

**P: No hay prompt en la terminal / no hay eco / el tema de oh-my-bash se rompe.**
R: Actualiza a VorteX Guard Engine v1.7.0+ — la arquitectura de shell hooks se reescribió para corregir esto.

**P: Los scripts de shell escritos en Windows fallan con errores extraños.**
R: Los editores de Windows pueden añadir un BOM UTF-8, que rompe `bash source`. Esto ya está contemplado, pero también puedes volver a guardar como UTF-8 sin BOM y con finales de línea LF.

**P: El nuevo APK no abre después de instalarlo.**
R: Confirma que la ABI coincide (`arm64-v8a` es la habitual), confirma que no quede una instalación anterior con otra firma y, si hace falta, captura el logcat y abre un issue.
