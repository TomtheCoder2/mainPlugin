#!/usr/bin/env bash
screen -r v7

while true; do
    java -jar server-release.jar
    # shellcheck disable=SC2181
    if [ $? -eq 0 ]; then
        echo "server exited cleanly"
    else
#        java -jar server-release.jar
        echo "server crashed"
    fi
done

