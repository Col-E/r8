#!/bin/bash
#
# Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

set -e
set -x

if [[ -z "$1" ]]; then
  echo "No version argument supplied"
  exit -1
fi

VERSION=$1

RELEASES_ROOT=third_party/openjdk/desugar_jdk_libs_releases
mkdir -p $RELEASES_ROOT
RELEASED_VERSION_DIR=$RELEASES_ROOT/$VERSION
if [[ -d $RELEASED_VERSION_DIR ]]; then
    echo "$RELEASED_VERSION_DIR already exists"
    exit -1
fi

MAVEN_REPO_DIR=/tmp/maven_repo_local
rm -rf $MAVEN_REPO_DIR

DOWNLOAD_DIR=/tmp/desugar_jdk_libs_download
rm -rf $DOWNLOAD_DIR
mkdir -p $DOWNLOAD_DIR

mvn \
  org.apache.maven.plugins:maven-dependency-plugin:2.4:get \
  -Dmaven.repo.local=$MAVEN_REPO_DIR \
  -DremoteRepositories=http://maven.google.com \
  -Dartifact=com.android.tools:desugar_jdk_libs:$VERSION \
  -Ddest=$DOWNLOAD_DIR/desugar_jdk_libs.jar

mvn \
  org.apache.maven.plugins:maven-dependency-plugin:2.4:get \
  -Dmaven.repo.local=$MAVEN_REPO_DIR \
  -DremoteRepositories=http://maven.google.com \
  -Dartifact=com.android.tools:desugar_jdk_libs_configuration:$VERSION \
  -Ddest=$DOWNLOAD_DIR/desugar_jdk_libs_configuration.jar

  unzip $DOWNLOAD_DIR/desugar_jdk_libs_configuration.jar META-INF/desugar/d8/desugar.json -d $DOWNLOAD_DIR

  mkdir $RELEASED_VERSION_DIR
  cp $DOWNLOAD_DIR/desugar_jdk_libs.jar $RELEASED_VERSION_DIR
  cp $DOWNLOAD_DIR/desugar_jdk_libs_configuration.jar $RELEASED_VERSION_DIR
  cp $DOWNLOAD_DIR/META-INF/desugar/d8/desugar.json $RELEASED_VERSION_DIR/desugar.json
  cp third_party/openjdk/desugar_jdk_libs/README.google $RELEASED_VERSION_DIR

  (cd $RELEASES_ROOT && \
    upload_to_google_storage.py -a --bucket r8-deps $VERSION && \
    git add $VERSION.tar.gz.sha1)
