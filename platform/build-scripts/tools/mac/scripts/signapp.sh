#!/bin/bash

#immediately exit script with an error if a command fails
set -euo pipefail

export COPY_EXTENDED_ATTRIBUTES_DISABLE=true
export COPYFILE_DISABLE=true

INPUT_FILE=$1
EXPLODED=$2.exploded
USERNAME=$3
PASSWORD=$4
CODESIGN_STRING=$5
HELP_DIR_NAME=$6
NOTARIZE=$7
BUNDLE_ID=$8

cd "$(dirname "$0")"

function log() {
  echo "$(date '+[%H:%M:%S]') $*"
}

log "Deleting $EXPLODED ..."
if test -d "$EXPLODED"; then
  find "$EXPLODED" -mindepth 1 -maxdepth 1 -exec chmod -R u+wx '{}' \;
fi
rm -rf "$EXPLODED"
mkdir "$EXPLODED"

log "Unzipping $INPUT_FILE to $EXPLODED ..."
unzip -q "$INPUT_FILE" -d "$EXPLODED"
rm "$INPUT_FILE"
BUILD_NAME="$(ls "$EXPLODED")"
log "$INPUT_FILE unzipped and removed"

APPLICATION_PATH="$EXPLODED/$BUILD_NAME"

if [ $# -eq 9 ] && [ -f "$9" ]; then
  archiveJDK="$9"
  log "Preparing jdk $archiveJDK..."
  log "Modifying Info.plist"
  sed -i -e 's/1.6\*/1.6\+/' "$APPLICATION_PATH/Contents/Info.plist"
  jdk="jdk-bundled"
  if [[ $1 == *custom-jdk-bundled* ]]; then
    jdk="custom-$jdk"
  fi
  rm -f "$APPLICATION_PATH/Contents/Info.plist-e"
  log "Info.plist has been modified"
  log "Copying JDK: $archiveJDK to $APPLICATION_PATH/Contents"
  tar xvf "$archiveJDK" -C "$APPLICATION_PATH/Contents" --exclude='._jdk'
  find "$APPLICATION_PATH/Contents/" -mindepth 1 -maxdepth 1 -exec chmod -R u+w '{}' \;
  log "JDK has been copied"
  rm -f "$archiveJDK"
fi

if [ "$HELP_DIR_NAME" != "no-help" ]; then
  HELP_DIR="$APPLICATION_PATH/Contents/Resources/$HELP_DIR_NAME/Contents/Resources/English.lproj/"
  log "Building help indices for $HELP_DIR"
  hiutil -Cagvf "$HELP_DIR/search.helpindex" "$HELP_DIR"
fi

find "$APPLICATION_PATH/Contents/bin" \
  -maxdepth 1 -type f -name '*.jnilib' -print0 |
  while IFS= read -r -d $'\0' file; do
    if [ -f "$file" ]; then
      log "Linking $file"
      b="$(basename "$file" .jnilib)"
      ln -sf "$b.jnilib" "$(dirname "$file")/$b.dylib"
    fi
  done

find "$APPLICATION_PATH/Contents/" \
  -maxdepth 1 -type f -name '*.txt' -print0 |
  while IFS= read -r -d $'\0' file; do
    if [ -f "$file" ]; then
      log "Moving $file"
      mv "$file" "$APPLICATION_PATH/Contents/Resources"
    fi
  done

non_plist=$(find "$APPLICATION_PATH/Contents/" -maxdepth 1 -type f -and -not -name 'Info.plist' | wc -l)
if [[ $non_plist -gt 0 ]]; then
  log "Only Info.plist file is allowed in Contents directory but found $non_plist file(s):"
  log "$(find "$APPLICATION_PATH/Contents/" -maxdepth 1 -type f -and -not -name 'Info.plist')"
  exit 1
fi

log "Unlocking keychain..."
# Make sure *.p12 is imported into local KeyChain
security unlock-keychain -p "$PASSWORD" "/Users/$USERNAME/Library/Keychains/login.keychain"

attempt=1
limit=3
set +e
while [[ $attempt -le $limit ]]; do
  log "Signing (attempt $attempt) $APPLICATION_PATH ..."
  ./sign.sh "$APPLICATION_PATH" "$CODESIGN_STRING"
  ec=$?
  if [[ $ec -ne 0 ]]; then
    ((attempt += 1))
    if [ $attempt -eq $limit ]; then
      set -e
    fi
    log "Signing failed, wait for 30 sec and try to sign again"
    sleep 30
  else
    log "Signing done"
    codesign -v "$APPLICATION_PATH" -vvvvv
    log "Check sign done"
    ((attempt += limit))
  fi
done

set -e

if [ "$NOTARIZE" = "yes" ]; then
  log "Notarizing..."
  # shellcheck disable=SC1090
  source "$HOME/.notarize_token"
  APP_NAME="${INPUT_FILE%.*}"
  ./notarize.sh "$APPLICATION_PATH" "$APPLE_USERNAME" "$APPLE_PASSWORD" "$APP_NAME" "$BUNDLE_ID"

  log "Stapling..."
  xcrun stapler staple "$APPLICATION_PATH"
else
  log "Notarization disabled"
  log "Stapling disabled"
fi

log "Zipping $BUILD_NAME to $INPUT_FILE ..."
(
  cd "$EXPLODED"
  ditto -c -k --sequesterRsrc --keepParent "$BUILD_NAME" "../$INPUT_FILE"
  log "Finished zipping"
)
rm -rf "$EXPLODED"
log "Done"
