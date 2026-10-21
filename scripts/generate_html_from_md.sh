#!/bin/bash

SCRIPT_DIR=$(dirname "$0")
BASEDIR=$SCRIPT_DIR/..

cd $BASEDIR/doc
for i in *.md ; do
  echo "$i"
  pandoc -s $i --metadata title="$i" --css="file:///android_asset/help/layout.css" -o ../app/src/main/assets/help/$i.html ;
  sed -i '/<header id="title-block-header">/,/<\/header>/d' "../app/src/main/assets/help/$i.html"
done
cd --
