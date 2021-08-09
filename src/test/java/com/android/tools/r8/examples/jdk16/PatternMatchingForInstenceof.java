// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.examples.jdk16;

import com.android.tools.r8.examples.JavaExampleClassProxy;
import java.nio.file.Path;

public class PatternMatchingForInstenceof {

  private static final String EXAMPLE_FILE = "examplesJava16/pattern_matching_for_instanceof";

  public static final JavaExampleClassProxy Main =
      new JavaExampleClassProxy(EXAMPLE_FILE, "pattern_matching_for_instanceof/Main");

  public static Path jar() {
    return JavaExampleClassProxy.examplesJar(EXAMPLE_FILE);
  }
}
