#!/bin/sh
# Backwards-compatible Unix entry point. The real cross-platform launcher is
# run.cmd: it is a batch file for Windows and a POSIX shell script elsewhere.
set -eu
exec sh "$(dirname "$0")/run.cmd" "$@"
