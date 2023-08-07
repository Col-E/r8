// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.internal;

import com.android.tools.r8.ToolHelper;

public abstract class NestCompilationBase extends CompilationTestBase {
  static final String BASE = ToolHelper.THIRD_PARTY_DIR + "nest/nest_20180926_7c6cfb/";
  static final String DEPLOY_JAR = "obsidian-development-debug.jar";
  static final String PG_CONF = "proguard/proguard.cfg";
  static final String PG_CONF_NO_OPT = "proguard/proguard-no-optimizations.cfg";
}
