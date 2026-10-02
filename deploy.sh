#!/usr/bin/env bash
# Despliegue de Termux Ultra en un dispositivo Motorola por adb.
#
# Contexto importante: el manifiesto declara
#   android:sharedUserId="${TERMUX_PACKAGE_NAME}"  →  sharedUserId="com.termux"
# Eso obliga a que TODAS las apps Termux (app, API, Boot, Styling, Tasker,
# Widget) compartan UID y estén firmadas con la MISMA clave. Si el dispositivo
# tiene un Termux de otra fuente (F-Droid, Play, una build previa con otro
# keystore), la instalación falla con:
#   INSTALL_FAILED_SHARED_USER_INCOMPATIBLE
# o "signatures do not match". Por eso este script detecta y desinstala antes.
#
# Uso:  ./deploy.sh            (dry-run: solo informa, no cambia nada)
#       ./deploy.sh --install  (pide confirmación si hay que desinstalar)
#       DEPLOY_YES=1 ./deploy.sh --install   (sin confirmación, para CI/no interactivo)
#
# Variables de entorno (todas con valor por defecto, no hace falta exportar ninguna):
#   APK_PATH        APK a instalar. Por defecto busca el de debug; si no está,
#                   cae al de release, y si tampoco, avisa de compilar.
#   BUILD_TOOLS    directorio de build-tools del SDK (aapt2/apksigner).
#   ANDROID_SDK    raíz del SDK. Por defecto se deduce de local.properties y,
#                   si no está, de ANDROID_HOME / ANDROID_SDK_ROOT / $HOME/Android/sdk.
#   ADB_SERIAL     dispositivo concreto cuando hay varios conectados.
#   PKG            paquete de la app. Cambiarlo solo si compilas con otro
#                   applicationId; también afecta a la lista de add-ons.
#   MIN_API        versión mínima de Android aceptada (por defecto 26).
#   DEPLOY_YES=1   omite la confirmación interactiva.
#
# No se usa `set -e`: las comprobaciones de adb son asíncronas por naturaleza y un
# grep sin coincidencias (código 1) es una respuesta válida, no un error. Los
# puntos donde el fallo sí importa usan `die` explícito.
set -uo pipefail

say()  { printf '\n\033[1;36m== %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33m!! %s\033[0m\n' "$*"; }
ok()   { printf '\033[1;32mOK %s\033[0m\n' "$*"; }
die()  { printf '\033[1;31mXX %s\033[0m\n' "$*" >&2; exit 1; }

# Raíz del repo, deducida de la ubicación del propio script: el guion funciona
# desde cualquier directorio y en una copia del proyecto en otra ruta.
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

PKG="${PKG:-com.termux}"
ADDONS=(com.termux.api com.termux.boot com.termux.styling com.termux.tasker com.termux.widget)
MODE="${1:-dryrun}"
MIN_API="${MIN_API:-26}"
# Actividad de entrada, relativa al paquete (puedes cambiar el paquete con PKG=...)
MAIN_ACTIVITY="${MAIN_ACTIVITY:-com.termux.app.SplashActivity}"

# --------------------------------------------------------------- rutas del APK
# Antes era una ruta absoluta fija a /home/jhon/..., así que el guion solo
# funcionaba en esa máquina y en ese directorio concreto.
find_apk() {
  local variant="$1"
  echo "$REPO_ROOT/app/build/outputs/apk/$variant/app-$variant.apk"
}

if [ -n "${APK_PATH:-}" ]; then
  APK="$APK_PATH"
elif [ -f "$(find_apk debug)" ]; then
  APK="$(find_apk debug)"
elif [ -f "$(find_apk release)" ]; then
  APK="$(find_apk release)"
  warn "No hay APK de debug; usando el de release"
else
  APK=""
fi

