#!/usr/bin/env bash
set -euo pipefail

OUTFILE="all_files.txt"
SKIPFILE="skip.txt"

# очищаем или создаём файл
: > "$OUTFILE"

# читаем список исключённых имён (без расширения) в массив
if [[ -f "$SKIPFILE" ]]; then
  mapfile -t SKIP_LIST < "$SKIPFILE"
else
  SKIP_LIST=()
fi

find . -type f -name "*.java" | sort | while read -r f; do
  fname=$(basename "$f" .java)   # имя файла без расширения

  # проверяем, есть ли имя в списке исключений
  skip=false
  for skipf in "${SKIP_LIST[@]}"; do
    if [[ "$fname" == "$skipf" ]]; then
      skip=true
      break
    fi
  done

  if [[ "$skip" == true ]]; then
    echo "Пропускаем $f"
    continue
  fi

  cat "$f" >> "$OUTFILE"
  echo >> "$OUTFILE"    # пустая строка-разделитель
done

echo "Готово! Все .java файлы собраны в $OUTFILE (кроме исключённых из $SKIPFILE)"
