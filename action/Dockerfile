# Docker-based GitHub Action: wraps the published uploader image (#122) with an entry point
# that maps action inputs onto the unitrack-cli. Pin to a released tag in production.
FROM ghcr.io/alexmond/unitrack-uploader:latest

COPY entrypoint.sh /entrypoint.sh
USER root
RUN chmod 0755 /entrypoint.sh
USER 1001
ENTRYPOINT ["/entrypoint.sh"]
