FROM openjdk:8-slim

RUN apt-get update && \
    apt-get install -y \
    apt-transport-https \
    ca-certificates \
    curl \
    gnupg2 \
    software-properties-common
RUN curl -fsSL https://download.docker.com/linux/debian/gpg | apt-key add -
RUN add-apt-repository "deb [arch=amd64] https://download.docker.com/linux/debian stretch stable"
RUN apt-get update && \
    apt-get install -y rsync python-pip git docker-ce && \
    pip install docker-compose && \
    git config --global user.email "push-apps-ci@example.com" && \
    git config --global user.name "Push Apps CI"
