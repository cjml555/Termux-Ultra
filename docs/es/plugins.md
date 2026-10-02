---
title: Desarrollo de plugins
lang: es
ref: plugins
description: Guía completa de plugins de Termux Ultra — referencia de manifest.json, permisos, tarjetas de recursos, skills del agente, prompt del sistema, páginas H5 y puente JS, DSL JSON de Compose, puente de acciones del host, sesiones persistentes, empaquetado y depuración.
---

## 1. Visión general

El sistema de plugins de Termux Ultra v2.0.0 permite que desarrolladores de terceros extiendan la app. Los plugins se empaquetan como **archivos ZIP** con la extensión `.tup` y se instalan desde **Página de recursos → Centro de plugins**.

Novedades de v2.0.0 frente a v1.2.0:

- **Páginas con DSL JSON de Compose**: `pages[].type = "compose"` declara una interfaz que el `ComposeRenderer` del host renderiza de forma nativa (sin WebView)
- **Puente de acciones del host (`HostActionRegistry`)**: `action.hostActionId` en tarjetas de recursos, `onClick: "action:xxx"` en Compose y `hostAction()` en H5 desembocan todos en los mismos puntos de entrada del host
- **API de navegación entre páginas**: `navigate(pageId)` desde H5 salta a cualquier subpágina compose/h5 del mismo plugin
- **Protocolo unificado de cadenas de acción**: `ActionExecutor` admite los prefijos `shell:` / `action:` / `nav:` y URL sin prefijo
- **Sesiones persistentes de plugin**: `PluginPersistentSession` del lado del host ofrece un shell de larga duración con lecturas incrementales y soporte de Ctrl+C / EOF (API Kotlin del host, no el puente JS)
- **`minHostVersion` ahora tiene `2.0.0` como valor por defecto**

## 2. Inicio rápido

1. Crea la estructura de directorios del plugin
2. Escribe el `manifest.json`
3. Agrega tu funcionalidad (H5 / Compose / skills / tarjetas de recursos)
4. Comprímelo en un zip y renómbralo a `.tup`
5. Instálalo y pruébalo desde el centro de plugins

## 3. Estructura de directorios del plugin

```
my-plugin/
├── manifest.json          # Required: plugin manifest
├── icon.png               # Recommended: 192x192 PNG icon
├── web/                   # Optional: H5 pages (all H5 files live here)
│   ├── index.html         # Home page (referenced by h5Home.entry)
│   ├── about.html         # H5 sub-page (referenced by pages[].entry)
│   └── settings.html
├── compose/               # Optional: Compose JSON DSL pages
│   ├── home.json          # Compose sub-page (pages[].entry, type="compose")
│   └── adb_list.json
└── skills/                # Optional: custom skills (JSON definitions)
    └── my_skill.json
```

> **Regla de ubicación de archivos de página**: todos los archivos de página (HTML / CSS / JS / imágenes / JSON de Compose) deben empaquetarse bajo la raíz del plugin, y los campos `entry` de `manifest.json` usan rutas **relativas a la raíz del plugin** (por ejemplo `web/index.html`, `compose/home.json`). La instalación valida que todos los archivos referenciados por `entry` existan; los archivos faltantes abortan la instalación.

## 4. Referencia de campos de manifest.json

```json
{
  "id": "com.example.myplugin",
  "name": "My Plugin",
  "version": "1.0.0",
  "minHostVersion": "2.0.0",
  "description": "What the plugin does",
  "author": "Developer name",
  "icon": "icon.png",
  "permissions": ["H5_WEBVIEW", "TERMUX_SESSION_ACCESS", "FILE_SYSTEM_READ"],
  "entryPoints": {
    "resourceCards": [],
    "settingItems": [],
    "agentSkills": [],
    "h5Home": {},
    "pages": []
  },
  "systemPrompt": {}
}
```

