// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.desugaredlibrary.gson;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.rules.TemporaryFolder;

public abstract class GsonDesugaredLibraryTestUtils {

  static final Path GSON_CONFIGURATION =
      ToolHelper.getSourceFileForTestClass(GsonDesugaredLibraryTestUtils.class)
          .getParent()
          .resolve("gson.cfg");
  // TODO(b/289363570): GSON should not rely on Iosched.
  static final Path GSON_2_8_1_JAR =
      Paths.get(ToolHelper.THIRD_PARTY_DIR, "iosched_2019/gson-2.8.1.jar");

  static String uniqueName(
      TemporaryFolder temp,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification,
      TestParameters parameters)
      throws IOException {
    return temp.newFolder("test_serialization").toString()
        + "/test_"
        + libraryDesugaringSpecification.toString()
        + "_"
        + compilationSpecification.toString()
        + "_"
        + parameters.getRuntime()
        + "_"
        + parameters.getApiLevel();
  }
}
