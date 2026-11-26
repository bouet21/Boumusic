#!/usr/bin/env bash
set -e

# ==============================================================================
# Script de construcción de AppImage para Boumusic Desktop en Linux x86_64
# No requiere que el usuario final tenga Java instalado (empaqueta JRE autónomo)
# ==============================================================================

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD_DIR="$PROJECT_DIR/build/appimage"
APP_DIR="$BUILD_DIR/Boumusic.AppDir"
OUTPUT_DIR="$PROJECT_DIR/build/compose/binaries/main/appimage"
VERSION="2.0.1"

echo "=== [1/5] Compilando distribución binaria standalone con Gradle ==="
cd "$PROJECT_DIR"
./gradlew createDistributable

DIST_DIR="$PROJECT_DIR/build/compose/binaries/main/app/boumusic"
if [ ! -d "$DIST_DIR" ]; then
    DIST_DIR="$PROJECT_DIR/build/compose/binaries/main/app/Boumusic"
fi

if [ ! -d "$DIST_DIR" ]; then
    echo "ERROR: No se encontró la carpeta de distribución compilada en $DIST_DIR"
    exit 1
fi

echo "=== [2/5] Creando estructura AppDir ==="
rm -rf "$BUILD_DIR"
mkdir -p "$APP_DIR/usr/bin"
mkdir -p "$APP_DIR/usr/lib"
mkdir -p "$APP_DIR/usr/share/applications"
mkdir -p "$APP_DIR/usr/share/icons/hicolor/256x256/apps"
mkdir -p "$OUTPUT_DIR"

# Copiar aplicación completa (incluyendo runtime JRE embebido y dependencias)
cp -r "$DIST_DIR"/* "$APP_DIR/usr/bin/"

# Copiar Desktop Entry e icono
cp "$PROJECT_DIR/scripts/boumusic.desktop" "$APP_DIR/usr/share/applications/boumusic.desktop"
cp "$PROJECT_DIR/scripts/boumusic.desktop" "$APP_DIR/boumusic.desktop"
cp "$PROJECT_DIR/src/main/resources/icons/icon.png" "$APP_DIR/usr/share/icons/hicolor/256x256/apps/boumusic.png"
cp "$PROJECT_DIR/src/main/resources/icons/icon.png" "$APP_DIR/boumusic.png"
cp "$PROJECT_DIR/src/main/resources/icons/icon.png" "$APP_DIR/.DirIcon"

echo "=== [3/5] Generando AppRun ejecutable ==="
cat << 'EOF' > "$APP_DIR/AppRun"
#!/bin/sh
SELF=$(readlink -f "$0")
HERE=${SELF%/*}
export PATH="${HERE}/usr/bin:${PATH}"
export LD_LIBRARY_PATH="${HERE}/usr/lib:${HERE}/usr/bin/runtime/lib:${LD_LIBRARY_PATH}"
cd "$HERE/usr/bin"
exec ./boumusic "$@"
EOF
chmod +x "$APP_DIR/AppRun"

echo "=== [4/5] Descargando appimagetool si no existe ==="
cd "$BUILD_DIR"
if [ ! -f "appimagetool" ]; then
    curl -L -o appimagetool "https://github.com/AppImage/AppImageKit/releases/download/continuous/appimagetool-x86_64.AppImage"
    chmod +x appimagetool
fi

echo "=== [5/5] Creando Boumusic-$VERSION-x86_64.AppImage ==="
ARCH=x86_64 ./appimagetool --no-appstream "$APP_DIR" "$OUTPUT_DIR/Boumusic-$VERSION-x86_64.AppImage"

echo "=== Proceso completado con éxito ==="
echo "AppImage generado en: $OUTPUT_DIR/Boumusic-$VERSION-x86_64.AppImage"
sha256sum "$OUTPUT_DIR/Boumusic-$VERSION-x86_64.AppImage"
