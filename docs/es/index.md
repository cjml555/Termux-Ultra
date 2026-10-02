---
title: Inicio de la documentación
lang: es
ref: index
permalink: /es/
description: Documentación oficial de Termux Ultra — manual de usuario, guía de funciones y documentación de desarrollo de plugins.
---

<div class="hero">
  <h1>Documentación de Termux Ultra</h1>
  <p>Un emulador de terminal para Android y entorno Linux construido sobre Termux. La interfaz está escrita en Jetpack Compose con el lenguaje de diseño Miuix (HyperOS), e incluye escritorio remoto por VNC, SSH, un gestor de archivos, contenedores proot, máquinas virtuales QEMU, despliegue de recursos con un solo toque, un asistente de IA y un sistema de plugins completo.</p>
</div>

<div class="badges">
  <span class="badge">Android 8.0+ (API 26)</span>
  <span class="badge">Paquete com.termux</span>
  <span class="badge">AGPL-3.0</span>
  <span class="badge">Basado en Termux v0.119.0</span>
</div>

## Las tres documentaciones

<div class="cards">
  <a class="card" href="{{ '/es/manual/' | relative_url }}">
    <span class="card-title">📖 Manual de usuario</span>
    <span class="card-desc">Instalación, desinstalación, primer inicio, sesiones de terminal, gestor de archivos, VNC/SSH, contenedores y máquinas virtuales, ajustes y respaldo, configuración del asistente de IA, registros y preguntas frecuentes.</span>
  </a>
  <a class="card" href="{{ '/es/features/' | relative_url }}">
    <span class="card-title">🧩 Funciones</span>
    <span class="card-desc">Arquitectura, motor de terminal (LibTerminal), interfaz Miuix, mecanismo de herramientas integradas, matriz de capacidades de plugins y modelo de permisos, asistente de IA y motor de seguridad.</span>
  </a>
  <a class="card" href="{{ '/es/plugins/' | relative_url }}">
    <span class="card-title">🔌 Desarrollo de plugins</span>
    <span class="card-desc">Referencia completa de manifest.json, permisos, tarjetas de recursos, skills del agente, prompt del sistema, páginas H5 y el puente JS, DSL JSON de Compose, puente de acciones del host, empaquetado y depuración.</span>
  </a>
</div>

## Paneles

<div class="cards">
  <a class="card" href="{{ '/okr/' | relative_url }}">
    <span class="card-title">📊 Radar de OKR del equipo</span>
    <span class="card-desc">Compara el cumplimiento objetivo contra el real en las ocho áreas de capacidad del repositorio (núcleo de terminal, ecosistema de plugins, asistente de IA, remoto y contenedores, movimiento de la interfaz, infraestructura de ingeniería, documentación e internacionalización, comunidad) para los equipos de producto / ingeniería / diseño — con tasa de cumplimiento general, áreas más fuertes y más débiles, y tendencia trimestral, codificada por color según superen, se acerquen o queden por debajo del objetivo.</span>
  </a>
</div>

## Inicio rápido

1. Descarga el APK correspondiente a tu arquitectura desde [GitHub Releases](https://github.com/TiG-Kira/Termux-Ultra/releases).
2. Asegúrate de que **no** haya instalado ningún Termux / plugin de Termux de otra fuente (la firma debe coincidir — consulta el manual de usuario).
3. El primer inicio abre el asistente OOBE; síguelo para terminar la inicialización.
4. La barra inferior tiene cuatro pestañas — **Terminal / Archivos / Remoto / Recursos** — y también puedes deslizar horizontalmente entre ellas.

> Si ya tienes instalada una compilación de Termux de F-Droid o de la Play Store, desinstala todo primero y luego reinstala todos los APK de este repositorio. De lo contrario te encontrarás con `INSTALL_FAILED_SHARED_USER_INCOMPATIBLE` o `signatures do not match`.

## Ramas y versiones

| Rama | Base | Versión | Estado |
|--------|------|---------|--------|
| `main` | Termux upstream `v0.119.0-beta.3` | 2.x.x.R5 | Línea principal estable — funciones, correcciones de errores, trabajo de arquitectura |
| `release/r1-r4` | Termux upstream `v0.118.3` | 1.8.0.R4 | Instantánea histórica de v1.8.x, sin funciones nuevas, solo correcciones de emergencia |
| `archived/corebump/2.x` | Termux upstream `v0.119.0-beta.3` | 2.0.0.R5 | Instantánea archivada que conserva las etapas de desarrollo de 2.x |

## Enlaces

- [Repositorio en GitHub](https://github.com/TiG-Kira/Termux-Ultra)
- [Releases (APK estables)](https://github.com/TiG-Kira/Termux-Ultra/releases)
- [Compilaciones de CI (artefactos beta)](https://github.com/TiG-Kira/Termux-Ultra/actions/workflows/ci.yml)
- [Abrir un issue](https://github.com/TiG-Kira/Termux-Ultra/issues)
- [Termux upstream](https://github.com/termux/termux-app)
- [Paquetes instalables (termux-packages)](https://github.com/termux/termux-packages)