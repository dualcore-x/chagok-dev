#!/bin/sh
# Create the release signing key outside the repo. Run once, on the machine that builds release APKs.
# Nothing here is printed or committed. Back the folder up: losing the key means installed apps cannot be updated.
set -e
DIR="${DC_SIGNING_DIR:-$HOME/.drive-companion}"
if [ -e "$DIR/signing.properties" ]; then
  echo "Already exists: $DIR/signing.properties"
  exit 1
fi
for candidate in "$JAVA_HOME/bin/keytool" /opt/homebrew/opt/openjdk@21/bin/keytool \
  "/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/keytool"; do
  if [ -x "$candidate" ]; then KEYTOOL="$candidate"; break; fi
done
[ -n "$KEYTOOL" ] || { echo "keytool not found. Set JAVA_HOME."; exit 1; }

umask 077
mkdir -p "$DIR"
DC_PASS="$(openssl rand -hex 24)"
export DC_PASS
"$KEYTOOL" -genkeypair -keystore "$DIR/release.jks" -storetype PKCS12 -alias release \
  -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Drive Companion" \
  -storepass:env DC_PASS -keypass:env DC_PASS 2>/dev/null
cat > "$DIR/signing.properties" <<PROPS
storeFile=$DIR/release.jks
storePassword=$DC_PASS
keyAlias=release
keyPassword=$DC_PASS
PROPS
echo "Created $DIR. Back this folder up somewhere safe."
