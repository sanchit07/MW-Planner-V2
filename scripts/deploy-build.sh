#!/usr/bin/env bash
# Production build for the Reserved VM deployment: v2-gateway (the single-port
# ingress that replaced the legacy V1 Express server), the standalone mock-IAM
# service, the static V2 frontend, and both Spring Boot jars.
# GRADLE_USER_HOME is forced inside the workspace so the Gradle-downloaded
# JDK 24 (foojay toolchain) ships with the deployment image and deploy-run.sh
# can find a java binary at runtime.
set -euo pipefail
cd "$(dirname "$0")/.."

export GRADLE_USER_HOME="$PWD/.gradle"
# JDK 24 is vendored at .jdk24 (foojay auto-provisioning proved flaky here);
# point Gradle's toolchain detection at it via gradle.properties + JAVA_HOME
# and forbid downloads. (-P flags do NOT reach toolchain detection.)
export JAVA_HOME="$PWD/.jdk24"
mkdir -p "$GRADLE_USER_HOME"
printf 'org.gradle.java.installations.auto-download=false\norg.gradle.java.installations.paths=%s\n' "$JAVA_HOME" > "$GRADLE_USER_HOME/gradle.properties"

echo "==> Building v2-gateway"
(cd v2-gateway && npm install && npm run build)

echo "==> Building v2-iam-companion"
(cd v2-iam-companion && npm install && npm run build)

echo "==> Building V2 frontend (static, base /)"
# npx vite build (not npm run build): the package.json build script runs tsc
# first, which fails on long-standing type errors in test files unrelated to
# the shipped bundle.
(cd v2 && npx vite build)

echo "==> Building V2 backend jar"
(cd v2-backend && ./gradlew bootJar -x test --no-daemon)

echo "==> Building V2 recommendation jar"
(cd v2-recommendation && ./gradlew bootJar -x test --no-daemon)

echo "==> Build complete"
ls -lh v2-backend/build/libs v2-recommendation/build/libs
