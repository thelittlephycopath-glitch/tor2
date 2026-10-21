#!/bin/bash

set -e

# This file used by reproducible scripts and fdroid to get correct rust version and package versions

# Ensure executing in ...../vpn/
cd $(dirname "$0")
cd ..

echo "  Installing rust tools: bindgen-cli, cargo-ndk..."
cargo install bindgen-cli@0.71.1 --locked
cargo install cargo-ndk@3.5.4 --locked