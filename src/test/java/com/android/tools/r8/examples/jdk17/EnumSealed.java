// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.examples.jdk17;

import com.android.tools.r8.examples.JavaExampleClassProxy;
import java.nio.file.Path;

public class EnumSealed {

  private static final String EXAMPLE_FILE = "examplesJava17/enum_sealed";

  public static final JavaExampleClassProxy Enum =
      new JavaExampleClassProxy(EXAMPLE_FILE, "enum_sealed/Enum");
  public static final JavaExampleClassProxy EnumB =
      new JavaExampleClassProxy(EXAMPLE_FILE, "enum_sealed/Enum$1");
  public static final JavaExampleClassProxy Main =
      new JavaExampleClassProxy(EXAMPLE_FILE, "enum_sealed/Main");

  public static Path jar() {
    return JavaExampleClassProxy.examplesJar(EXAMPLE_FILE);
  }
}
