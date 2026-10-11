#!/usr/bin/env bash
set -e

VERSION="${1:-0.1.0-beta}"
OUTPUT_DIR="build/libs"
WORK_DIR="/tmp/speedrun_ci_build"

JAVAC_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
JAR_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}jar"

echo "Building SpeedRun? v${VERSION} using $($JAVAC_BIN --version)..."
rm -rf "$WORK_DIR" && mkdir -p "$WORK_DIR/lib" "$WORK_DIR/bin" "$OUTPUT_DIR"

# Download 26.3 client
if [ ! -f "/tmp/minecraft-26.3-client.jar" ]; then
    echo "Fetching Minecraft 26.3 client..."
    curl -s -L -o /tmp/minecraft-26.3-client.jar "https://piston-data.mojang.com/v1/objects/e877b6a07acd633fb3bb475002175cec036e7b87/client.jar"
fi

# Download Fabric Loader
if [ ! -f "/tmp/fabric-loader-0.19.5.jar" ]; then
    echo "Fetching Fabric Loader..."
    curl -s -L -o /tmp/fabric-loader-0.19.5.jar "https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.19.5/fabric-loader-0.19.5.jar"
fi

# Download Fabric API & extract nested modules
if [ ! -d "/tmp/fabric_api_modules_26.3" ]; then
    echo "Fetching Fabric API 26.3..."
    curl -s -L -o /tmp/fabric-api-0.161.0.jar "https://cdn.modrinth.com/data/P7dR8mSH/versions/bNnaTiuM/fabric-api-0.161.0%2B26.3.jar"
    mkdir -p /tmp/fabric_api_modules_26.3
    unzip -q -j /tmp/fabric-api-0.161.0.jar "META-INF/jars/*.jar" -d /tmp/fabric_api_modules_26.3
fi

# Download Mod Menu
if [ ! -f "/tmp/modmenu-21.0.0.jar" ] || [ ! -s "/tmp/modmenu-21.0.0.jar" ]; then
    echo "Fetching Mod Menu 21.0.0..."
    curl -s -L -o /tmp/modmenu-21.0.0.jar "https://cdn.modrinth.com/data/mOgUt4GM/versions/kyy7dbrZ/modmenu-21.0.0.jar"
fi

# Download WorldReset API
if [ ! -f "/tmp/worldreset-api-26.3.jar" ]; then
    echo "Fetching WorldReset API..."
    curl -s -L -o /tmp/worldreset-api-26.3.jar "https://github.com/Libreh/WorldReset/releases/download/0.2.2%2B26.3/worldreset-api-0.2.2%2B26.3.jar"
fi

# Download TAB API
if [ ! -f "/tmp/tab-api.jar" ]; then
    echo "Fetching TAB..."
    curl -s -L -o /tmp/tab-api.jar "https://github.com/NEZNAMY/TAB/releases/download/6.2.0/TAB.v6.2.0.jar"
fi

# Fetch Mojang libraries (Gson, Brigadier, etc.)
python3 scripts/fetch_libs.py

find /tmp/mc_libs -name "*.jar" -size 0 -delete
rm -f /tmp/mc_libs/jorbis-0.0.17.jar

CP="/tmp/minecraft-26.3-client.jar:/tmp/fabric-loader-0.19.5.jar:/tmp/modmenu-21.0.0.jar:/tmp/worldreset-api-26.3.jar:/tmp/tab-api.jar"
for f in /tmp/mc_libs/*.jar; do
    CP="$CP:$f"
done
for f in /tmp/fabric_api_modules_26.3/*.jar; do
    CP="$CP:$f"
done

echo "Compiling Java sources..."
$JAVAC_BIN -cp "$CP" $(find src/main/java -name "*.java") -d "$WORK_DIR/bin"

echo "Copying resources..."
cp -r src/main/resources/* "$WORK_DIR/bin/"
sed -i "s/\${version}/$VERSION/g" "$WORK_DIR/bin/fabric.mod.json"

JAR_PATH="$OUTPUT_DIR/speedrun-${VERSION}.jar"
echo "Creating $JAR_PATH..."
ROOT_DIR="$(pwd)"
(cd "$WORK_DIR/bin" && $JAR_BIN -cvf "$ROOT_DIR/$JAR_PATH" .)

echo "Build successful: $JAR_PATH"
