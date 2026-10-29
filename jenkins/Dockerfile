# Custom Jenkins image for the FinSecure pipeline.
# Adds the tools the Jenkinsfile calls: Maven, Docker CLI, kubectl, Trivy.
#
# Build:  docker build -t finsecure-jenkins jenkins/
FROM jenkins/jenkins:lts-jdk21

USER root

# Run Maven + basics
RUN apt-get update \
 && apt-get install -y --no-install-recommends maven ca-certificates curl gnupg git \
 && rm -rf /var/lib/apt/lists/*

# Docker CLI + buildx (talks to Docker Desktop through the mounted socket with no daemon inside)
RUN install -m 0755 -d /etc/apt/keyrings \
 && curl -fsSL https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc \
 && echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/debian $(. /etc/os-release && echo $VERSION_CODENAME) stable" \
      > /etc/apt/sources.list.d/docker.list \
 && apt-get update \
 && apt-get install -y --no-install-recommends docker-ce-cli docker-buildx-plugin \
 && rm -rf /var/lib/apt/lists/*

# run kubectl (to deploy to AKS)
RUN curl -fsSL -o /usr/local/bin/kubectl \
      "https://dl.k8s.io/release/$(curl -fsSL https://dl.k8s.io/release/stable.txt)/bin/linux/$(dpkg --print-architecture)/kubectl" \
 && chmod +x /usr/local/bin/kubectl

# Trivy (dependency + image vulnerability scanner) from Aqua's signed apt repo
RUN curl -fsSL https://aquasecurity.github.io/trivy-repo/deb/public.key | gpg --dearmor -o /usr/share/keyrings/trivy.gpg \
 && echo "deb [signed-by=/usr/share/keyrings/trivy.gpg] https://aquasecurity.github.io/trivy-repo/deb generic main" \
      > /etc/apt/sources.list.d/trivy.list \
 && apt-get update \
 && apt-get install -y --no-install-recommends trivy \
 && rm -rf /var/lib/apt/lists/*

# NOTE: Jenkins runs as root in this LOCAL LAB setup so it can use the Docker Desktop socket.
# In production you would use dedicated build agents instead of giving the controller Docker access.
