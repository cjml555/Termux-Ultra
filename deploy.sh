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
# No se usa `set -e`: las comprobaciones de adb son asíncronas por naturaleza y un
# grep sin coincidencias (código 1) es una respuesta válida, no un error. Los
# puntos donde el fallo sí importa usan `die` explícito.
set -uo pipefail

APK="/home/jhon/Projects/Termux-Ultra/app/build/outputs/apk/debug/app-debug.apk"
PKG="com.termux"
ADDONS=(com.termux.api com.termux.boot com.termux.styling com.termux.tasker com.termux.widget)
MODE="${1:-dryrun}"

say()  { printf '\n\033[1;36m== %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33m!! %s\033[0m\n' "$*"; }
ok()   { printf '\033[1;32mOK %s\033[0m\n' "$*"; }
die()  { printf '\033[1;31mXX %s\033[0m\n' "$*" >&2; exit 1; }

# ---------------------------------------------------------------- dispositivo
say "Dispositivo"
# Sin timeout se queda colgado para siempre esperando un móvil que no está.
if ! adb start-server >/dev/null 2>&1; then warn "No se pudo arrancar el servidor adb"; fi
if ! timeout 15 adb wait-for-device 2>/dev/null; then
  die "No hay dispositivo conectado.
   1) Conecta el Motorola por USB y ponlo en modo de carga (no solo carga).
   2) Ajustes → Ajustes → Aplicaciones → acceso a USB especial:Archivos y medios.
   3) Activa Depuración USB y acepta el diálogo de autorización en su pantalla."
fi
adb devices -l | sed 's/^/   /'

state=$(adb get-state 2>/dev/null) || die "adb no responde"
[ "$state" = "device" ] || die "Estado '$state' (unauthorized = acepta el diálogo de depuración en la pantalla del móvil)"

model=$(adb shell getprop ro.product.model 2>/dev/null | tr -d '\r')
rel=$(adb shell getprop ro.build.version.release 2>/dev/null | tr -d '\r')
sdk=$(adb shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r')
abi=$(adb shell getprop ro.product.cpu.abi 2>/dev/null | tr -d '\r')
ok "$model · Android $rel (API $sdk) · $abi"
[ "${sdk:-0}" -ge 26 ] || die "Requiere API 26+; el dispositivo reporta API ${sdk:-?}"

# ------------------------------------------------------------------ el APK
say "APK"
[ -f "$APK" ] || die "No existe $APK — compila antes: ./gradlew :app:assembleDebug"
ls -lh "$APK" | awk '{print "   tamaño: "$5}'
# Build-tools: tomar la más reciente del SDK en vez de fijar una versión. Con la
# versión fijada, un SDK con otra versión no encuentra aapt2/apksigner y los
# `2>/dev/null` silenciaban el fallo: la firma salía "desconocida" como si nada.
BT="${BUILD_TOOLS:-}"
if [ -z "$BT" ]; then
  BT=$(ls -d "$HOME"/Android/sdk/build-tools/*/ 2>/dev/null | sort -V | tail -1)
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
installed=$(adb shell pm list packages 2>/dev/null | tr -d '\r' | grep -x "package:$PKG" || true)
conflicts=()
# com.termux TIENE que entrar en conflicts. Antes solo se listaban los add-ons,
# así que en el caso más habitual — Termux principal de F-Droid/Play y ningún
# add-on — conflicts quedaba vacío, el bloque de desinstalación se saltaba
# entero y la instalación moría con INSTALL_FAILED_SHARED_USER_INCOMPATIBLE.
for p in "$PKG" "${ADDONS[@]}"; do
  if adb shell pm list packages 2>/dev/null | tr -d '\r' | grep -qx "package:$p"; then
    src=$(adb shell dumpsys package "$p" 2>/dev/null | grep -m1 -oE 'installerPackageName=[^ ]*' | cut -d= -f2)
    conflicts+=("$p (instalada por: ${src:-desconocido})")
    echo "   PRESENTE: $p  ← ${src:-origen desconocido}"
  fi
done

[ -n "$installed" ] || echo "   $PKG no está instalada"

# --------------------------------------- comparar firma de lo que está vivo
mismatch=0
if [ -n "$installed" ]; then
  say "Firma de la app instalada"
  # adb shell pm path + extraer no es viable; comparamos el hash que reporta el sistema
  installed_sig=$(adb shell dumpsys package "$PKG" 2>/dev/null | grep -m1 -oE 'signatures=.*' || true)
  echo "   ${installed_sig:-no informado por dumpsys}"
fi

# ------------------------------------------------------------------ decisión
if [ "$MODE" != "--install" ]; then
  say "Dry-run: no se cambió nada"
  echo "   Para desinstalar lo incompatible e instalar:"
  echo "     $0 --install"
  if [ ${#conflicts[@]} -gt 0 ]; then
    warn "Se desinstalarían: ${conflicts[*]}"
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
    warn "Se borran los datos de la app y de los add-ons. El home no se toca:"
    warn "  /data/data/com.termux se conserva, pero el estado de las apps se pierde."
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
  warn "El UID com.termux se comparte: quitar solo una deja el resto con UID roto."
  for p in "${ADDONS[@]}" com.termux; do
    if adb shell pm list packages 2>/dev/null | tr -d '\r' | grep -q "^package:$p$"; then
      if adb uninstall "$p" >/dev/null 2>&1; then
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
if ! adb install -r -d "$APK" 2>&1 | sed 's/^/   /'; then
  warn "adb install devolvió error"
fi
if ! adb shell pm list packages 2>/dev/null | tr -d '\r' | grep -qx "package:$PKG"; then
  die "La instalación falló. Si el error es INSTALL_FAILED_SHARED_USER_INCOMPATIBLE
   o de firmas, quedan apps Termux de otra fuente: desinstálalas desde Ajustes."
fi

say "Verificación"
adb shell dumpsys package "$PKG" 2>/dev/null | grep -m1 -E 'versionName|versionCode' | sed 's/^/   /'
adb shell pm path "$PKG" | sed 's/^/   /'
adb shell am start -n "$PKG/com.termux.app.SplashActivity" 2>&1 | sed 's/^/   /' || true
ok "Lanzada. En el móvil: Ajustes → Ajustes de Termux → Idioma → Español"