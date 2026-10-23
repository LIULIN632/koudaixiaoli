#!/bin/bash
# 口袋小鲤 — 一键打包脚本
# 用法:
#   ./build.sh              递增版本号并打包
#   ./build.sh --no-bump    不递增版本号，直接打包当前版本
#
# ⚠️ 版本号住在 gradle.properties，**不是** app/build.gradle.kts。
# 原因见 gradle.properties 里的注释：写进构建脚本会让"发一次版"触发全量重编
# （实测：改 kts 约 72 秒 vs 改 properties 约 53 秒，空转只要 2 秒）。
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROPS_FILE="$SCRIPT_DIR/gradle.properties"
KTS_FILE="$SCRIPT_DIR/app/build.gradle.kts"

NO_BUMP=0
for arg in "$@"; do
    case "$arg" in
        --no-bump|-NoBump|--NoBump) NO_BUMP=1 ;;
        *) echo "未知参数: $arg"; echo "用法: ./build.sh [--no-bump]"; exit 1 ;;
    esac
done

echo "========================================"
echo "  口袋小鲤 — 一键打包"
echo "========================================"

if [ ! -f "$PROPS_FILE" ]; then
    echo "错误：找不到 $PROPS_FILE"
    exit 1
fi

# 迁移护栏：版本号若又出现在 build.gradle.kts 里，这里读的 properties 会变成无效值
# （APK 里仍是 kts 那个号），是最难查的一类不一致。打包前直接拦住。
if [ -f "$KTS_FILE" ] && grep -qE '^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*[0-9]' "$KTS_FILE"; then
    echo "错误：版本号又写回 app/build.gradle.kts 了。"
    echo "      请改回 gradle.properties 的 versionCode=数字（打包脚本只递增那份）。"
    exit 1
fi

# 1. 读取当前版本（只认行首 key=value，避免匹配到注释里的同名文本）
VC=$(grep -oP '^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*\K\d+' "$PROPS_FILE" | head -1)
VN=$(grep -oP '^[[:space:]]*versionName[[:space:]]*=[[:space:]]*\K[0-9][0-9.]*' "$PROPS_FILE" | head -1)

if [ -z "$VC" ] || [ -z "$VN" ]; then
    echo "错误：无法读取版本号，请检查 $PROPS_FILE（格式：versionCode=102）"
    exit 1
fi

echo "当前版本: v$VN ($VC)"

# 2. 版本号递增（三段式 X.Y.Z：Patch +1，每段上限 99，如 1.99.99）
if [ "$NO_BUMP" -eq 0 ]; then
    NEW_VC=$((VC + 1))
    MAJOR=$(echo "$VN" | cut -d. -f1)
    MINOR=$(echo "$VN" | cut -d. -f2)
    PATCH=$(echo "$VN" | cut -d. -f3)
    PATCH=${PATCH:-0}

    NEW_PATCH=$((PATCH + 1))
    NEW_MINOR=$MINOR
    NEW_MAJOR=$MAJOR
    if [ "$NEW_PATCH" -gt 99 ]; then
        NEW_PATCH=0
        NEW_MINOR=$((MINOR + 1))
    fi
    if [ "$NEW_MINOR" -gt 99 ]; then
        NEW_MINOR=0
        NEW_MAJOR=$((MAJOR + 1))
    fi
    NEW_VN="$NEW_MAJOR.$NEW_MINOR.$NEW_PATCH"
    echo "新版本:   v$NEW_VN ($NEW_VC)"
else
    NEW_VC=$VC
    NEW_VN=$VN
    echo "已指定 --no-bump，沿用当前版本号"
fi

# 3. 写入新版本号（锚定行首，避免改到注释）
if [ "$NO_BUMP" -eq 0 ]; then
    sed -i.bak -E "s/^([[:space:]]*versionCode[[:space:]]*=[[:space:]]*)$VC[[:space:]]*\$/\1$NEW_VC/" "$PROPS_FILE"
    sed -i.bak -E "s/^([[:space:]]*versionName[[:space:]]*=[[:space:]]*)$VN[[:space:]]*\$/\1$NEW_VN/" "$PROPS_FILE"
    rm -f "${PROPS_FILE}.bak"

    # 复核：写不进去就立刻停，别产出一个版本号不对的包
    VC_NOW=$(grep -oP '^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*\K\d+' "$PROPS_FILE" | head -1)
    VN_NOW=$(grep -oP '^[[:space:]]*versionName[[:space:]]*=[[:space:]]*\K[0-9][0-9.]*' "$PROPS_FILE" | head -1)
    if [ "$VC_NOW" != "$NEW_VC" ] || [ "$VN_NOW" != "$NEW_VN" ]; then
        echo "错误：版本号写回失败（properties 里现在是 $VN_NOW ($VC_NOW)）"
        exit 1
    fi
    echo "✓ 版本号已更新"
fi

# 4. 构建 Release（renameApk 统一负责根目录输出与旧包归档）
echo ""
echo "正在构建 Release APK..."
"$SCRIPT_DIR/gradlew" renameApk -p "$SCRIPT_DIR" --console=plain
echo "✓ 构建完成"

# 5. 检查并显示结果
APK_DEST="$SCRIPT_DIR/口袋小鲤_v$NEW_VN.apk"
if [ ! -f "$APK_DEST" ]; then
    echo "错误：renameApk 未生成 $APK_DEST"
    exit 1
fi
SIZE=$(du -h "$APK_DEST" | cut -f1)

echo ""
echo "========================================"
echo "  打包完成！"
echo "  文件: 口袋小鲤_v$NEW_VN.apk"
echo "  版本: v$NEW_VN ($NEW_VC)"
echo "  大小: $SIZE"
echo "  路径: $APK_DEST"
echo "========================================"