| Campo | Tipo | Obligatorio | Por defecto | Descripción |
|-------|------|----------|---------|-------------|
| `id` | string | sí | — | ID único del plugin, en formato de dominio inverso `^[a-zA-Z][a-zA-Z0-9_.]*$` |
| `name` | string | sí | — | Nombre visible |
| `version` | string | sí | — | Versión semántica |
| `minHostVersion` | string | no | `2.0.0` | Versión mínima del host; los hosts más antiguos rechazan la instalación |
| `description` | string | no | `""` | Descripción breve |
| `author` | string | no | `""` | Autor |
| `icon` | string | no | `null` | Ruta del ícono, relativa |
| `permissions` | string[] | no | `[]` | Lista de permisos |
| `entryPoints` | object | no | `null` | Configuración de puntos de entrada (tarjetas de recursos / ajustes / skills / inicio H5 / subpáginas) |
| `systemPrompt` | object | no | `null` | Estrategia de modificación del prompt del sistema |

## 5. Permisos

Los plugins declaran los permisos que necesitan y el host solicita el consentimiento del usuario en el momento de uso:

| Permiso | Descripción | Riesgo |
|------------|-------------|------|
| `TERMUX_SESSION_ACCESS` | Leer/escribir sesiones de terminal, abrir sesiones persistentes | Medio |
| `ROOT_EXECUTE` | Ejecutar comandos con privilegios ROOT | Alto |
| `FILE_SYSTEM_READ` | Leer el sistema de archivos | Medio |
| `FILE_SYSTEM_WRITE` | Escribir en el sistema de archivos | Alto |
| `AGENT_MODIFY` | Modificar el comportamiento del agente y el prompt del sistema | Alto |
| `H5_WEBVIEW` | Cargar la página de inicio H5 | Bajo |
| `CROSS_APP_BRIDGE` | Mensajería entre aplicaciones | Medio |
| `INTERNET_ACCESS` | Acceso a la red | Bajo |

## 6. Tarjetas de recursos

Decláralas bajo `entryPoints.resourceCards`. Al tocar una tarjeta se ejecuta su `action`. Se admiten cuatro tipos de acción:

```json
{
  "entryPoints": {
    "resourceCards": [
      {
        "id": "my_shell",
        "title": "Run command",
        "description": "Runs pkg install",
        "action": { "type": "SHELL_COMMAND", "command": "pkg install git -y" }
      },
      {
        "id": "my_url",
        "title": "Open link",
        "description": "Opens the external browser",
        "action": { "type": "OPEN_URL", "url": "https://example.com" }
      },
      {
        "id": "my_host",
        "title": "Open VNC settings",
        "description": "Calls a native host entry (new in v2.0.0)",
        "action": { "type": "HOST_ACTION", "hostActionId": "open_vnc_settings" }
      },
      {
        "id": "my_custom",
        "title": "Custom",
        "description": "Handled by host extensions",
        "action": { "type": "CUSTOM" }
      }
    ]
  }
}
```

| `action.type` | Campos obligatorios | Descripción |
|---------------|-----------------|-------------|
| `SHELL_COMMAND` | `command` | Ejecuta un comando en el shell de Termux (necesita `TERMUX_SESSION_ACCESS` o `ROOT_EXECUTE`) |
| `OPEN_URL` | `url` | Abre un enlace en el navegador externo |
| `HOST_ACTION` | `hostActionId` | Llama a una entrada nativa registrada en el `HostActionRegistry` del host (ver sección 10) |
| `CUSTOM` | — | Tipo personalizado manejado por extensiones del host |

> El `id` debe ser único dentro de un plugin; el ID expuesto de la tarjeta es `{pluginId}.{cardId}`.

## 7. Skills personalizados del agente

Se declaran bajo `entryPoints.agentSkills`. El asistente de IA los inyecta en las tarjetas de skills de su prompt del sistema:

