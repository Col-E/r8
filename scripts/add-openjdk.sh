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
# Update JDK_VERSION below

# Now run script with fingers crossed!

JDK_VERSION=17

tar xf ~/Downloads/openjdk-${JDK_VERSION}_linux-x64_bin.tar.gz
cp -rL jdk-${JDK_VERSION} linux
cp README.google linux
upload_to_google_storage.py -a --bucket r8-deps linux
rm -rf jdk-${JDK_VERSION}
rm -rf linux
rm linux.tar.gz

tar xf ~/Downloads/openjdk-${JDK_VERSION}_macos-x64_bin.tar.gz
cp -rL jdk-${JDK_VERSION}.jdk osx
cp README.google osx
upload_to_google_storage.py -a --bucket r8-deps osx
rm -rf osx
rm -rf jdk-${JDK_VERSION}.jdk
rm osx.tar.gz

unzip ~/Downloads/openjdk-${JDK_VERSION}_windows-x64_bin.zip
cp -rL jdk-${JDK_VERSION} windows
cp README.google windows
upload_to_google_storage.py -a --bucket r8-deps windows
rm -rf windows
rm -rf jdk-${JDK_VERSION}
rm windows.tar.gz

git add *.sha1

echo "Update additional files, see https://r8-review.googlesource.com/c/r8/+/61909"