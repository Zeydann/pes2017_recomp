#!/bin/sh
# Fetches the apk the touch-stick work was measured against, and refuses
# anything that is not byte-for-byte the one that was measured.
#
#   ./fetch.sh <url-or-path>     install it as reference_apk/game.apk
#   ./fetch.sh --verify <path>   check a file without installing it
#
# A path is copied, a url is downloaded with curl. The result is checked
# against reference-apk.json before it is kept: a mismatch leaves the previous
# file untouched and exits non-zero, so a replaced or truncated download can
# never quietly become the benchmark.
#
# The apk itself is not in this repository (see README.md), and *.apk is
# ignored, so whatever lands here stays out of the next commit.
set -eu

here=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
manifest="$here/reference-apk.json"
dest="$here/game.apk"

if [ ! -f "$manifest" ]; then
    echo "error: $manifest is missing, so there is nothing to verify against" >&2
    exit 1
fi

want_sha=$(sed -n 's/.*"sha256": *"\([0-9a-fA-F]\{64\}\)".*/\1/p' "$manifest")
want_bytes=$(sed -n 's/.*"bytes": *\([0-9][0-9]*\).*/\1/p' "$manifest")
[ -n "$want_sha" ] && [ -n "$want_bytes" ] || {
    echo "error: could not read sha256/bytes out of $manifest" >&2
    exit 1
}

if command -v sha256sum >/dev/null 2>&1; then
    hash() { sha256sum "$1" | cut -d' ' -f1; }
elif command -v shasum >/dev/null 2>&1; then
    hash() { shasum -a 256 "$1" | cut -d' ' -f1; }
else
    echo "error: neither sha256sum nor shasum is installed" >&2
    exit 1
fi

# True when $1 is the benchmark. Says why when it is not.
verify() {
    file=$1
    [ -f "$file" ] || { echo "error: no such file: $file" >&2; return 1; }
    got_bytes=$(wc -c < "$file" | tr -d ' ')
    if [ "$got_bytes" != "$want_bytes" ]; then
        echo "error: $file is $got_bytes bytes, the benchmark is $want_bytes" >&2
        return 1
    fi
    got_sha=$(hash "$file")
    if [ "$got_sha" != "$want_sha" ]; then
        echo "error: sha256 mismatch" >&2
        echo "  want $want_sha" >&2
        echo "  got  $got_sha" >&2
        return 1
    fi
    echo "ok: $file is the benchmark ($got_bytes bytes, sha256 ${got_sha%"${got_sha#????????}"}...)"
}

case ${1:-} in
    "")
        echo "usage: $0 <url-or-path>" >&2
        echo "       $0 --verify <path>" >&2
        exit 2
        ;;
    --verify)
        [ $# -eq 2 ] || { echo "usage: $0 --verify <path>" >&2; exit 2; }
        verify "$2"
        exit $?
        ;;
esac

src=$1
tmp="$dest.part"
rm -f "$tmp"

if [ -f "$src" ]; then
    echo "copying $src"
    cp "$src" "$tmp"
else
    echo "downloading $src"
    curl -fL --retry 3 --progress-bar -o "$tmp" "$src" || {
        rm -f "$tmp"
        echo "error: download failed" >&2
        exit 1
    }
fi

if verify "$tmp"; then
    mv "$tmp" "$dest"
    echo "installed: $dest"
else
    rm -f "$tmp"
    echo "kept the previous $dest untouched" >&2
    exit 1
fi
