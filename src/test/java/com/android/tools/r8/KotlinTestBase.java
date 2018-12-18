// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.utils.FileUtils;
import java.nio.file.Path;
import java.nio.file.Paths;

public abstract class KotlinTestBase extends TestBase {
  private static final String RSRC = "kotlinR8TestResources";

  protected final KotlinTargetVersion targetVersion;

  protected KotlinTestBase(KotlinTargetVersion targetVersion) {
    this.targetVersion = targetVersion;
  }

  protected Path getKotlinJarFile(String folder) {
    return Paths.get(ToolHelper.TESTS_BUILD_DIR, RSRC,
        targetVersion.getFolderName(), folder + FileUtils.JAR_EXTENSION);
  }

  protected Path getJavaJarFile(String folder) {
    return Paths.get(ToolHelper.TESTS_BUILD_DIR, RSRC,
        targetVersion.getFolderName(), folder + ".java" + FileUtils.JAR_EXTENSION);
  }

  protected Path getMappingfile(String folder, String mappingFileName) {
    return Paths.get(ToolHelper.TESTS_DIR, RSRC, folder, mappingFileName);
  }

}
