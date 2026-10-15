#!/usr/bin/env bash
# 按键改键器 —— 手工打 APK
#
# 用 JRE8 + ECJ + aapt2 + d8 + apksigner，不依赖 Gradle，也不需要完整 Android SDK。
#
# 流程：
#   源码搬到纯 ASCII 临时目录
#     -> aapt2 编译资源
#     -> aapt2 link 出 base.apk，同时生成 R.java
#     -> ECJ 编译 java（含 R.java）-> .class
#     -> d8 把 .class 转成 classes.dex
#     -> 把 dex 塞回 APK
#     -> apksigner 签名
#   产物：build/app.apk
#
# ⚠️ 为什么要先搬到临时目录：
#   aapt2 / apksigner 是 Windows 程序，路径里带中文就直接报
#   “failed to open directory: 系统找不到指定的文件”。
#   所以这棵源码树可以放在任何位置（哪怕目录名是中文），
#   脚本会自己搬到一个纯 ASCII 的临时目录去编。
#
# 工具链准备见 README 的「自己编译」一节。
set -e

# 脚本自己所在的目录 = 源码根。搬到哪都能编。
SRC="$(cd "$(dirname "$0")" && pwd)"
TOOLCHAIN="$SRC/toolchain"

# ---------- 工具链定位 ----------
# 优先级从高到低，每一样都试三遍：
#   1) 环境变量直接指定（AAPT2 / D8JAR / APKSIGNER / ANDROIDJAR / ECJ / JAVA）
#   2) 源码根的 toolchain/ 目录（把文件扁平放进去即可，见 README）
#   3) 标准 Android SDK 布局（build-tools 版本号不固定，用通配兜）
if [ -n "$ANDROID_SDK" ]; then
  SDK="$ANDROID_SDK"
else
  SDK=""
  for c in "$HOME/Android/Sdk" "$HOME/Library/Android/sdk" \
           "$HOME/AppData/Local/Android/Sdk"; do
    if [ -d "$c" ]; then SDK="$c"; break; fi
  done
fi
[ -z "$SDK" ] && SDK="$HOME/Android/Sdk"

sdk_find() {  # sdk_find <文件名> <SDK 子目录>
  local name="$1" sub="$2" hit
  if [ -n "$sub" ]; then
    hit="$(ls -1 "$SDK/$sub"/*/"$name" 2>/dev/null | tail -1)"
  else
    hit="$(find "$SDK" -maxdepth 3 -type f -name "$name" 2>/dev/null | head -1)"
  fi
  printf '%s' "$hit"
}

resolve() {  # resolve <环境变量名> <toolchain 文件名> <SDK 子目录>
  local v="${!1:-}"
  if [ -n "$v" ];            then printf '%s' "$v"; return; fi
  if [ -f "$TOOLCHAIN/$2" ]; then printf '%s' "$TOOLCHAIN/$2"; return; fi
  sdk_find "$2" "$3"
}

JAVA="${JAVA:-}"
[ -z "$JAVA" ] && [ -n "$JAVA_HOME" ] && JAVA="$JAVA_HOME/bin/java"
[ -z "$JAVA" ] && JAVA="$(command -v java || command -v java.exe || true)"

# 编译期用哪个 android.jar（决定能引用哪些 API）。默认取 SDK 里版本号最大的那个。
PLATFORM="${ANDROID_PLATFORM:-}"
if [ -z "$PLATFORM" ] && [ -d "$SDK/platforms" ]; then
  PLATFORM="$(ls -1 "$SDK/platforms" 2>/dev/null | sort -V | tail -1)"
fi
[ -z "$PLATFORM" ] && PLATFORM="android-30"

AAPT2="$(resolve AAPT2 aapt2.exe build-tools)"
D8JAR="$(resolve D8JAR d8.jar build-tools)"
APKSIGNER="$(resolve APKSIGNER apksigner.jar build-tools)"
ECJ="$(resolve ECJ ecj-3.26.0.jar '')"

# android.jar 单独挑：先按上面定的 PLATFORM 找，找不到再在 platforms 里兜
if [ -n "${ANDROIDJAR:-}" ]; then
  :
