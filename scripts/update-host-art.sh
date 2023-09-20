#! /bin/bash
# Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

# This script will update the host art VM in tools/linux/art

# Before running this script make sure that you have a full android build
# and that the host Art version required is build in ~/android/master:
#
#  m -j24
#  m -j24 build-art
#
# Maybe also run the Art host tests:
#
#  m -j24 test-art-host

set -e

ANDROID_CHECKOUT=~/android/master
ANDROID_PRODUCT=angler
ART_DIR=art
DEST_ROOT=tools/linux

function usage {
  echo "Usage: $(basename $0)"
  echo "  [--android-checkout <android repo root>]"
  echo "  [--art-dir <destination directory>]"
  echo "  [--android-product <product name>]"
  echo "  [--destination-dir <destination directory>]"
  echo ""
  echo "  --android-checkout specifies the Android repo root"
  echo "  Defaults to: $ANDROID_CHECKOUT"
  echo ""
  echo "  --art-dir specifies the directory name inside the root destination"
  echo "  directory for the art bundle"
  echo "  Defaults to: $ART_DIR"
  echo ""
  echo "  --android-product specifies the Android product for the framework to include"
  echo "  Defaults to: $ANDROID_PRODUCT"
  echo ""
  echo "  --destination-dir specifies the root destination directory for the art bundle"
  echo "  Defaults to: $DEST_ROOT"
  echo ""
  echo "Update the master version of art from ~/android/master:"
  echo "  "
  echo "  $(basename $0)"
  echo "  "
  echo "Update a specific version of art:"
  echo "  "
  echo "  $(basename $0) --android-checkout ~/android/5.1.1_r19 --art-dir 5.1.1"
  echo "  "
  echo "Test the Art bundle in a temporary directory:"
  echo "  "
  echo "  $(basename $0) --android-checkout ~/android/5.1.1_r19 --art-dir art-5.1.1 --android-product mako --destination-dir /tmp/art"
  echo "  "
  exit 1
}

# Process options.
while [ $# -gt 0 ]; do
  case $1 in
    --android-checkout)
      ANDROID_CHECKOUT="$2"
      shift 2
      ;;
    --art-dir)
      ART_DIR="$2"
      shift 2
      ;;
    --android-product)
      ANDROID_PRODUCT="$2"
      shift 2
      ;;
    --destination-dir)
      DEST_ROOT="$2"
      shift 2
      ;;
    --help|-h|-H|-\?)
      usage
      ;;
    *)
      echo "Unkonwn option $1"
      echo ""
      usage
      ;;
  esac
done

ANDROID_HOST_BUILD=$ANDROID_CHECKOUT/out/host/linux-x86
ANDROID_TARGET_BUILD=$ANDROID_CHECKOUT/out/target
DEST=$DEST_ROOT/$ART_DIR

# Clean out the previous version of Art.
rm -rf $DEST

# Copy build_spec.xml for documentation.
mkdir -p $DEST
if [ -f $ANDROID_CHECKOUT/build_spec.xml ]; then
  cp $ANDROID_CHECKOUT/build_spec.xml $DEST
  # Remove the build spec to ensure it is created anew for a new build.
  rm $ANDROID_CHECKOUT/build_spec.xml
else
  echo "File $ANDROID_CHECKOUT/build_spec.xml not found. Please run"
  echo
  echo "  repo manifest -r -o build_spec.xml"
  echo
  echo "in $ANDROID_CHECKOUT"
  exit 1
fi

# Required binaries and scripts.
mkdir -p $DEST/bin
if [ -f $ANDROID_HOST_BUILD/bin/art ]; then
  cp $ANDROID_HOST_BUILD/bin/art $DEST/bin
else
  cp $ANDROID_CHECKOUT/art/tools/art $DEST/bin
fi

cp $ANDROID_HOST_BUILD/bin/dalvikvm64 $DEST/bin
cp $ANDROID_HOST_BUILD/bin/dalvikvm32 $DEST/bin
# Copy the dalvikvm link creating a regular file instead, as download_from_google_stroage.py does
# not allow tar files with symbolic links (depending on Android/Art version dalvikvm links to
# dalvikvm32 or dalvikvm64).
cp $ANDROID_HOST_BUILD/bin/dalvikvm $DEST/bin
cp $ANDROID_HOST_BUILD/bin/dex2oat $DEST/bin
if [ -e $ANDROID_HOST_BUILD/bin/dex2oat64 ]
  then cp $ANDROID_HOST_BUILD/bin/dex2oat64 $DEST/bin
