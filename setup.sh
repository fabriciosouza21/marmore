#!/usr/bin/env bash
# Configura o repo local apos clone:
#   - ativa .githooks/ como hooksPath do git
# Uso: ./setup.sh

set -euo pipefail

cd "$(dirname "$0")"

if [ ! -d .git ] && [ ! -f .git ]; then
  echo "Erro: rode este script na raiz de um repositorio git." >&2
  exit 1
fi

git config core.hooksPath .githooks
echo "Hooks configurados em: .githooks/"
echo "Para bypass em emergencias: SKIP_PRE_COMMIT=1 git commit ..."
