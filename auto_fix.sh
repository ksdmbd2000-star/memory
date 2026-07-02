#!/bin/bash
while true; do
  gradle :app:compileDebugKotlin > build.log 2>&1
  if grep -q "Syntax error: Missing '}'" build.log; then
    echo "}" >> app/src/main/java/com/example/MainActivity.kt
    echo "Added }"
  else
    cat build.log
    break
  fi
done
