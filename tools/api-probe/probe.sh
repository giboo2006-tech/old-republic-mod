#!/usr/bin/env bash
# Prints API signatures and vanilla resources from the exact Minecraft and Fabric API
# jars this project compiles against, so code can be checked against the real 26.2 API.
# Usage: probe.sh <query-file>
# Query lines:
#   net.minecraft.Foo            javap -protected of a class (nested: net.minecraft.Foo$Bar)
#   body:net.minecraft.Foo#name  bytecode of methods named `name`
#   find:<regex>                 class names matching regex (on path form, e.g. world/item/.*Item$)
#   pkg:net/minecraft/world/item list classes directly in a package
#   res:<path in jar>            print a resource file (e.g. data/minecraft/worldgen/structure/igloo.json)
#   resfind:<regex>              list resource paths matching regex
#   # comment
set -u
QUERY="$1"
JARS=$(find "$HOME/.gradle/caches/fabric-loom" "$HOME/.gradle/caches/modules-2/files-2.1/net.fabricmc.fabric-api" .gradle/loom-cache -name '*.jar' 2>/dev/null | grep -v -- '-sources' | sort -u)
MCJARS=""
for j in $JARS; do
  if unzip -l "$j" 2>/dev/null | grep -q 'net/minecraft/world/level/Level.class\|net/minecraft/client/Minecraft.class'; then MCJARS="$MCJARS $j"; fi
done
CP=$(echo $JARS | tr ' ' ':')
echo "== Minecraft jars:"; for j in $MCJARS; do echo "  $j"; done
LIST=$(mktemp); for j in $JARS; do unzip -Z1 "$j" 2>/dev/null | sed "s|^|$j!|"; done > "$LIST"
while IFS= read -r line || [ -n "$line" ]; do
  [ -z "$line" ] && continue
  case "$line" in
    \#*) continue;;
    find:*) echo "==== $line"; grep -E "!net/minecraft/.*${line#find:}" "$LIST" | grep '\.class$' | sed 's|.*!||' | grep -v '\$[0-9]' | head -200;;
    pkg:*) p="${line#pkg:}"; echo "==== $line"; grep -E "!$p/[^/]+\.class$" "$LIST" | sed 's|.*!||' | grep -v '\$[0-9]' | head -400;;
    resfind:*) echo "==== $line"; grep -E "${line#resfind:}" "$LIST" | grep -v '\.class$' | sed 's|.*!||' | head -200;;
    res:*) r="${line#res:}"; echo "==== $line"; src=$(grep -m1 -F "!$r" "$LIST" | sed 's|!.*||'); [ -n "$src" ] && unzip -p "$src" "$r" | head -c 20000; echo;;
    body:*) c="${line#body:}"; cls="${c%#*}"; m="${c#*#}"; echo "==== $line"; javap -cp "$CP" -c -p "$cls" 2>&1 | awk -v m="$m" '/^  [a-z].*\(/{p=($0 ~ (" " m "\\("))} p' | head -300;;
    *) echo "==== $line"; javap -cp "$CP" -protected "$line" 2>&1 | head -400;;
  esac
done < "$QUERY"