elif [ -f "$TOOLCHAIN/android.jar" ]; then
  ANDROIDJAR="$TOOLCHAIN/android.jar"
elif [ -f "$SDK/platforms/$PLATFORM/android.jar" ]; then
  ANDROIDJAR="$SDK/platforms/$PLATFORM/android.jar"
else
  ANDROIDJAR="$(sdk_find android.jar platforms)"
fi

missing=""
for pair in "JAVA:$JAVA" "aapt2:$AAPT2" "d8.jar:$D8JAR" \
            "apksigner.jar:$APKSIGNER" "android.jar:$ANDROIDJAR" "ecj:$ECJ"; do
  name="${pair%%:*}"; path="${pair#*:}"
  [ -z "$path" ] && missing="$missing $name"
  [ -n "$path" ] && [ ! -f "$path" ] && missing="$missing $name(路径无效)"
done
if [ -n "$missing" ]; then
  echo "!! 找不到这些工具：$missing"
  echo "   三种办法任选其一："
  echo "     1) 设环境变量 ANDROID_SDK 指向你的 Android SDK"
  echo "     2) 把工具文件扁平放进 $TOOLCHAIN/"
  echo "     3) 单独指定，例如 ECJ=/path/to/ecj.jar ./build.sh"
  exit 1
fi

# python 只用来把 classes.dex 塞进 apk
PY="$(command -v python || command -v python3 || true)"
if [ -z "$PY" ]; then
  echo "!! 找不到 python，装一个或者把它加到 PATH"
  exit 1
fi

BUILD_ROOT="${BUILD_ROOT:-/tmp/_tcl_keymapper_build}"
PROJ="$BUILD_ROOT/src"
OUT="$BUILD_ROOT/out"
rm -rf "$BUILD_ROOT"
mkdir -p "$PROJ" "$OUT/classes" "$OUT/dex" "$OUT/gen"
cp -r "$SRC/java" "$SRC/res" "$PROJ/"
cp "$SRC/AndroidManifest.xml" "$SRC/debug.keystore" "$PROJ/"

echo "[1/5] aapt2 编译资源"
"$AAPT2" compile --dir "$PROJ/res" -o "$OUT/res.zip"

echo "[2/5] aapt2 链接 + 生成 R.java"
"$AAPT2" link \
  -o "$OUT/base.apk" \
  -I "$ANDROIDJAR" \
  --manifest "$PROJ/AndroidManifest.xml" \
  "$OUT/res.zip" \
  --java "$OUT/gen" \
  --auto-add-overlay

if [ ! -f "$OUT/gen/com/tclhotkey/redirect/R.java" ]; then
  echo "!! R.java 没生成"
  exit 1
fi

echo "[3/5] ECJ 编译 Java(含 R.java) -> class"
"$JAVA" -jar "$ECJ" \
  -nowarn -source 1.8 -target 1.8 -encoding UTF-8 \
  -bootclasspath "$ANDROIDJAR" \
  -classpath "$ANDROIDJAR" \
  -d "$OUT/classes" \
  $(find "$PROJ/java" -name '*.java') \
  "$OUT/gen/com/tclhotkey/redirect/R.java"

echo "[4/5] d8 转 class -> dex"
"$JAVA" -cp "$D8JAR" com.android.tools.r8.D8 \
  --lib "$ANDROIDJAR" \
  --min-api 19 \
  --output "$OUT/dex" \
  $(find "$OUT/classes" -name '*.class')

echo "[5/5] 注入 dex + 签名"
cd "$OUT"
cp base.apk unsigned.apk
"$PY" -c "
import zipfile
z = zipfile.ZipFile('unsigned.apk', 'a')
z.write('dex/classes.dex', 'classes.dex')
z.close()
print('dex injected')
"
rm -f final.apk
"$JAVA" -jar "$APKSIGNER" sign \
  --ks "$PROJ/debug.keystore" \
  --ks-pass pass:android \
  --key-pass pass:android \
  --ks-key-alias hotkey \
  --out final.apk unsigned.apk

mkdir -p "$SRC/build"
cp -f "$OUT/final.apk" "$SRC/build/app.apk"
ls -lh "$SRC/build/app.apk"
echo "DONE -> $SRC/build/app.apk"
