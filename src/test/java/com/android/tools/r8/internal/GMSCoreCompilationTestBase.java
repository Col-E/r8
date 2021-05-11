// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal;

public abstract class GMSCoreCompilationTestBase extends CompilationTestBase {
  // Files pertaining to the full GMSCore build.
  static final String PG_CONF = "GmsCore_prod_alldpi_release_all_locales_proguard.config";
  static final String PG_MAP = "GmsCore_prod_alldpi_release_all_locales_proguard.map";
  static final String DEPLOY_JAR = "GmsCore_prod_alldpi_release_all_locales_deploy.jar";
  static final String RELEASE_APK_X86 = "x86_GmsCore_prod_alldpi_release.apk";
}
