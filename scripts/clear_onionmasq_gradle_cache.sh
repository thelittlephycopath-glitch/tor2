#!/bin/bash

echo "Attention, use this script on your own risk ;)"
find ~/.gradle -name *onionmasq* -type d | xargs -I {} rm -rf {}
