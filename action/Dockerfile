# Docker-based GitHub Action: wraps the published uploader image (#122) with an entry point
# that maps action inputs onto the unitrack-cli. Pinned to the major line (`:0`), which tracks
# the latest 0.x uploader — matching the moving `@v0` action tag consumers reference.
FROM ghcr.io/alexmond/unitrack-uploader:0

COPY entrypoint.sh /entrypoint.sh
USER root
RUN chmod 0755 /entrypoint.sh
USER 1001
ENTRYPOINT ["/entrypoint.sh"]
