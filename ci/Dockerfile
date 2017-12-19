FROM openjdk:8-slim

RUN apt-get update && \
    apt-get install -y rsync python-pip git && \
    pip install docker-compose && \
    git config --global user.email "push-apps-ci@example.com" && \
    git config --global user.name "Push Apps CI"
