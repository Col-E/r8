// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import org.junit.Test;

public class Dummy extends TestBase {

  @Test
  public void testKeep() throws Exception {
    PrintUses.main(
        ToolHelper.getJava8RuntimeJar().toString(),
        "build/libs/r8_without_deps.jar",
        "build/libs/r8tests.jar");
    PrintUses.main(
        "--keeprules",
        ToolHelper.getJava8RuntimeJar().toString(),
        "build/libs/r8_without_deps.jar",
        "build/libs/r8tests.jar");
  }
}
