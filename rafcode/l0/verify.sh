#!/bin/sh
# SPDX-FileCopyrightText: 2026 Rafael Melo Reis
# SPDX-License-Identifier: Apache-2.0
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
SRC="$ROOT/rafcode/l0/kernel.c"
OBJ="${TMPDIR:-/tmp}/rafcode-gradle-l0.$$.o"
CC_BIN=${CC:-cc}

trap 'rm -f "$OBJ"' EXIT HUP INT TERM

command -v "$CC_BIN" >/dev/null 2>&1 || CC_BIN=clang
command -v "$CC_BIN" >/dev/null 2>&1
command -v nm >/dev/null 2>&1

if sed '/^[[:space:]]*\/\*/,/^[[:space:]]*\*\//d' "$SRC" | grep -nE '^[[:space:]]*#include|(^|[^A-Za-z0-9_])(for|while|do|switch|goto|malloc|calloc|realloc|free|syscall)([^A-Za-z0-9_]|$)'; then
    printf '%s\n' 'FAIL: hosted dependency or unnecessary control-flow token in L0 source'
    exit 1
fi

"$CC_BIN" -std=c11 -Os -Wall -Wextra -Werror -ffreestanding -fno-builtin -fno-stack-protector -fno-asynchronous-unwind-tables -fno-unwind-tables -fno-ident -c "$SRC" -o "$OBJ"

test -z "$(nm -u "$OBJ")"
test "$(nm -g --defined-only "$OBJ" | grep -c ' raf_gradle_l0_gate$')" -eq 1

printf '%s\n' 'RAFCODE Gradle L0: PASS'
