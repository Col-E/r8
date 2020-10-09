// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.desugaredlibrary.gson;

import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import java.nio.file.Path;
import java.nio.file.Paths;

public abstract class GsonDesugaredLibraryTestBase extends DesugaredLibraryTestBase {
  protected static final Path GSON_CONFIGURATION =
      Paths.get("src/test/java/com/android/tools/r8/desugar/desugaredlibrary/gson/gson.cfg");
  protected static final Path GSON_2_8_1_JAR = Paths.get("third_party/iosched_2019/gson-2.8.1.jar");
}
