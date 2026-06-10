#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

ASM_VER="9.6"
ASM_JAR="asm-${ASM_VER}.jar"
ASM_COMMONS_JAR="asm-commons-${ASM_VER}.jar"
M2_REPO="https://repo1.maven.org/maven2/org/ow2/asm"

OUT_JAR="gto-guard-agent.jar"
BUILD_DIR="build"
SRC_DIR="src"

# ---- download ASM if missing ----
mkdir -p lib
if [ ! -f "lib/${ASM_JAR}" ]; then
    echo "Downloading ASM ${ASM_VER}..."
    curl -fsSL "${M2_REPO}/asm/${ASM_VER}/${ASM_JAR}" -o "lib/${ASM_JAR}"
fi
if [ ! -f "lib/${ASM_COMMONS_JAR}" ]; then
    echo "Downloading ASM Commons ${ASM_VER}..."
    curl -fsSL "${M2_REPO}/asm-commons/${ASM_VER}/${ASM_COMMONS_JAR}" -o "lib/${ASM_COMMONS_JAR}"
fi

# ---- compile ----
rm -rf "${BUILD_DIR}" "${OUT_JAR}"
mkdir -p "${BUILD_DIR}"

echo "Compiling..."
javac -cp "lib/${ASM_JAR}:lib/${ASM_COMMONS_JAR}" \
    -d "${BUILD_DIR}" \
    $(find "${SRC_DIR}" -name "*.java")

# ---- unpack ASM into build dir (shade) ----
echo "Shading ASM into build dir..."
unzip -qo "lib/${ASM_JAR}" -d "${BUILD_DIR}"
unzip -qo "lib/${ASM_COMMONS_JAR}" -d "${BUILD_DIR}"
# Remove ASM module-info to avoid conflicts on older Java
rm -f "${BUILD_DIR}/module-info.class" "${BUILD_DIR}/META-INF/versions/"*"/module-info.class" 2>/dev/null || true
rm -rf "${BUILD_DIR}/META-INF/versions" 2>/dev/null || true
# Remove ASM META-INF to avoid service conflicts
rm -rf "${BUILD_DIR}/META-INF/maven" 2>/dev/null || true
rm -rf "${BUILD_DIR}/META-INF/services" 2>/dev/null || true

# ---- create MANIFEST ----
mkdir -p "${BUILD_DIR}/META-INF"
cat > "${BUILD_DIR}/META-INF/MANIFEST.MF" << MANIFEST
Manifest-Version: 1.0
Premain-Class: gto.guard.Agent
Agent-Class: gto.guard.Agent
Can-Retransform-Classes: true
Can-Redefine-Classes: true
MANIFEST

# ---- package jar ----
echo "Packaging ${OUT_JAR}..."
jar cfm "${OUT_JAR}" "${BUILD_DIR}/META-INF/MANIFEST.MF" -C "${BUILD_DIR}" .

echo ""
echo "Done: ${OUT_JAR}"
echo "Usage: java -javaagent:${OUT_JAR} -jar forge-server.jar ..."
