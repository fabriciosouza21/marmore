#!/usr/bin/env bash
# Carrega as variaveis de ambiente do .env no shell atual.
# Uso:  source ./load-env.sh    (ou:  . ./load-env.sh)

ENV_FILE="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)/.env"

if [ ! -f "$ENV_FILE" ]; then
  echo "Arquivo .env nao encontrado: $ENV_FILE" >&2
  return 1 2>/dev/null || exit 1
fi

# set -a exporta automaticamente toda variavel definida a seguir
set -a
# shellcheck source=/dev/null
. "$ENV_FILE"
set +a

echo "Variaveis carregadas de: $ENV_FILE"
