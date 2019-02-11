// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.TestBase;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.Ignore;
import org.junit.Test;

public class UnusedTypeInThrowingTestRunner extends TestBase {

  static final Class THROWABLE_CLASS = UnusedTypeInThrowingThrowable.class;
  static final Class MAIN_CLASS = UnusedTypeInThrowingTest.class;

  @Test
  @Ignore("b/124019003")
  public void testTypeIsMarkedAsLive() throws IOException, CompilationFailedException {
    Path outDex = temp.newFile("out.zip").toPath();
    testForR8(Backend.CF)
        .addProgramClasses(MAIN_CLASS)
        .addProgramClasses(THROWABLE_CLASS)
        .addKeepMainRule(MAIN_CLASS)
        .addKeepRules(ImmutableList.of("-keepattributes Exceptions"))
        .setMode(CompilationMode.RELEASE)
        .enableInliningAnnotations()
        .minification(true)
        .compile()
        .run(MAIN_CLASS)
        .assertSuccessWithOutput("42");
  }
}
