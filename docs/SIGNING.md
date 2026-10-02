# Firma de la aplicación

> Documento en español. El resto de `docs/` tiene versiones en `en/`, `es/` y `zh/`;
> esta página es deliberadamente solo en español porque documenta una decisión
> interna del proyecto.

## Resumen

La clave de firma de release de Termux Ultra **está en el repositorio público**, y
eso es una decisión consciente, no un descuido. Este documento explica por qué,
cuál es el modelo de amenaza real y qué haría falta para cerrarlo del todo.

Estado actual: `cjml555/Termux-Ultra` es un repositorio público. El keystore
(`ki-terminal-release.jks`, alias `ki-terminal`) se commiteó en el pasado y sigue
presente en el historial de git, además de estar referenciado en el workflow de
release. Cualquiera que clone el repo puede firmar APKs.

## Por qué se acepta

La app **no está en Google Play**. No hay canal de distribución respaldado por una
clave secreta, y el proyecto se distribuye desde sus propios releases.

En ese escenario, proteger la clave tiene un coste concreto — historial que
reescribir, releases que revocar, instalaciones que se rompen — y un beneficio
limitado. Lo que una clave secreta protegería, aquí, es la reputación de la
identidad de la app, no datos ni dinero.

## Modelo de amenaza

Lo que **no** da acceso a un atacante con el keystore:

- acceso a cuentas, tokens o credenciales de ningún tipo;
- acceso al dispositivo de quien lo tenga instalado;
- capacidad de instalar nada en el dispositivo de otra persona.

Lo que **sí** permite:

- Firmar un APK que Android identifica como esta app. Un atacante podría
  distribuirlo y hacerse pasar por un build oficial.
- Al compartir `sharedUserId="com.termux"` con todas las demás apps Termux, un APK
  así firmado cumple los requisitos de firma del paquete compartido.

El daño es de **identidad y confianza**, no financiero ni de acceso. Es real, pero
acotado.

## La solución de verdad: reproducible builds

Hay una respuesta que elimina el problema sin secretos, y es la que usan los
proyectos abiertos que firman en CI:

**Builds reproducibles y verificables.** Si cualquier persona puede comprobar que un
APK sale del código del repo, la clave deja de ser una confianza y pasa a ser algo
verificable. Entonces puede estar en el repo sin que importe.

En Android esto tiene soporte nativo:

- firma v2/v3 con `SOURCE_DATE_EPOCH` fijo;
- marca de tiempo y orden de entradas deterministas;
- verificación con herramientas tipo `reproducible-builds/verification` o
  `apksigner verify` contra un hash esperado publicado.

Con eso, la firma deja de ser el punto de confianza: el código lo es.

## Qué haría falta para implementarlo

1. Fijar `SOURCE_DATE_EPOCH` en el workflow de release.
2. Publicar el hash del APK junto a cada release, junto con los pasos exactos
   para reproducirlo (versiones de JDK, Android SDK, NDK, `gradle/` lockfiles).
3. Fijar el `gradle-wrapper.jar` y la cadena de herramientas exacta, que ya están
   parcialmente verificadas por `gradle-wrapper-validation.yml`.
4. Publicar una clave de verificación propia (`apksigner verify --print-certs`) para
   que se pueda comprobar la firma sin confiar en GitHub.

Los pasos 1 y 2 son los que hacen la diferencia. El 3 y el 4 son robustez.

## Cómo se firma hoy

Las credenciales ya **no** están hardcodeadas en el repo. `app/build.gradle` las
lee del entorno, y el workflow de release usa secrets de GitHub:

| Variable | Dónde se define |
|---|---|
`TERMUX_STORE_PASSWORD` | entorno o `gradle.properties` local |
`TERMUX_KEY_PASSWORD` | entorno o `gradle.properties` local |
`TERMUX_KEY_ALIAS` | entorno o `gradle.properties` local |
`TERMUX_KEYSTORE_PATH` | entorno o `gradle.properties` local |
`RELEASE_JKS_BASE64` | secret de GitHub Actions |
`RELEASE_STORE_PASSWORD` | secret de GitHub Actions |
`RELEASE_KEY_PASSWORD` | secret de GitHub Actions |
`RELEASE_KEY_ALIAS` | secret de GitHub Actions |

Solo la variante `release` las necesita. `assembleDebug` funciona sin ninguna.

Esto evita que la copia que circule con cada commit sea la clave de producción. No
cambia el modelo de amenaza descrito arriba —el historial sigue ahí— pero evita
propagarlo hacia adelante.

## Si algún día hay que rotar

Rotar es posible, y el procedimiento depende de si para entonces hay instalaciones
en dispositivos:

1. Generar un keystore nuevo: `keytool -genkeypair -v -keystore nuevo.jks ...`
2. Actualizar los secrets de GitHub y `TERMUX_KEYSTORE_PATH`.
3. Publicar el release nuevo.
4. **Los dispositivos que ya tienen la app instalada con la clave vieja no podrán
   actualizarse** — Android rechaza cambios de firma. Hay que desinstalar primero,
   lo cual borra el estado de la app.

Ese punto 4 es el coste real de rotar, y la razón por la que aquí no se hace.

## Referencias

- [Reproducible Builds](https://reproducible-builds.org/)
- [Verificación de firmas de Android](https://developer.android.com/studio/publish/app-signing)
- [Backing up Termux](https://wiki.termux.com/wiki/Backing_up_Termux) — para
  respaldar datos antes de cualquier desinstalación.