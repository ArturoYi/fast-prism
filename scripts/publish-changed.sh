#!/usr/bin/env bash
# 只发布变更过的可发布模块。application 模块永远跳过。
# jcenter/bintray 已关停，本脚本只调用 Gradle 的 Maven Central 任务。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

MODE=""
TAG=""
MODULE=""
SNAPSHOT="true"
DRY_RUN="false"

usage() {
  cat <<'EOF'
用法:
  scripts/publish-changed.sh --mode snapshot [--base SHA] [--head SHA] [--dry-run]
  scripts/publish-changed.sh --mode release --tag X.Y.Z [--base SHA] [--head SHA] [--dry-run]
  scripts/publish-changed.sh --mode manual --module :library [--snapshot true|false] [--dry-run]

snapshot：发布变更模块的 SNAPSHOT。
release：只发布版本号等于 tag 的变更模块，并 close & release Sonatype staging。
manual：只发布指定的一个模块。
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --mode) MODE="${2:-}"; shift 2 ;;
    --tag) TAG="${2:-}"; shift 2 ;;
    --module) MODULE="${2:-}"; shift 2 ;;
    --snapshot) SNAPSHOT="${2:-}"; shift 2 ;;
    --base) PUBLISH_BASE="${2:-}"; shift 2 ;;
    --head) PUBLISH_HEAD="${2:-}"; shift 2 ;;
    --dry-run) DRY_RUN="true"; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "未知参数: $1" >&2; usage; exit 2 ;;
  esac
done

if [[ -z "$MODE" ]]; then
  usage
  exit 2
fi

case "$MODE" in
  snapshot) SNAPSHOT="true" ;;
  release)
    SNAPSHOT="false"
    if [[ ! "$TAG" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
      echo "release 需要 --tag X.Y.Z，当前是: ${TAG:-<空>}" >&2
      exit 2
    fi
    ;;
  manual)
    if [[ ! "$MODULE" =~ ^:[A-Za-z0-9][A-Za-z0-9._:-]*$ ]]; then
      echo "manual 需要 --module :path，当前是: ${MODULE:-<空>}" >&2
      exit 2
    fi
    if [[ "$SNAPSHOT" != "true" && "$SNAPSHOT" != "false" ]]; then
      echo "--snapshot 只能是 true 或 false" >&2
      exit 2
    fi
    ;;
  *)
    echo "不支持的 mode: $MODE" >&2
    exit 2
    ;;
esac

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "当前目录不是 git 仓库，无法判断变更模块。" >&2
  exit 1
fi

read_prop() {
  local key="$1"
  local file="$2"
  local line
  line="$(grep -E "^${key}=" "$file" | head -n 1 || true)"
  printf '%s' "${line#*=}"
}

module_version() {
  local file="$1"
  local mode version
  mode="$(read_prop sdk.versionMode gradle.properties)"
  mode="${mode:-independent}"
  if [[ "$mode" == "unified" ]]; then
    read_prop sdk.version gradle.properties
    return
  fi
  version="$(sed -nE 's/^[[:space:]]*version[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/p' "$file" | head -n 1)"
  printf '%s' "$version"
}

module_group() {
  local file="$1"
  local group
  group="$(sed -nE 's/^[[:space:]]*group[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/p' "$file" | head -n 1)"
  if [[ -n "$group" ]]; then
    printf '%s' "$group"
    return
  fi
  read_prop sdk.group gradle.properties
}

is_publishable() {
  grep -Eq 'sdk\.android\.library\.published|sdk-android-library-published|sdk\.bom' "$1"
}

list_publishable() {
  local raw path dir file
  while IFS= read -r raw; do
    [[ -z "$raw" ]] && continue
    raw="${raw#:}"
    path=":${raw}"
    dir="${raw//:/\/}"
    file="$dir/build.gradle.kts"
    if [[ -f "$file" ]] && is_publishable "$file"; then
      printf '%s\t%s\n' "$path" "$dir"
    fi
  done < <(grep -oE 'include\("[^"]+"\)' settings.gradle.kts | sed -E 's/^include\("([^"]+)"\)$/\1/')
}

detect_base() {
  local head="$1"
  if [[ -n "${PUBLISH_BASE:-}" ]]; then
    printf '%s' "$PUBLISH_BASE"
    return
  fi
  if [[ "$MODE" == "manual" ]]; then
    printf '%s' "MANUAL"
    return
  fi
  if [[ "$MODE" == "release" ]]; then
    local prev
    prev="$(git describe --tags --abbrev=0 "${head}^" 2>/dev/null || true)"
    if [[ -n "$prev" ]]; then
      printf '%s' "$prev"
    else
      printf '%s' "ROOT"
    fi
    return
  fi
  local before="${GITHUB_EVENT_BEFORE:-}"
  if [[ -n "$before" && ! "$before" =~ ^0+$ ]] && git cat-file -e "${before}^{commit}" >/dev/null 2>&1; then
    printf '%s' "$before"
    return
  fi
  if git rev-parse --verify "${head}^" >/dev/null 2>&1; then
    git rev-parse "${head}^"
    return
  fi
  printf '%s' "ROOT"
}

