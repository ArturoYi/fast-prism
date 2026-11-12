#!/usr/bin/env bash
# 检查当前仓库是否符合 Accompanist 风格约定。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

failures=0

ok() { printf 'OK   %s\n' "$1"; }
bad() { printf 'FAIL %s\n' "$1" >&2; failures=$((failures + 1)); }

require_file() {
  if [[ -f "$1" ]]; then
    ok "存在 $1"
  else
    bad "缺少 $1"
  fi
}

require_file settings.gradle.kts
require_file build.gradle.kts
require_file gradle.properties
require_file gradle/libs.versions.toml
require_file gradle/wrapper/gradle-wrapper.properties
require_file build-logic/settings.gradle.kts
require_file build-logic/convention/build.gradle.kts
require_file build-logic/convention/src/main/kotlin/sdk/buildlogic/MavenPublish.kt
require_file build-logic/convention/src/main/kotlin/sdk/buildlogic/AndroidLibraryConventionPlugin.kt
require_file build-logic/convention/src/main/kotlin/sdk/buildlogic/AndroidLibraryPublishedConventionPlugin.kt
require_file build-logic/convention/src/main/kotlin/sdk/buildlogic/AndroidApplicationConventionPlugin.kt
require_file build-logic/convention/src/main/kotlin/sdk/buildlogic/BomConventionPlugin.kt
require_file LICENSE
require_file README.md
require_file CHANGELOG.md
require_file docs/package.json
require_file docs/content/2.guide/2.publishing.md
require_file .github/workflows/docs.yml
require_file .github/workflows/pr.yml
require_file .github/workflows/publish.yml

if grep -q 'includeBuild("build-logic")' settings.gradle.kts; then
  ok "settings 使用 included build"
else
  bad "settings.gradle.kts 没有 includeBuild(\"build-logic\")"
fi

groovy_files="$(find . -type f -name '*.gradle' -not -path '*/.gradle/*' -not -path './.git/*' || true)"
if [[ -z "$groovy_files" ]]; then
  ok "没有 Groovy 构建脚本"
else
  bad "发现 Groovy 构建脚本: $groovy_files"
fi

if grep -Eq 'subprojects[[:space:]]*\{' build.gradle.kts; then
  bad "根 build.gradle.kts 使用了 subprojects 代码块"
else
  ok "根工程没有 subprojects 注入"
fi

if grep -R --include='*.kts' --include='*.kt' --include='*.toml' --include='*.properties' -n 'com.jfrog.bintray' . \
  | grep -v '/build/' | grep -v '已关停' | grep -v '禁止' | grep -v 'withPlugin'; then
  bad "发现 com.jfrog.bintray 配置"
else
  ok "没有 bintray 发布配置"
fi

if grep -Eq '^[^#[:space:]].*(mavenCentralPassword|signingInMemoryKey|signing\.password)[[:space:]]*=' gradle.properties; then
  bad "gradle.properties 写入了发布密钥"
else
  ok "仓库内没有发布密钥"
fi

if grep -q 'vanniktechMavenPublish = "0.28.0"' gradle/libs.versions.toml \
  && grep -q 'agp = "8.7.0"' gradle/libs.versions.toml \
  && grep -q 'kotlin = "2.0.20"' gradle/libs.versions.toml \
  && grep -q 'gradle-8.10' gradle/wrapper/gradle-wrapper.properties; then
  ok "基线版本为 AGP 8.7.0 / Kotlin 2.0.20 / Gradle 8.10 / vanniktech 0.28.0"
else
  bad "基线版本与约定不一致"
fi

if grep -Eq '^VERSION_NAME=|^SONATYPE_HOST=|^RELEASE_SIGNING_ENABLED=' gradle.properties; then
  bad "gradle.properties 使用了会绕过 convention 的 VERSION_NAME / SONATYPE_HOST / RELEASE_SIGNING_ENABLED"
else
  ok "版本和 Sonatype 主机由 convention 决定"
fi

if grep -q 'rootProject.name = "fast-prism"' settings.gradle.kts; then
  ok "根工程名是仓库名 fast-prism"
else
  bad "根工程名应该是 fast-prism"
fi

if grep -q 'include(":fast-dowload")' settings.gradle.kts \
  && grep -q 'plugins {' fast-dowload/build.gradle.kts \
  && grep -q 'base' fast-dowload/build.gradle.kts \
  && ! grep -Eq 'com.android|maven.publish|sdk.jvm.library' fast-dowload/build.gradle.kts; then
  ok ":fast-dowload 只聚合子模块，不发布"
