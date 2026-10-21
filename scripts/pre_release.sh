#!/bin/bash

## This script should be called before each tor-vpn release 
## and resulting changes should be committed to keep translations
## and pre-shipped bridges up-to-date.

# Get Translations dep
mkdir -p deps
cd deps

if [ ! -d "translation" ]; then
  echo "cloning translation/tor-vpn"
  git clone https://gitlab.torproject.org/tpo/translation.git --branch tor-vpn  --single-branch
else
  echo "pulling latest translation/tor-vpn"
  cd translation
  git pull -f
  cd ..
fi

cd ..

# fetch translations
python3 scripts/get-l10n.py deps/translation
# IF the above fails like it is at 2025.12.11 because it's getting anubis anti ddos HTML/JS instead of JSON
# load in the browser and manually paste the json into files deps/weblate.1.json and 2 as needed and
# comment the above and uncomment the below to use those instead
# python3 scripts/get-l10n.py deps/translation -f deps/weblate

# fetch bridges
./scripts/fetch_default_bridges.sh