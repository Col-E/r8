#!/bin/bash
#
# Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

set -e
set -x

echo "Update this script manually before using"
echo "If updating API database also update API_LEVEL in " \
    "AndroidApiHashingDatabaseBuilderGeneratorTest"
exit -1

# Download Platform SDK in @SDK_HOME
SDK_HOME=$HOME/Android/Sdk

# Modify these to match the SDK android.jar to add.
SDK_DIR_NAME=android-Sv2
SDK_VERSION=32

SDK_DIR=$SDK_HOME/platforms/$SDK_DIR_NAME
THIRD_PARTY_ANDROID_JAR=third_party/android_jar
THIRD_PARTY_ANDROID_JAR_LIB=$THIRD_PARTY_ANDROID_JAR/lib-v$SDK_VERSION

UPDATE_ANDROID_JAR="no"
if [[ "$UPDATE_ANDROID_JAR" == "yes" ]]; then
  rm -rf $THIRD_PARTY_ANDROID_JAR_LIB
  rm -f ${THIRD_PARTY_ANDROID_JAR_LIB}.tar.gz
  rm -f ${THIRD_PARTY_ANDROID_JAR_LIB}.tar.sha1

  mkdir -p $THIRD_PARTY_ANDROID_JAR_LIB/optional
  cp $SDK_DIR/android.jar $THIRD_PARTY_ANDROID_JAR_LIB/android.jar
  cp $SDK_DIR/optional/*.jar $THIRD_PARTY_ANDROID_JAR_LIB/optional
  cp $SDK_DIR/optional/optional.json $THIRD_PARTY_ANDROID_JAR_LIB/optional
  cp $THIRD_PARTY_ANDROID_JAR/lib-v31/README.google $THIRD_PARTY_ANDROID_JAR_LIB
  vi $THIRD_PARTY_ANDROID_JAR_LIB/README.google

  (cd $THIRD_PARTY_ANDROID_JAR \
      && upload_to_google_storage.py -a --bucket r8-deps lib-v$SDK_VERSION)
  rm -rf $THIRD_PARTY_ANDROID_JAR_LIB
  rm ${THIRD_PARTY_ANDROID_JAR_LIB}.tar.gz
  git add ${THIRD_PARTY_ANDROID_JAR_LIB}.tar.gz.sha1
fi

UPDATE_API_DATABASE="no"
if [[ "$UPDATE_API_DATABASE" == "yes" ]]; then
  rm -rf $THIRD_PARTY_ANDROID_JAR/api-versions
  rm -f $THIRD_PARTY_ANDROID_JAR/api-versions.tar.gz
  rm -f $THIRD_PARTY_ANDROID_JAR/api-versions.tar.gz.sha1
  mkdir -p $THIRD_PARTY_ANDROID_JAR/api-versions
  cp $SDK_DIR/data/api-versions.xml $THIRD_PARTY_ANDROID_JAR/api-versions
  (cd $THIRD_PARTY_ANDROID_JAR \
      && upload_to_google_storage.py -a --bucket r8-deps api-versions)
  tools/gradle.py r8NoManifestWithoutDeps testJar repackageTestDeps
  java -cp build/libs/r8_no_manifest_without_deps.jar:build/libs/deps_all.jar:build/libs/r8tests.jar:build/libs/test_deps_all.jar \
      com.android.tools.r8.apimodel.AndroidApiHashingDatabaseBuilderGeneratorTest

  rm -rf $THIRD_PARTY_ANDROID_JAR/api-versions
  rm -f $THIRD_PARTY_ANDROID_JAR/api-versions.tar.gz
  git add $THIRD_PARTY_ANDROID_JAR/api-versions.tar.gz.sha1
  git add src/main/resources/api_database/api_database_ambiguous.txt
  git add src/main/resources/api_database/api_database_api_level.ser
  git add src/main/resources/api_database/api_database_hash_lookup.ser
fi

echo "Update build.gradle with this new cloud dependency, " \
    "and verify with tools/gradle.py downloadDeps"
