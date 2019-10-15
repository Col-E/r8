// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal;

abstract class GmailCompilationBase extends CompilationTestBase {
  static final String APK = "Gmail_release_unsigned.apk";
  static final String DEPLOY_JAR = "Gmail_release_unstripped_deploy.jar";
  static final String PG_JAR = "Gmail_release_unstripped_proguard.jar";
  static final String PG_MAP = "Gmail_release_unstripped_proguard.map";
  static final String BASE_PG_CONF = "Gmail_release_unstripped_proguard.config";
  static final String PG_CONF = "Gmail_proguard.config";

  final String base;

  GmailCompilationBase(int majorVersion, int minorVersion) {
    this.base = "third_party/gmail/gmail_android_" + majorVersion + "." + minorVersion + "/";
  }
}
