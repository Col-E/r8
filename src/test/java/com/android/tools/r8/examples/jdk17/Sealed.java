// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.examples.jdk17;

import com.android.tools.r8.examples.JavaExampleClassProxy;
import java.nio.file.Path;

public class Sealed {

  private static final String EXAMPLE_FILE = "examplesJava17/sealed";

  public static final JavaExampleClassProxy Compiler =
      new JavaExampleClassProxy(EXAMPLE_FILE, "sealed/Compiler");
  public static final JavaExampleClassProxy R8Compiler =
      new JavaExampleClassProxy(EXAMPLE_FILE, "sealed/R8Compiler");
  public static final JavaExampleClassProxy D8Compiler =
      new JavaExampleClassProxy(EXAMPLE_FILE, "sealed/D8Compiler");
  public static final JavaExampleClassProxy Main =
      new JavaExampleClassProxy(EXAMPLE_FILE, "sealed/Main");

  public static Path jar() {
    return JavaExampleClassProxy.examplesJar(EXAMPLE_FILE);
  }
}