```json
{
  "entryPoints": {
    "agentSkills": [
      {
        "id": "demo_check_env",
        "name": "Check environment",
        "description": "Check whether the Termux runtime is healthy",
        "category": "System",
        "handler": "echo 'Checking environment...' && which bash && which pkg && echo 'OK'",
        "requiresClick": false,
        "hasOutput": true,
        "riskLevel": "NONE"
      },
      {
        "id": "demo_root_op",
        "name": "ROOT operation",
        "description": "High-risk operation example",
        "category": "High risk",
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
|-------|------|---------|-------------|
| `id` | string | — | ID único del skill, expuesto como `{pluginId}.{skillId}` |
| `name` | string | — | Nombre visible de la tarjeta del skill |
| `description` | string | — | Qué hace el skill; la IA lo usa para decidir cuándo invocarlo |
| `category` | string | — | Categoría del skill |
| `handler` | string | — | Lógica de manejo, actualmente un comando de shell |
| `requiresClick` | boolean | `true` | Si el usuario debe tocar para confirmar la ejecución |
| `hasOutput` | boolean | `false` | Si la salida se devuelve |
| `riskLevel` | string | `"NONE"` | `NONE` / `LOW` / `MEDIUM` / `HIGH` / `CRITICAL` |
| `cardFormat` | object | `null` | Renderizado personalizado de la tarjeta (solo necesario si el plugin cambia la lógica de tarjetas del prompt) |

> `cardFormat` es opcional. Si el plugin no cambia la lógica de tarjetas del prompt del sistema, se usa el renderizado por defecto.

## 8. Extensión del prompt del sistema

```json
{
  "systemPrompt": {
    "mode": "APPEND",
    "content": "## Extra instructions from plugin \"My Plugin\"\nYou can also use:\n- skill demo_check_env to check the environment\n- skill demo_hello to say hello\n- the demo resource card to run commands quickly",
    "cardFormat": null
  }
}
```

| Campo | Tipo | Por defecto | Descripción |
|-------|------|---------|-------------|
| `mode` | string | `"APPEND"` | `APPEND` / `MODIFY` / `OVERWRITE` |
| `content` | string | — | Texto en línea que se escribe directamente en el prompt del sistema (**no se admiten rutas de archivo**) |
| `cardFormat` | object | `null` | Formato de tarjeta personalizado (opcional) |

Modos:

- `APPEND`: agrega al final del prompt base (riesgo bajo, surte efecto de inmediato)
- `MODIFY`: reemplaza una sección específica (riesgo medio)
- `OVERWRITE`: reemplaza por completo las reglas base (**riesgo extremadamente alto**; muestra la confirmación `PluginOverwriteDialog` antes de aplicarse)

> **Nota**: `content` es texto en línea y no admite referencias a rutas de archivo. En modo `OVERWRITE`, el prompt del sistema del plugin reemplaza por completo el original, y la entrada de prompt del sistema personalizado en los ajustes pasa a llamarse "Restaurar el prompt del sistema".

## 9. Interfaz H5 de múltiples páginas (h5Home + pages)

`pages[].type` puede ser `"h5"` (WebView que carga HTML) o `"compose"` (renderizado nativo, ver sección 11).

```json
{
  "entryPoints": {
    "h5Home": {
      "enabled": true,
      "entry": "web/index.html",
      "title": "Plugin home title"
    },
    "pages": [
      { "id": "page_about", "title": "About", "type": "h5", "entry": "web/about.html" },
      { "id": "page_settings", "title": "Settings", "type": "h5", "entry": "web/settings.html" },
      { "id": "page_compose_home", "title": "Native page", "type": "compose", "entry": "compose/home.json" }
    ]
  }
}
```

| Campo | Tipo | Obligatorio | Descripción |
|-------|------|----------|-------------|
| `h5Home.enabled` | boolean | sí | Si la página de inicio H5 está habilitada |
| `h5Home.entry` | string | sí | Ruta del archivo de entrada de inicio (relativa a la raíz del plugin, por defecto `web/index.html`) |
| `h5Home.title` | string | no | Nombre visible del inicio (si falta, se usa el nombre del plugin) |
| `pages[].id` | string | sí | Identificador único de la subpágina |
| `pages[].title` | string | sí | Nombre visible de la subpágina |
| `pages[].icon` | string | no | Ruta del ícono de la subpágina, relativa |
| `pages[].type` | string | sí | `h5` (WebView) o `compose` (nativo) |
| `pages[].entry` | string | sí* | Ruta del archivo de entrada (obligatoria para compose) |

**Navegación entre páginas H5**: usa rutas relativas dentro del WebView (mismo directorio):

```html
<a href="about.html">About</a>
<a href="settings.html">Settings</a>
```

> El WebView resuelve las rutas relativas contra el directorio del archivo HTML actual; los enlaces externos `http://` / `https://` se interceptan y se abren en el navegador del sistema.

### 9.1 API del puente JS

Las páginas H5 llegan a las capacidades nativas mediante `window.TermuxUltra`:

