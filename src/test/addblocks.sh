#!/usr/bin/env bash
set -euo pipefail

OUTFILE="all_files.txt"

# === Список файлов (ТОЛЬКО имена без расширений) ===
# пример: Main значит Main.java, Utils значит Utils.java
NAMES=(
  "IT_04_UserParams_NoAuth"
  "AddBlockSender"
  "ChainState"
  "JsonBuilders"
)

# очищаем или создаём файл
: > "$OUTFILE"

# Быстрый фильтр: сделаем хеш-таблицу из имён (ассоц. массив)
declare -A WANT=()
for name in "${NAMES[@]}"; do
  WANT["$name"]=1
done

# собрать только нужные *.java по базовому имени
find . -type f -name "*.java" | sort | while read -r f; do
  base="$(basename "$f" .java)"
  if [[ -n "${WANT[$base]+x}" ]]; then
    cat "$f" >> "$OUTFILE"
    echo >> "$OUTFILE"  # пустая строка-разделитель
  fi
done

# скопировать весь файл в буфер обмена (Wayland)
wl-copy < "$OUTFILE"

echo "Готово!"
echo "Выбрано имён: ${#NAMES[@]}"
echo "Все нужные .java файлы собраны в $OUTFILE"
echo "Содержимое скопировано в буфер обмена (Wayland)"
