// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.examples.jdk18.jdk8272564;

import com.android.tools.r8.examples.JavaExampleClassProxy;
import java.nio.file.Path;

public class Jdk8272564 {

  private static final String EXAMPLE_FILE = "examplesJava21/jdk8272564";

  public static final JavaExampleClassProxy A =
      new JavaExampleClassProxy(EXAMPLE_FILE, "jdk8272564/A");
  public static final JavaExampleClassProxy B =
      new JavaExampleClassProxy(EXAMPLE_FILE, "jdk8272564/B");
  public static final JavaExampleClassProxy C =
      new JavaExampleClassProxy(EXAMPLE_FILE, "jdk8272564/C");
  public static final JavaExampleClassProxy I =
      new JavaExampleClassProxy(EXAMPLE_FILE, "jdk8272564/I");
  public static final JavaExampleClassProxy J =
      new JavaExampleClassProxy(EXAMPLE_FILE, "jdk8272564/J");
  public static final JavaExampleClassProxy K =
      new JavaExampleClassProxy(EXAMPLE_FILE, "jdk8272564/K");
  public static final JavaExampleClassProxy Main =
      new JavaExampleClassProxy(EXAMPLE_FILE, "jdk8272564/Main");

  public static Path jar() {
    return JavaExampleClassProxy.examplesJar(EXAMPLE_FILE);
  }
}