| API | Devuelve | Descripción | Desde |
|-----|---------|-------------|-------|
| `getPluginInfo()` | cadena JSON | Metadatos del plugin (id / name / version / enabled / permissions) | v1.2.0 |
| `getConfig()` | cadena JSON | Mapa de configuración del plugin | v1.2.0 |
| `setConfig(key, value)` | boolean | Guarda una entrada de configuración en SharedPreferences | v1.2.0 |
| `exec(command)` | cadena JSON | Ejecuta un comando de terminal `{success, output\|error}` | v1.2.0 |
| `readFile(path)` | cadena JSON | Lee un archivo dentro del paquete del plugin `{success, content\|error}` | v1.2.0 |
| `openUrl(url)` | void | Abre un enlace en el navegador externo | v1.2.0 |
| `toast(message)` | void | Muestra un Toast | v1.2.0 |
| `getDeviceInfo()` | cadena JSON | Información del dispositivo (model / brand / androidVersion / sdkVersion / termuxVersion / rootAvailable) | v1.2.0 |
| `finishPage()` | void | Cierra la página actual del plugin | v1.2.0 |
| `hostAction(actionId)` | cadena JSON | Llama a una acción del host registrada en `HostActionRegistry` | v2.0.0 |
| `navigate(pageId)` | cadena JSON | Salta a otra página del mismo plugin (compose o h5) | v2.0.0 |

**Ejemplo**:

```javascript
var bridge = window.TermuxUltra;

// 1. Basic calls
var info = JSON.parse(bridge.getPluginInfo());
var result = JSON.parse(bridge.exec('echo Hello from ' + info.name));
bridge.toast('Command finished');

// 2. Config persistence
bridge.setConfig('last_open', new Date().toISOString());
var cfg = JSON.parse(bridge.getConfig());
bridge.toast('Last opened: ' + cfg.last_open);

// 3. Call native host entries (new in v2.0.0)
bridge.hostAction('open_vnc_settings');     // open the AVNC settings page
bridge.hostAction('open_plugin_center');    // open the plugin center
bridge.hostAction('open_system_settings');  // open Termux Ultra system settings

// 4. Navigate to other pages of the same plugin (new in v2.0.0)
bridge.navigate('page_about');        // jump to the H5 page with pages[].id = "page_about"
bridge.navigate('page_compose_home'); // works even if the target is a compose page
```

> `hostAction` / `navigate` devuelven `{"success": true/false}`; devuelven `false` cuando el `actionId` no está registrado o el `pageId` no se encuentra.

## 10. Puente de acciones del host (HostActionRegistry)

`HostActionRegistry` es un singleton Kotlin del lado del host que mantiene un mapa `actionId → handler`, invocado desde tres lugares:

1. **Tarjetas de recursos**: `action.type = "HOST_ACTION"` + `action.hostActionId`
2. **Nodos Compose**: `onClick: "action:xxx"`
3. **Puente JS de H5**: `bridge.hostAction("xxx")`

El host registra todas las entradas integradas en `HostActionRegistry.registerDefaults()` al arrancar. **Los autores de plugins solo pueden referenciar IDs ya registrados — no pueden registrar nuevos** (agregar extensiones del host requiere modificar el código fuente del host).

| Sitio de llamada | Código |
|-----------|------|
| Tarjeta de recurso en manifest.json | `{"action": {"type":"HOST_ACTION","hostActionId":"open_vnc_settings"}}` |
| onClick de nodo Compose | `"onClick": "action:open_vnc_settings"` |
| Puente JS de H5 | `bridge.hostAction("open_vnc_settings")` |

### 10.1 Acciones integradas del host

| `hostActionId` | Descripción |
|----------------|-------------|
| `open_vnc_settings` | Abre la página de ajustes de AVNC |
| `open_termux_styling` | Abre Termux:Styling (colores / fuentes) |
| `open_termux_tasker` | Abre Termux:Tasker |
| `open_termux_widget` | Abre Termux:Widget |
| `open_plugin_center` | Abre el centro de plugins |
| `open_system_settings` | Abre los ajustes de sistema de Termux Ultra |

> El host puede registrar más entradas en `HostActionRegistry.registerDefaults()` — agregar una página nativa cuesta una sola línea `register(...)`.

## 11. Páginas con DSL JSON de Compose

