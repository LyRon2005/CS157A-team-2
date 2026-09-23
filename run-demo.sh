#!/bin/sh
set -eu
cd "$(dirname "$0")"
mkdir -p bin
javac --release 17 -encoding UTF-8 -d bin src/demo/Main.java
exec java -cp bin demo.Main "$@"