else
  bad ":fast-dowload 不是 SDK 入口，或误挂了 Android / 发布插件"
fi

for module in fast-dowload:core fast-dowload:engine fast-dowload:testing fast-dowload:sample; do
  if grep -q "include(\":$module\")" settings.gradle.kts; then
    ok "启用 :$module"
  else
    bad "settings 未启用 :$module"
  fi
done

if grep -Eq 'include\(":(fast-dowload-core|fast-dowload-engine|fast-dowload-testing|sample)"\)' settings.gradle.kts; then
  bad "SDK 子模块被平铺在仓库根上"
else
  ok "FastDowload 子模块都挂在 :fast-dowload 下面"
fi

if grep -q 'include(":example")' settings.gradle.kts; then
  bad "仍启用已删除的 :example"
else
  ok "没有 :example"
fi

jvm_plugin="build-logic/convention/src/main/kotlin/sdk/buildlogic/JvmLibraryConventionPlugin.kt"
if grep -q 'pluginManager.apply("org.jetbrains.kotlin.jvm")' "$jvm_plugin" \
  && grep -q 'pluginManager.apply("java-library")' "$jvm_plugin" \
  && ! grep -q 'pluginManager.apply("com.android' "$jvm_plugin"; then
  ok "JVM convention 使用 kotlin jvm"
else
  bad "JVM convention 不正确"
fi

for module in fast-dowload/core fast-dowload/engine fast-dowload/testing; do
  if grep -q 'sdk.jvm.library' "$module/build.gradle.kts" \
    && ! grep -q 'com.android' "$module/build.gradle.kts"; then
    ok ":${module//\//:} 是纯 JVM"
  else
    bad ":${module//\//:} 不是纯 JVM"
  fi
done

if grep -q '@Disabled("M1 启用")' fast-dowload/engine/src/test/kotlin/com/arturoyi/fastdowload/engine/FlushOrderTest.kt; then
  ok "FlushOrderTest 已按 M1 预留"
else
  bad "缺少 M1 FlushOrderTest 占位"
fi

manifest="fast-dowload/sample/src/main/AndroidManifest.xml"
if grep -q 'android.permission.INTERNET' "$manifest" \
  && ! grep -Eq 'READ_EXTERNAL_STORAGE|WRITE_EXTERNAL_STORAGE|MANAGE_EXTERNAL_STORAGE|FOREGROUND_SERVICE|POST_NOTIFICATIONS' "$manifest"; then
  ok "sample 只有网络权限"
else
  bad "sample 权限不符合内部目录下载"
fi

if grep -R --include='*.kt' -nE '^import (android|androidx|com\.android)\.' fast-dowload/core fast-dowload/engine; then
  bad "core/engine 出现 Android import"
else
  ok "core/engine 源码没有 Android import"
fi

if grep -q 'sdk.android.application' fast-dowload/sample/build.gradle.kts \
  && ! grep -Eq 'sdk.android.library.published|com.vanniktech.maven.publish' fast-dowload/sample/build.gradle.kts; then
  ok ":fast-dowload:sample 是不发布的 application"
else
  bad ":fast-dowload:sample 不是纯 application，或误挂了发布插件"
fi

if grep -q 'publishToMavenCentral' build-logic/convention/src/main/kotlin/sdk/buildlogic/MavenPublish.kt \
  && grep -q 'https://oss.sonatype.org/content/repositories/snapshots/' build-logic/convention/src/main/kotlin/sdk/buildlogic/MavenPublish.kt \
  && grep -q 'https://oss.sonatype.org/content/repositories/releases/' build-logic/convention/src/main/kotlin/sdk/buildlogic/MavenPublish.kt \
  && grep -q 'signAllPublications' build-logic/convention/src/main/kotlin/sdk/buildlogic/MavenPublish.kt; then
  ok "convention 封装了 Sonatype、签名和仓库地址"
else
  bad "convention 发布实现不完整"
fi

if grep -q 'Apache License' LICENSE; then
  ok "LICENSE 为 Apache 2.0"
else
  bad "LICENSE 不是 Apache 2.0"
fi

if [[ "$failures" -gt 0 ]]; then
  echo "$failures 项不符合约定" >&2
  exit 1
fi

echo "工程符合当前 Accompanist 风格约定。"
