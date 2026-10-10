# syntax=docker/dockerfile:1
# Build from the repository root:
# docker build -f .oss-scanner/Dockerfile -t inqwise-indexer-oss-scanner .
FROM maven:3.9.9-eclipse-temurin-21

RUN apt-get update \
	&& apt-get install -y --no-install-recommends ca-certificates curl git python3 ripgrep \
	&& rm -rf /var/lib/apt/lists/*

# OSS Scanner expects the checkout at /src. Keep sources, test classes, build
# outputs, Maven's local repository, the downloaded Node/npm, and npm's cache.
COPY . /src
WORKDIR /src
ENV PATH="/src/indexer-web/target/node:${PATH}" \
	MAVEN_OPTS="-Dmaven.repo.local=/opt/maven-repository"

# The normal reactor build installs Node/npm, builds the console, executes Java
# tests, packages every module, and caches dependencies and test-provider plugins.
# Keep test failures in target/surefire-reports without blocking audit setup,
# as recommended by the scanner template. Compilation/setup errors remain fatal.
RUN mvn --batch-mode --no-transfer-progress install -Dmaven.test.failure.ignore=true \
	&& npm --prefix indexer-web/src/main/frontend test

# Maven offline mode does not cover npm. Force both tools offline and prove the
# cached environment works with all networking removed (loopback is available).
ENV NPM_CONFIG_OFFLINE=true
RUN --network=none mvn --batch-mode --no-transfer-progress --offline verify \
	-Dskip.installnodenpm=true -Dmaven.test.failure.ignore=true \
	&& npm --prefix indexer-web/src/main/frontend test

# This is an audit workspace, not the production node image.
ENTRYPOINT []
CMD ["/bin/bash"]