En lugar de HTML, un plugin puede describir una página en JSON que el `ComposeRenderer` del host renderiza de forma nativa. Pon `type` en `"compose"` dentro de `pages[]` y apunta `entry` a un archivo JSON:

```json
{
  "entryPoints": {
    "pages": [
      {
        "id": "page_compose_home",
        "title": "Native page",
        "type": "compose",
        "entry": "compose/home.json"
      }
    ]
  }
}
```

`compose/home.json` es un árbol de `ComposeUiNode` con la forma `{ "type", "props", "children" }`:

```json
{
  "type": "column",
  "props": { "padding": 12 },
  "children": [
    {
      "type": "card",
      "props": { "title": "Device status" },
      "children": [
        {
          "type": "listItem",
          "props": {
            "title": "Open VNC settings",
            "subtitle": "Jump to the AVNC settings page",
            "onClick": "action:open_vnc_settings"
          }
        },
        {
          "type": "listItem",
          "props": {
            "title": "Show uname",
            "subtitle": "Runs a shell command",
            "onClick": "shell:uname -a"
          }
        },
        {
          "type": "listItem",
          "props": {
            "title": "About page",
            "subtitle": "Jump to the H5 about page",
            "onClick": "nav:page_about"
          }
        }
      ]
    },
    {
      "type": "switch",
      "props": {
        "stateKey": "auto_refresh",
        "label": "Auto refresh",
        "onChange": "shell:echo auto_refresh={value} >> ~/.termux/plugin.cfg"
      }
    },
    {
      "type": "button",
      "props": {
        "text": "Open website",
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

### 11.1 Tipos de nodo Compose admitidos

| type | Props clave | Descripción |
|------|-----------|-------------|
| `column` | `padding` | Disposición vertical, hijos en `children` |
| `row` | — | Disposición horizontal |
| `text` | `text`, `fontSize`, `fontWeight` (`"Bold"`) | Texto |
| `card` | `title` | Contenedor de tarjeta, hijos en `children` |
| `listItem` | `title`, `subtitle`, `onClick` | Elemento de lista; al tocarlo se dispara una acción |
| `switch` | `stateKey`, `label`, `onChange` | Interruptor; el valor se persiste en la configuración del plugin |
| `button` | `text`, `onClick` | Botón |
| `slider` | `stateKey` | Deslizador; el valor se guarda en el almacén de estado |
| `spacer` | `height` | Espaciado (dp) |
| `divider` | — | Separador horizontal |
| `lazyColumn` | `itemsSource`, `itemTemplate` | Lista dinámica, admite fuentes de datos de shell |

> `lazyColumn.itemsSource` por ahora solo admite `{ "type": "shell", "command": "..." }`. La salida del shell se interpreta primero como un arreglo JSON y, si falla, como líneas separadas por espacios (los campos se mapean a `serial` / `status` / `raw`). Las props de `itemTemplate` admiten marcadores `{key}`.

### 11.2 Protocolo de cadenas de acción (onClick / onChange)

| Prefijo | Ejemplo | Descripción |
|--------|---------|-------------|
| `shell:` | `shell:uname -a` | Ejecuta un comando en el shell del plugin (necesita `TERMUX_SESSION_ACCESS` o `ROOT_EXECUTE`) |
| `action:` | `action:open_vnc_settings` | Llama a una acción del host (ver sección 10.1) |
| `nav:` | `nav:page_about` | Navega a otra página del mismo plugin (coincide por `pages[].id`, compose o h5) |
| `http://` / `https://` | `https://example.com` | Abre en el navegador externo |
| `{value}` | `shell:echo {value}` | En `switch.onChange`, se reemplaza por el booleano actual (`true` / `false`) |

> Una cadena que no coincide con ningún prefijo se ignora (devuelve `false`).

## 12. Sesiones persistentes de plugin (API Kotlin del host)

`PluginPersistentSession` es una **sesión de shell de larga duración** que el host ofrece al código Kotlin (manejadores de skills del agente, extensiones de renderizado Compose, módulos nativos). **No se expone a través del puente JS.**

A diferencia de `PluginManager.executeShellCommand()` (que lanza un proceso nuevo en cada llamada), una sesión persistente permanece viva hasta que el plugin la cierra explícitamente o `TermuxService` se destruye.

