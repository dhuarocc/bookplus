# Jenkins LTS (JDK 21) con el CLI de Docker, para poder construir imágenes desde el pipeline
# usando el socket de Docker del host (montado en docker-compose.jenkins.yml).
FROM jenkins/jenkins:lts-jdk21

USER root
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl gnupg \
    && install -m 0755 -d /etc/apt/keyrings \
    && curl -fsSL https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc \
    && chmod a+r /etc/apt/keyrings/docker.asc \
    && echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/debian $(. /etc/os-release && echo $VERSION_CODENAME) stable" > /etc/apt/sources.list.d/docker.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends docker-ce-cli \
    && rm -rf /var/lib/apt/lists/*
USER jenkins
ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false"

# Plugins necesarios preinstalados.
RUN jenkins-plugin-cli --plugins \
    workflow-aggregator \
    docker-workflow \
    git \
    credentials-binding \
    junit \
    pipeline-stage-view \
    ansicolor \
    ws-cleanup \
    sonar \
    configuration-as-code \
    job-dsl \
    hashicorp-vault-plugin \
    slack \
    email-ext \
    pipeline-utility-steps
