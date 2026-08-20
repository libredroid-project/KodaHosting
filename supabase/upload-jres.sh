#!/usr/bin/env bash
# Laedt die AngelAuraMC android-arm64 JRE-Builds in den Supabase "artifacts"-Bucket.
# Die App nutzt diese URLs zuerst (Supabase-first) und faellt auf GitHub zurueck,
# solange die Dateien nicht hochgeladen sind.
#
# Vorbereitung (einmalig):
#   1. Bucket "artifacts" anlegen (Supabase Dashboard -> Storage -> New bucket, PUBLIC)
#   2. Service Role Key aus Dashboard -> Settings -> API kopieren
#
# Ausfuehren:
#   SERVICE_KEY=eyJ... ./supabase/upload-jres.sh

set -euo pipefail
PROJECT_URL="https://scsezpfrrmpyuapblbxk.supabase.co"
: "${SERVICE_KEY:?Bitte SERVICE_KEY (Supabase service_role) setzen}"

upload() {
  local version="$1" tag="$2" file="$3"
  echo ">> Lade $file (Java $version)..."
  curl -sL -o "/tmp/$file" "https://github.com/AngelAuraMC/angelauramc-openjdk-build/releases/download/$tag/$file"
  curl -s -X POST "$PROJECT_URL/storage/v1/object/artifacts/openjdk$version/$file" \
    -H "Authorization: Bearer $SERVICE_KEY" \
    -H "apikey: $SERVICE_KEY" \
    -H "Content-Type: application/octet-stream" \
    --data-binary "@/tmp/$file"
  echo ""
  rm -f "/tmp/$file"
}

upload 8  "download_jre8"  "jre8-android-arm64.tar.xz"
upload 17 "download_jre17" "jre17-android-arm64.tar.xz"
upload 21 "download_jre21" "jre21-android-arm64.tar.xz"

echo "Fertig. Die App erwartet:"
echo "  $PROJECT_URL/storage/v1/object/public/artifacts/openjdk{8,17,21}/openjdk{8,17,21}-android-arm64.tar.xz"
echo "(Dateinamen im Bucket auf openjdk<N>-android-arm64.tar.xz umbenennen, falls noetig)"