HEAD_SHA="${PUBLISH_HEAD:-${GITHUB_SHA:-HEAD}}"
BASE_SHA="$(detect_base "$HEAD_SHA")"

if [[ "$BASE_SHA" == "ROOT" ]]; then
  CHANGED="$(git ls-files)"
elif [[ "$BASE_SHA" == "MANUAL" ]]; then
  CHANGED=""
else
  CHANGED="$(git diff --name-only "$BASE_SHA" "$HEAD_SHA")"
fi

GLOBAL="false"
if [[ "$BASE_SHA" != "MANUAL" ]]; then
  while IFS= read -r file; do
    [[ -z "$file" ]] && continue
    case "$file" in
      build-logic/*|gradle/libs.versions.toml|gradle.properties|settings.gradle.kts|build.gradle.kts|gradle/wrapper/*)
        GLOBAL="true"
        ;;
    esac
  done <<< "$CHANGED"
fi

SELECTED="$(mktemp)"
trap 'rm -f "$SELECTED"' EXIT

while IFS="$(printf '\t')" read -r path dir; do
  [[ -z "$path" ]] && continue
  file="$dir/build.gradle.kts"
  group="$(module_group "$file")"
  version="$(module_version "$file")"
  if [[ -z "$group" || -z "$version" ]]; then
    echo "模块 $path 缺少 group 或 version" >&2
    exit 1
  fi

  take="false"
  if [[ "$MODE" == "manual" ]]; then
    [[ "$path" == "$MODULE" ]] && take="true"
  elif [[ "$GLOBAL" == "true" ]]; then
    take="true"
  else
    while IFS= read -r changed; do
      [[ -z "$changed" ]] && continue
      case "$changed" in
        "$dir"/*) take="true" ;;
      esac
    done <<< "$CHANGED"
  fi

  if [[ "$take" != "true" ]]; then
    continue
  fi

  if [[ "$MODE" == "release" && "$version" != "$TAG" ]]; then
    echo "跳过 $path：声明版本 $version 不等于 tag $TAG" >&2
    continue
  fi

  printf '%s\t%s\t%s\n' "$group" "$path" "$version" >> "$SELECTED"
done < <(list_publishable)

if [[ "$MODE" == "manual" && ! -s "$SELECTED" ]]; then
  echo "模块 $MODULE 不存在，或不是可发布模块。" >&2
  exit 1
fi

if [[ ! -s "$SELECTED" ]]; then
  if [[ "$MODE" == "release" ]]; then
    echo "没有版本等于 $TAG 的变更模块，发布中止。" >&2
    exit 1
  fi
  echo "没有变更的可发布模块，跳过发布。"
  exit 0
fi

if [[ "$GLOBAL" == "true" ]]; then
  echo "共享构建文件有变更，本次发布所有受影响的可发布模块。" >&2
fi

echo "发布计划（mode=$MODE snapshot=$SNAPSHOT）：" >&2
while IFS="$(printf '\t')" read -r group path version; do
  echo "  $path  $group  $version" >&2
done < "$SELECTED"

run_gradle() {
  local snapshot="$1"
  shift
  local modules=("$@")
  if [[ ${#modules[@]} -eq 0 ]]; then
    return
  fi
  local args=()
  local module
  for module in "${modules[@]}"; do
    args+=("${module}:publish")
  done
  if [[ "$snapshot" == "true" ]]; then
    args+=("-Psdk.snapshot=true")
  else
    args+=("-Psdk.automaticRelease=true")
  fi
  args+=(--no-daemon --stacktrace)
  echo "+ ./gradlew ${args[*]}" >&2
  if [[ "$DRY_RUN" == "true" ]]; then
    return
  fi
  ./gradlew "${args[@]}"
}

if [[ "$SNAPSHOT" == "true" ]]; then
  modules=()
  while IFS="$(printf '\t')" read -r _ path _; do
    modules+=("$path")
  done < "$SELECTED"
  run_gradle true "${modules[@]}"
  exit 0
fi

groups="$(cut -f1 "$SELECTED" | sort -u)"
while IFS= read -r group; do
  [[ -z "$group" ]] && continue
  modules=()
  while IFS="$(printf '\t')" read -r row_group path _; do
    if [[ "$row_group" == "$group" ]]; then
      modules+=("$path")
    fi
  done < "$SELECTED"
  echo "按 groupId=$group 单独发布，避免不同 namespace 共用一个 staging 仓库。" >&2
  run_gradle false "${modules[@]}"
done <<< "$groups"
