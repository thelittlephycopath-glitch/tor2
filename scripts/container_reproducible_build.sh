
#!/usr/bin/env bash

TLD=$(git rev-parse --show-toplevel)

cd $TLD
mkdir -p build

#
# TODO: Ideally one day the project adopts a versioning plan for contaienrs for these builds as
#   change over time and then we include the version here too. for now the Containerfile in the c
#   commit may have to be built to get it.
CONTAINER=tor/rustjava

podman image exists $CONTAINER

if [ $? -ne 0 ]; then
  echo "Error: container tor/rustjava not detected!"
  echo "To build it, in project root, run: \`podman build . -t tor/rustjava\`"
  exit 1
fi

exec podman run --rm -i -v $TLD:/builds/vpn \
		-w /builds/vpn $CONTAINER \
		sh -c "./scripts/reproducible_build.sh $*"