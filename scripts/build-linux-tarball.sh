#!/usr/bin/env bash
set -e

# ==============================================================================
# Script de empaquetado Portable Tarball para Linux x86_64
# Permite ejecutar Boumusic descomprimiendo y ejecutando ./boumusic (con JRE embebido)
# ==============================================================================

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTPUT_DIR="$PROJECT_DIR/build/compose/binaries/main/tarball"
VERSION="2.1.0"

echo "=== [1/3] Compilando distribución binaria standalone con Gradle ==="
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

mkdir -p "$OUTPUT_DIR"
TARBALL="$OUTPUT_DIR/Boumusic-$VERSION-linux-x64.tar.gz"

echo "=== [2/3] Creando archivo tar.gz portable ==="
tar -czf "$TARBALL" -C "$(dirname "$DIST_DIR")" "$(basename "$DIST_DIR")"

echo "=== [3/3] Calculando checksum SHA-256 ==="
sha256sum "$TARBALL"
echo "Tarball generado con éxito en: $TARBALL"
