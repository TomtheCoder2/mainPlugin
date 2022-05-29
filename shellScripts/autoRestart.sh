#!/bin/bash
screen -r mod

while true; do
    java -jar server-release.jar
    # shellcheck disable=SC2181
    if [ $? -eq 0 ]; then
        echo "server exited cleanly"
    else
        echo "server crashed"
    fi
done

