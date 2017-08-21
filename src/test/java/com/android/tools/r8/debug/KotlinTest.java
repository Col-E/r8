// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import org.apache.harmony.jpda.tests.framework.jdwp.Value;
import org.junit.Assert;
import org.junit.Test;

public class KotlinTest extends DebugTestBase {

  @Test
  public void testKotlinApp() throws Throwable {
    final String inliningMethodName = "invokeInlinedFunctions";
    runDebugTestKotlin("KotlinApp",
        breakpoint("KotlinApp$Companion", "main"),
        run(),
        inspect(s -> {
          Assert.assertEquals("KotlinApp.kt", s.getSourceFile());
          Assert.assertEquals(8, s.getLineNumber());
          s.checkLocal("this");
          s.checkLocal("args");
        }),
        stepOver(),
        inspect(s -> {
          Assert.assertEquals(9, s.getLineNumber());
          s.checkLocal("this");
          s.checkLocal("args");
        }),
        stepOver(),
        inspect(s -> {
          Assert.assertEquals(10, s.getLineNumber());
          s.checkLocal("this");
          s.checkLocal("args");
          s.checkLocal("instance");
        }),
        stepOver(),
        inspect(s -> {
          Assert.assertEquals(11, s.getLineNumber());
          s.checkLocal("this");
          s.checkLocal("args");
          s.checkLocal("instance");
        }),
        stepInto(),
        inspect(s -> {
          Assert.assertEquals(inliningMethodName, s.getMethodName());
          Assert.assertEquals(24, s.getLineNumber());
          s.checkLocal("this");
        }),
        stepInto(),
        inspect(s -> {
          // We must have stepped into the code of the inlined method but the actual current method
          // did not change.
          Assert.assertEquals(inliningMethodName, s.getMethodName());
          // TODO(shertz) get the original line if JSR45 is supported by the targeted ART runtime.
          s.checkLocal("this");
        }),
        stepInto(),
        inspect(s -> {
          Assert.assertEquals(inliningMethodName, s.getMethodName());
          Assert.assertEquals(25, s.getLineNumber());
          s.checkLocal("this");
        }),
        stepInto(),
        inspect(s -> {
          Assert.assertEquals(inliningMethodName, s.getMethodName());
          Assert.assertEquals(26, s.getLineNumber());
          s.checkLocal("this");
          s.checkLocal("inA", Value.createInt(1));
          // This is a "hidden" lv added by Kotlin (which is neither initialized nor used).
          s.checkLocal("$i$f$inlinedA");
          s.checkLocal("$i$a$1$inlinedA");
        }),
        stepInto(),
        inspect(s -> {
          // We must have stepped into the code of the second inlined method but the actual current
          // method did not change.
          Assert.assertEquals(inliningMethodName, s.getMethodName());
          // TODO(shertz) get the original line if JSR45 is supported by the targeted ART runtime.
          s.checkLocal("this");
        }),
        stepInto(),
        inspect(s -> {
          Assert.assertEquals(inliningMethodName, s.getMethodName());
          Assert.assertEquals(27, s.getLineNumber());
          s.checkLocal("this");
        }),
        stepInto(),
        inspect(s -> {
          Assert.assertEquals(inliningMethodName, s.getMethodName());
          Assert.assertEquals(28, s.getLineNumber());
          s.checkLocal("this");
          s.checkLocal("inB", Value.createInt(2));
          // This is a "hidden" lv added by Kotlin (which is neither initialized nor used).
          s.checkLocal("$i$f$inlinedB");
          s.checkLocal("$i$a$1$inlinedB");
        }),
        run());
  }

}