fi
if [ -e $ANDROID_HOST_BUILD/bin/patchoat ]
  # File patchoat does not exist on Q anymore.
  then cp $ANDROID_HOST_BUILD/bin/patchoat $DEST/bin
fi


# Required framework files.
mkdir -p $DEST/framework
mkdir -p $DEST/out/host/linux-x86/framework
cp -R $ANDROID_HOST_BUILD/framework/* $DEST/framework
cp $ANDROID_HOST_BUILD/framework/*.jar $DEST/out/host/linux-x86/framework

# Required library files.
mkdir -p $DEST/lib64
cp -r $ANDROID_HOST_BUILD/lib64/* $DEST/lib64
mkdir -p $DEST/lib
cp -r $ANDROID_HOST_BUILD/lib/* $DEST/lib

# Image files required for dex2oat of actual android apps. We need an actual android product
# image containing framework classes to verify the code against.
mkdir -p $DEST/product/$ANDROID_PRODUCT/system/framework
cp -rL $ANDROID_TARGET_BUILD/product/$ANDROID_PRODUCT/system/framework/* $DEST/product/$ANDROID_PRODUCT/system/framework

# Required auxillary files.
if [ -e $ANDROID_HOST_BUILD/apex ]; then
  mkdir -p $DEST/apex
  cp -rL $ANDROID_HOST_BUILD/apex/* $DEST/apex
  if [ -e $ANDROID_HOST_BUILD/com.android.i18n ]; then
    mkdir -p $DEST/com.android.i18n
    cp -r $ANDROID_HOST_BUILD/com.android.i18n/* $DEST/com.android.i18n
  fi
  if [ -e $ANDROID_HOST_BUILD/com.android.tzdata ]; then
    mkdir -p $DEST/com.android.tzdata
    cp -r $ANDROID_HOST_BUILD/com.android.tzdata/* $DEST/com.android.tzdata
  fi
  if [ -e $ANDROID_HOST_BUILD/usr ]; then
    mkdir -p $DEST/usr
    cp -r $ANDROID_HOST_BUILD/usr/* $DEST/usr
  fi
else
  if [ -e $ANDROID_HOST_BUILD/usr/icu ]; then
    mkdir -p $DEST/usr/icu
    cp -r $ANDROID_HOST_BUILD/usr/icu/* $DEST/usr/icu
  else
    mkdir -p $DEST/com.android.runtime/etc/icu/
    cp -r $ANDROID_HOST_BUILD/com.android.runtime/etc/icu/* $DEST/com.android.runtime/etc/icu/
  fi
fi

# Update links for vdex files for Android P and later.
if [ -f $DEST/product/$ANDROID_PRODUCT/system/framework/boot.vdex ]; then
  for VDEXFILE in $DEST/product/$ANDROID_PRODUCT/system/framework/*.vdex; do
    VDEXNAME=$(basename ${VDEXFILE});
    for ARCH in arm arm64; do
      rm $DEST/product/$ANDROID_PRODUCT/system/framework/$ARCH/${VDEXNAME};
      # This relative link command will create a symbolic link of the form
      # ../${VDEXNAME} for each architecture.
      # ln -r -s $DEST/product/$ANDROID_PRODUCT/system/framework/${VDEXNAME} $DEST/product/$ANDROID_PRODUCT/system/framework/$ARCH/${VDEXNAME};
      # The Cloud Storage dependency tool (download_from_google_storage.py) does
      # not allow synlinks at all, so instad of the ln in the comment above just
      # copy the ${VDEXNAME} files.
      cp $DEST/product/$ANDROID_PRODUCT/system/framework/${VDEXNAME} $DEST/product/$ANDROID_PRODUCT/system/framework/$ARCH/${VDEXNAME};
    done
  done
fi

# Allow failure for strip commands below.
set +e

strip $DEST/bin/* 2> /dev/null
strip $DEST/lib/*
strip $DEST/lib64/*
strip $DEST/framework/x86/* 2> /dev/null
strip $DEST/framework/x86_64/* 2> /dev/null

echo "Now run"
echo "(cd $DEST_ROOT; upload_to_google_storage.py -a --bucket r8-deps $ART_DIR)"
echo "NOTE; If $ART_DIR has several directory elements adjust accordingly."
