#!/usr/bin/env bash
# OryxOS pre-commit 门禁：格式不合规不允许提交（与 CI 同一道关）。
# 修复方式：mvn spotless:apply
# 本脚本由 git-build-hook-maven-plugin 在任意 mvn 命令执行时自动安装到 .git/hooks/pre-commit。
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"
echo "[pre-commit] mvn spotless:check ..."
if ! mvn -q spotless:check; then
  echo "[pre-commit] 格式检查未通过，请先执行: mvn spotless:apply" >&2
  exit 1
fi
