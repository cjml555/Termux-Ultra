# Plugin de demostración para Termux Ultra

<div align="center">

[ <a href="./README.es.md">**Español**</a> · <a href="./README.md">**中文**</a> ]

</div>

Este es el plugin de ejemplo oficial del sistema de plugins de Termux Ultra.

## Funcionalidad

- 🌐 **Interfaz H5 de varias páginas**: página de inicio + página "Acerca de" + página de ajustes, para demostrar la navegación entre páginas
- 🔌 **Tarjeta de recurso**: agrega una tarjeta de demostración en la página de recursos que ejecuta un comando con un solo toque
- 🤖 **Tarjeta de habilidad**: agrega 2 nuevas habilidades a Termux Agent
- 📝 **System Prompt**: agrega al Agent las instrucciones relacionadas con el plugin

## Permisos requeridos

- `H5_WEBVIEW` — mostrar la página de inicio H5
- `TERMUX_SESSION_ACCESS` — ejecutar comandos de terminal
- `FILE_SYSTEM_READ` — leer archivos
- `INTERNET_ACCESS` — acceso a internet

## Estructura de directorios del paquete del plugin

```
demo-plugin/
├── manifest.json          # 插件清单（必需）
├── web/                   # H5 页面目录（推荐）
│   ├── index.html         # 主页（h5Home.entry 指定）
│   ├── about.html         # 关于页（pages[0].entry 指定）
│   └── settings.html      # 设置页（pages[1].entry 指定）
└── README.md              # 说明文档（可选）
```

## Especificación de ubicación de archivos H5

### Reglas básicas

1. **Todos los archivos H5 deben empaquetarse dentro del directorio raíz del plugin**, con rutas relativas al directorio raíz del plugin
2. El campo `entry` en `manifest.json` usa una ruta relativa, por ejemplo `web/index.html`
3. Al instalar el plugin se valida que todos los archivos apuntados por `entry` existan; si falta alguno, se muestra un error

### Configuración de H5 en manifest.json

```json
{
  "entryPoints": {
    "h5Home": {
      "enabled": true,
      "entry": "web/index.html",
      "title": "主页标题"
    },
    "pages": [
      {
        "id": "page_id",
        "title": "页面显示名",
        "type": "h5",
        "entry": "web/about.html"
      }
    ]
  }
}
```

### Descripción de los campos

| Campo | Tipo | Obligatorio | Descripción |
|------|------|------|------|
| `h5Home.enabled` | boolean | Sí | Indica si la página de inicio H5 está habilitada |
| `h5Home.entry` | string | Sí | Ruta del archivo de entrada de la página de inicio (relativa al directorio raíz del plugin) |
| `h5Home.title` | string | No | Nombre visible de la página de inicio (si se deja vacío se usa el nombre del plugin) |
| `pages[].id` | string | Sí | Identificador único de la subpágina |
| `pages[].title` | string | Sí | Nombre visible de la subpágina |
| `pages[].type` | string | Sí | Tipo de página; las páginas H5 siempre usan `"h5"` |
| `pages[].entry` | string | Sí | Ruta del archivo de entrada de la subpágina (relativa al directorio raíz del plugin) |

### Especificación de rutas

- ✅ Correcto: `web/index.html`, `web/about.html`, `pages/home/main.html`
- ❌ Incorrecto: `/web/index.html` (ruta absoluta), `C:\path\to\file.html` (ruta de Windows)
- ❌ Incorrecto: `file:///sdcard/...` (ruta externa; no se permite acceder a archivos fuera del paquete del plugin)

### Navegación entre varias páginas

Las páginas H5 pueden navegar entre sí mediante rutas relativas:

```html
<a href="about.html">关于</a>
<a href="settings.html">设置</a>
```

El WebView resuelve las rutas relativas tomando como base el directorio donde se encuentra el archivo HTML actual.

## Empaquetado como .tup

Empaqueta todos los archivos en formato ZIP y cambia la extensión a `.tup`:

```bash
# 在 demo-plugin 目录下

zip -r ../demo-plugin.tup .
```

## Instalación

1. Abre Termux Ultra → página de recursos → centro de plugins
2. Toca «Instalar plugin»
3. Selecciona el archivo `demo-plugin.tup`
4. Confirma los permisos y actívalo

## API de JS Bridge

Las páginas H5 del plugin pueden acceder a las siguientes APIs mediante el objeto `window.TermuxUltra`:

| API | Descripción |
|-----|------|
| `getPluginInfo()` | Obtiene la información del plugin (devuelve una cadena JSON) |
| `getConfig()` | Lee la configuración del plugin (devuelve una cadena JSON) |
| `setConfig(key, value)` | Guarda un elemento de configuración |
| `exec(command)` | Ejecuta un comando de terminal (devuelve una cadena JSON) |
| `readFile(path)` | Lee el contenido de un archivo dentro del paquete del plugin |
| `openUrl(url)` | Abre un enlace en el navegador externo |
| `toast(message)` | Muestra un aviso Toast |
| `getDeviceInfo()` | Obtiene la información del dispositivo (devuelve una cadena JSON) |
| `finishPage()` | Cierra la página actual del plugin |

## Desarrollo y depuración

1. Después de modificar los archivos H5, vuelve a empaquetar el `.tup` y reinstala el plugin
2. Usa la depuración remota del WebView con Chrome DevTools (requiere haber habilitado la depuración del WebView en la app)
3. Muestra información de depuración en la página H5 con `bridge.toast()`