```kotlin
// 1. Open a session (requires TERMUX_SESSION_ACCESS)
val session = PluginManager.openPersistentSession(
    context,
    pluginId = "com.example.myplugin",
    sessionName = "My Plugin Session"
) ?: return  // null when permission is missing or creation fails

// 2. Write a command and read incremental output
session.writeln("ls -la /data")
Thread.sleep(200)
val output = session.readNew()  // new transcript since the last read

// 3. Control signals
session.interrupt()  // Ctrl+C, interrupt the foreground program
session.sendEof()    // Ctrl+D

// 4. State queries
val running = session.isRunning
val cwd = session.cwd           // current working directory (may be null)
val pid = session.pid           // process PID (0=not started, >0=running, -1=finished)
val exit = session.exitCode     // exit code (only valid once finished)

// 5. Close the session (idempotent, safe to call repeatedly)
PluginManager.closePersistentSession(session.sessionId, "com.example.myplugin")
```

### 12.1 Resumen de la API

| Miembro | Descripción |
|--------|-------------|
| `sessionId` | ID único de la sesión (`${pluginId}::${first 8 chars of uuid}`) |
| `pluginId` | ID del plugin propietario |
| `sessionName` | Nombre de la sesión (se muestra en la página de terminal) |
| `isRunning` | Si está viva |
| `cwd` | Directorio de trabajo actual del shell (puede ser null) |
| `pid` | PID del proceso |
| `exitCode` | Código de salida |
| `write(data)` | Escribe bytes crudos (stdin) |
| `writeln(line)` | Escribe una línea de texto (agrega salto de línea) |
| `executeCommand(cmd)` | Equivalente a `writeln` |
| `readNew(mark=true)` | Lee la salida nueva de forma incremental (avanza el cursor por defecto; `mark=false` solo examina) |
| `readAll()` | Lee toda la transcripción (no mueve el cursor) |
| `resetReadCursor()` | Reinicia el cursor al final, ignorando el historial |
| `interrupt()` | Envía Ctrl+C (0x03) |
| `sendEof()` | Envía EOF (0x04) |
| `close()` | Termina la sesión (SIGKILL, idempotente) |

> Las sesiones se registran automáticamente en `TermuxService.mTermuxSessions`, así que la página de terminal también puede gestionarlas. Cuando una sesión termina, `PluginPersistentSessionRegistry` resuelve el id de la sesión mediante `TerminalSession.mHandle` y limpia el registro del `PluginManager`. El acceso desde el mismo plugin se verifica con `PluginPersistentSession.pluginId`; el acceso entre plugins registra una advertencia y devuelve null.

## 13. Empaquetado e instalación

1. Distribuye los archivos del plugin según la estructura anterior
2. Comprímelos y renombra el archivo a `.tup`
3. Envía el archivo `.tup` al dispositivo
4. Abre `Termux Ultra → Página de recursos → Centro de plugins → Instalar desde archivo`

```bash
# Packaging example
cd my-plugin
zip -r ../my-plugin.tup .
adb push ../my-plugin.tup /sdcard/Download/
```

## 14. Consejos de depuración

- Registros de carga de plugins: en `Ajustes → Depuración → nivel de registro` pon `Verbose`
- Depuración de la página de inicio H5: usa Chrome DevTools para depurar el WebView de forma remota
- Pruebas de permisos: revoca un permiso en la página de gestión de plugins y vuelve a llamar a la API para ejercitar el flujo de concesión

## 15. Plugin de ejemplo

El directorio `demo-plugin/` del proyecto contiene un plugin de ejemplo completo (v1.1.0) que demuestra:

- Una interfaz H5 de múltiples páginas (inicio + about + settings) configurada con `h5Home` + `pages`
- El uso completo del puente JS (`window.TermuxUltra`)
- Definiciones de tarjetas de recursos (tipo `SHELL_COMMAND`)
- Definiciones de skills del agente (manejadores personalizados)
- Adición al prompt del sistema (modo `APPEND`)
- Persistencia de la configuración del plugin (`setConfig` / `getConfig`)
- El flujo completo de empaquetado a `.tup`

Contenido: `manifest.json`, `README.md`, `web/index.html`, `web/about.html`, `web/settings.html`.