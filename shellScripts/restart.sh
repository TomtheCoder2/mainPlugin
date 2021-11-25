#!/bin/bash

sleep 2
echo "restart v7 server"
screen -d -r v7 -X stuff $'\nsleep 1\nls\n'
sleep 2
screen -d -r v7 -X stuff $'ls\njava -jar server-release.jar\nstop\nhost\n'