# ------------------------------------------------------------ raíz del SDK
# local.properties es la fuente de verdad de Gradle (sdk.dir). Se usa antes que
# las variables de entorno para que el guion inspecte el APK con las mismas
# herramientas que la build.
sdk_root() {
  if [ -n "${ANDROID_SDK:-}" ]; then
    echo "$ANDROID_SDK"
    return
  fi
  local lp="$REPO_ROOT/local.properties"
  if [ -f "$lp" ]; then
    # sdk.dir puede venir escapado (\: o Windows-style); se limpia.
    local v
    v=$(sed -n 's/^[[:space:]]*sdk\.dir[[:space:]]*=[[:space:]]*//p' "$lp" | tail -1)
    v="${v//\\:/:}"
    if [ -n "$v" ] && [ -d "$v" ]; then echo "$v"; return; fi
  fi
  for candidate in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/Android/sdk"; do
    [ -n "$candidate" ] && [ -d "$candidate" ] && { echo "$candidate"; return; }
  done
  echo ""
}
ANDROID_SDK_DIR="$(sdk_root)"

# ---------------------------------------------------------------- dispositivo
say "Dispositivo"

# Con varios móviles conectados, adb se queja y falla. ADB_SERIAL fija cuál.
ADB=(adb)
if [ -n "${ADB_SERIAL:-}" ]; then
  ADB=(adb -s "$ADB_SERIAL")
fi

# Sin timeout se queda colgado para siempre esperando un móvil que no está.
if ! "${ADB[@]}" start-server >/dev/null 2>&1; then warn "No se pudo arrancar el servidor adb"; fi
if ! timeout 15 "${ADB[@]}" wait-for-device 2>/dev/null; then
  die "No hay dispositivo${ADB_SERIAL:+ ($ADB_SERIAL)} conectado.
   1) Conecta el móvil por USB y ponlo en modo de carga (no solo carga).
   2) Ajustes → Aplicaciones → acceso a USB especial: Archivos y medios.
   3) Activa Depuración USB y acepta el diálogo de autorización en su pantalla."
fi
"${ADB[@]}" devices -l | sed 's/^/   /'

state=$("${ADB[@]}" get-state 2>/dev/null) || die "adb no responde"
[ "$state" = "device" ] || die "Estado '$state' (unauthorized = acepta el diálogo de depuración en la pantalla del móvil)"

