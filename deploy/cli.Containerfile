# Hermetic uploader image: a JRE + the unitrack-cli fat jar, so the uploader runs on ANY CI
# (JS/Python/.NET/Go/JVM) with neither Java nor bash on the host. Also the base image the
# Docker-based GitHub Action wraps.
#
# Build context is the CLI module's target dir (the jar must be built first):
#   ./mvnw -pl unitrack-cli -am package -DskipTests
#   podman build -t ghcr.io/<owner>/unitrack-uploader:<tag> -f deploy/cli.Containerfile unitrack-cli/target
#
# Run (mount the workspace; pass the token via env, never baked into a layer):
#   docker run --rm -v "$PWD:/work" -e UNITRACK_URL=… -e UNITRACK_TOKEN=… \
#     ghcr.io/<owner>/unitrack-uploader upload --project app --junit 'target/surefire-reports/*.xml'
#
# TODO: pin the base by digest in CI (docker/metadata-action) for reproducibility.
FROM docker.io/library/eclipse-temurin:21-jre

# The mounted workspace is the working dir, so report globs resolve relative to it.
WORKDIR /work
# The runnable jar is attached under the 'exec' classifier (the plain main jar is a library).
COPY unitrack-cli-exec.jar /app/unitrack-cli.jar

RUN useradd --uid 1001 --system --create-home --shell /usr/sbin/nologin app
USER 1001

ENTRYPOINT ["java", "-jar", "/app/unitrack-cli.jar"]
