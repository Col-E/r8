#!/bin/bash
#
# Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

set -e
set -x

echo "Update this script manually before using"
exit -1

# Download JDK from https://jdk.java.net/X/ (X = version) into ~/Downloads
# Create directory third_party/openjdk/jdk-X
# cd into third_party/openjdk/jdk-X
# Prepare README.google
# Update JDK_VERSION and JDK_VERSION_FULL below

# Now run script with fingers crossed!

JDK_VERSION="20.0.1"
JDK_VERSION_FULL=${JDK_VERSION}
# For ea versions the full version name has a postfix.
# JDK_VERSION_FULL="${JDK_VERSION}-ea+33"

rm -rf linux
rm -f linux.tar.gz
rm -f linux.tar.gz.sha1
tar xf ~/Downloads/openjdk-${JDK_VERSION_FULL}_linux-x64_bin.tar.gz
cp -rL jdk-${JDK_VERSION} linux
cp README.google linux
upload_to_google_storage.py -a --bucket r8-deps linux
rm -rf jdk-${JDK_VERSION}
rm -rf linux
rm linux.tar.gz

rm -rf osx
rm -f osx.tar.gz
rm -f osx.tar.gz.sha1
tar xf ~/Downloads/openjdk-${JDK_VERSION_FULL}_macos-x64_bin.tar.gz
cp -rL jdk-${JDK_VERSION}.jdk osx
cp README.google osx
upload_to_google_storage.py -a --bucket r8-deps osx
rm -rf jdk-${JDK_VERSION}.jdk
rm -rf osx
rm osx.tar.gz

rm -rf windows
rm -f windows.tar.gz
rm -f windows.tar.gz.sha1
unzip ~/Downloads/openjdk-${JDK_VERSION_FULL}_windows-x64_bin.zip
cp -rL jdk-${JDK_VERSION} windows
cp README.google windows
upload_to_google_storage.py -a --bucket r8-deps windows
rm -rf jdk-${JDK_VERSION}
rm -rf windows
rm windows.tar.gz

git add *.sha1

echo "Update additional files, see https://r8-review.googlesource.com/c/r8/+/61909"