model=$("${ADB[@]}" shell getprop ro.product.model 2>/dev/null | tr -d '\r')
rel=$("${ADB[@]}" shell getprop ro.build.version.release 2>/dev/null | tr -d '\r')
sdk=$("${ADB[@]}" shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r')
abi=$("${ADB[@]}" shell getprop ro.product.cpu.abi 2>/dev/null | tr -d '\r')
ok "$model · Android $rel (API $sdk) · $abi"
[ "${sdk:-0}" -ge "$MIN_API" ] || die "Requiere API $MIN_API+; el dispositivo reporta API ${sdk:-?} (ajustable con MIN_API=...)"

# ------------------------------------------------------------------ el APK
say "APK"
if [ -z "$APK" ] || [ ! -f "$APK" ]; then
  die "No se encontró ningún APK que instalar.
   Compila antes:  ./gradlew :app:assembleDebug
   O indica uno:   APK_PATH=/ruta/al/app.apk $0${APK:+  (buscado: $APK)}"
fi
ls -lh "$APK" | awk '{print "   tamaño: "$5}'
# Build-tools: tomar la más reciente del SDK en vez de fijar una versión. Con la
# versión fijada, un SDK con otra versión no encuentra aapt2/apksigner y los
# `2>/dev/null` silenciaban el fallo: la firma salía "desconocida" como si nada.
BT="${BUILD_TOOLS:-}"
if [ -z "$BT" ] && [ -n "$ANDROID_SDK_DIR" ]; then
  BT=$(ls -d "$ANDROID_SDK_DIR"/build-tools/*/ 2>/dev/null | sort -V | tail -1)
  BT="${BT%/}"
fi
if [ -n "$BT" ] && [ -x "$BT/aapt2" ]; then
  "$BT/aapt2" dump badging "$APK" 2>/dev/null | grep -E "^package|locales" | sed 's/^/   /'
  apksigner_signer=$("$BT/apksigner" verify --print-certs "$APK" 2>/dev/null | grep "SHA-256" | head -1)
else
  warn "build-tools no encontrado; se omite la verificación de firma"
  apksigner_signer=""
fi
echo "   firma: ${apksigner_signer:-desconocida}"

# --------------------------------------------------------- qué hay instalado hoy
say "Estado actual en el dispositivo"
# `pm list packages` se pide UNA vez: antes se llamaba una vez por paquete en el
# bucle, y en un dispositivo con muchas apps cada llamada tarda.
#
# NO usar `grep -q` aquí. `grep -q` cierra el pipe en cuanto encuentra la
# coincidencia, `adb` recibe SIGPIPE y con `pipefail` el pipeline devuelve 141
# en vez de 0. El `if` daba por no encontrado un paquete que sí estaba
# instalado. Se usa `grep -x ... || true` y se compara el valor, no el rc.
installed_packages=$("${ADB[@]}" shell pm list packages 2>/dev/null | tr -d '\r' || true)

is_installed() { grep -qx "package:$1" <<<"$installed_packages"; }

installed=""
conflicts=()
if is_installed "$PKG"; then installed="$PKG"; fi

# --------------------------------------- comparar firma de lo que está vivo
# `dumpsys` solo expone un hash corto (signatures=[7c3fcce]) que no es
# comparable con el SHA-256 que da apksigner. Para decidir bien hay que extraer
# el APK instalado y sacarle la huella de verdad.
#
# Esto evita lo que pasaba antes: `conflicts` incluía siempre el paquete
# principal, así que cada --install desinstalaba y reinstalaba la app aunque
# las dos estuvieran firmadas con la MISMA clave. Eso borra el home, los
# paquetes y el estado de los plugins, cada vez que se probaba una build.
installed_digest=""
new_digest=""
if [ -n "$BT" ] && [ -x "$BT/apksigner" ]; then
  new_digest=$("$BT/apksigner" verify --print-certs "$APK" 2>/dev/null \
    | grep -m1 'SHA-256' | grep -oE '[0-9a-f]{64}')
fi

# Extraer el APK instalado es lo unico que da la huella real: dumpsys solo
# enseña un hash corto no comparable. Se hace en un directorio temporal y se
# borra siempre; el APK pesa ~110 MB asi que no se descarga mas de una vez.
tmpdir=""
if [ -n "$installed" ] && [ -n "$new_digest" ]; then
  tmpdir=$(mktemp -d)
  remote_apk=$("${ADB[@]}" shell pm path "$PKG" 2>/dev/null | tr -d '\r' | head -1 | sed 's/^package://')
  if [ -n "$remote_apk" ]; then
    "${ADB[@]}" pull "$remote_apk" "$tmpdir/instalado.apk" >/dev/null 2>&1
    if [ -s "$tmpdir/instalado.apk" ]; then
      installed_digest=$("$BT/apksigner" verify --print-certs "$tmpdir/instalado.apk" 2>/dev/null \
        | grep -m1 'SHA-256' | grep -oE '[0-9a-f]{64}')
    fi
  fi
  [ -n "$tmpdir" ] && rm -rf "$tmpdir"
fi

same_signature=0
if [ -n "$installed" ]; then
  say "Firma"
  if [ -n "$installed_digest" ] && [ -n "$new_digest" ] && [ "$installed_digest" = "$new_digest" ]; then
    same_signature=1
  fi
fi

# Sólo entra en `conflicts` lo que realmente impide instalar: el paquete
# principal únicamente si su firma es distinta de la del APK.
conflicts=()
for p in "${ADDONS[@]}"; do
  if is_installed "$p"; then
    src=$("${ADB[@]}" shell dumpsys package "$p" 2>/dev/null | grep -m1 -oE 'installerPackageName=[^ ]*' | cut -d= -f2)
    conflicts+=("$p (instalada por: ${src:-desconocido})")
    echo "   PRESENTE: $p  ← ${src:-origen desconocido}"
  fi
done

if [ -n "$installed" ]; then
  if [ "$same_signature" -eq 1 ]; then
    ok "$PKG ya instalada con la misma firma — se actualiza sin perder datos"
  else
    # Firma distinta o no verificable: instalar encima fallaría con
    # UPDATE_INCOMPATIBLE / signatures do not match. Hay que desinstalar.
    src=$("${ADB[@]}" shell dumpsys package "$PKG" 2>/dev/null | grep -m1 -oE 'installerPackageName=[^ ]*' | cut -d= -f2)
    conflicts=("$PKG (instalada por: ${src:-desconocido}, FIRMA DISTINTA)" "${conflicts[@]+"${conflicts[@]}"}")
    echo "   PRESENTE: $PKG  ← ${src:-origen desconocido} (firma distinta, hay que reemplazarla)"
  fi
else
  echo "   $PKG no está instalada"
fi

# ------------------------------------------------------------------ decisión
if [ "$MODE" != "--install" ]; then
  say "Dry-run: no se cambió nada"
  echo "   Para desinstalar lo incompatible e instalar:"
  echo "     $0 --install"
  if [ ${#conflicts[@]} -gt 0 ]; then
    warn "Se desinstalarían (y se perderían sus datos): ${conflicts[*]}"
  elif [ -n "$installed" ]; then
    ok "Se instalará encima: la firma coincide, tus datos se conservan."
  else
    echo "   No hay nada que desinstalar."
  fi
  exit 0
fi

# --------------------------------------------------------------- confirmación
# --install borra paquetes de forma irreversible (el UID compartido se rompe si
# se quita solo uno). Antes no había ninguna barrera: el flag basta para ejecutar.
if [ "${DEPLOY_YES:-0}" != "1" ]; then
  if [ ${#conflicts[@]} -eq 0 ]; then
    ok "No hay apps Termux instaladas: se instalará sin desinstalar nada."
  else
    say "Se DESINSTALARÁN estos paquetes (irreversible)"
    printf '     %s\n' "${conflicts[@]}"
    warn "Se borra TODO el contenido de /data/data/$PKG, incluido el home"
    warn "(paquetes instalados, scripts, configuracion de plugins)."
    warn "Si tienes ahi algo que valoras, haz backup antes: https://wiki.termux.com/wiki/Backing_up_Termux"
    printf '\n   ¿Continuar? [s/N] '
    read -r reply
    case "$reply" in
      s|S|y|Y|si|sí|SI|SÍ) ;;
      *) die "Cancelado por el usuario. No se cambió nada." ;;
    esac
  fi
fi

# --------------------------------------------------------- desinstalación
if [ ${#conflicts[@]} -gt 0 ]; then
  say "Desinstalando apps Termux existentes (comparten UID y clave)"
  warn "El UID $PKG se comparte: quitar solo una deja el resto con UID roto."
  for p in "$PKG" "${ADDONS[@]}"; do
    if is_installed "$p"; then
      if "${ADB[@]}" uninstall "$p" >/dev/null 2>&1; then
        ok "desinstalada $p"
      else
        warn "no se pudo desinstalar $p (¿root?)"
      fi
    fi
  done
  sleep 2
fi

# --------------------------------------------------------------- instalación
say "Instalando"
# Antes: `if adb install ... | sed ...; then :; fi` — el cuerpo del if no hacía
# nada, el código de salida de adb lo enmascaraba el pipe y el script no abortaba:
# si la instalación fallaba seguía al pm list, veía el paquete de una
# instalación previa y reportaba "instalada" con éxito falso.
# Ahora el código de salida de adb se captura directamente y un fallo es un fallo.
if ! "${ADB[@]}" install -r -d "$APK" 2>&1 | sed 's/^/   /'; then
  warn "adb install devolvió error"
fi
# Verificación posterior: hay que consultar DE NUEVO, la lista cacheada de antes
# de desinstalar ya no refleja el estado actual.
if ! "${ADB[@]}" shell pm list packages 2>/dev/null | tr -d '\r' | grep -x "package:$PKG" >/dev/null; then
  die "La instalación falló. Si el error es INSTALL_FAILED_SHARED_USER_INCOMPATIBLE
   o de firmas, quedan apps Termux de otra fuente: desinstálalas desde Ajustes."
fi

say "Verificación"
"${ADB[@]}" shell dumpsys package "$PKG" 2>/dev/null | grep -m1 -E 'versionName|versionCode' | sed 's/^/   /'
"${ADB[@]}" shell pm path "$PKG" | sed 's/^/   /'
"${ADB[@]}" shell am start -n "$PKG/$MAIN_ACTIVITY" 2>&1 | sed 's/^/   /' || true
ok "Lanzada. En el móvil: Ajustes → Ajustes de Termux → Idioma → Español"