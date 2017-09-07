// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.apache.harmony.jpda.tests.framework.jdwp.Value;
import org.junit.Ignore;
import org.junit.Test;

// TODO check double-depth inline (an inline in another inline)
public class KotlinInlineTest extends KotlinDebugTestBase {

  @Test
  public void testStepOverInline() throws Throwable {
    String methodName = "singleInline";
    runDebugTestKotlin("KotlinInline",
        breakpoint("KotlinInline", methodName),
        run(),
        inspect(s -> {
          assertEquals("KotlinInline", s.getClassName());
          assertEquals(methodName, s.getMethodName());
          assertEquals("KotlinInline.kt", s.getSourceFile());
          assertEquals(41, s.getLineNumber());
          s.checkLocal("this");
        }),
        stepOver(),
        inspect(s -> {
          assertEquals("KotlinInline", s.getClassName());
          assertEquals(methodName, s.getMethodName());
          assertEquals("KotlinInline.kt", s.getSourceFile());
          assertEquals(42, s.getLineNumber());
          s.checkLocal("this");
        }),
        kotlinStepOver(),
        inspect(s -> {
          assertEquals("KotlinInline", s.getClassName());
          assertEquals(methodName, s.getMethodName());
          assertEquals("KotlinInline.kt", s.getSourceFile());
          assertEquals(43, s.getLineNumber());
          s.checkLocal("this");
        }),
        run());
  }

  @Test
  public void testStepIntoInline() throws Throwable {
    String methodName = "singleInline";
    runDebugTestKotlin("KotlinInline",
        breakpoint("KotlinInline", methodName),
        run(),
        inspect(s -> {
          assertEquals("KotlinInline", s.getClassName());
          assertEquals(methodName, s.getMethodName());
          assertEquals("KotlinInline.kt", s.getSourceFile());
          assertEquals(41, s.getLineNumber());
          s.checkLocal("this");
        }),
        stepOver(),
        inspect(s -> {
          assertEquals("KotlinInline", s.getClassName());
          assertEquals(methodName, s.getMethodName());
          assertEquals("KotlinInline.kt", s.getSourceFile());
          assertEquals(42, s.getLineNumber());
          s.checkLocal("this");
        }),
        stepInto(),
        inspect(s -> {
          assertEquals("KotlinInline", s.getClassName());
          assertEquals(methodName, s.getMethodName());
          assertEquals("KotlinInline.kt", s.getSourceFile());
          // The actual line number (the one encoded in debug information) is different than the
          // source file one.
          // TODO(shertz) extract original line number from JSR-45's SMAP (only supported on
          // Android O+).
          assertTrue(42 != s.getLineNumber());
          s.checkLocal("this");
        }),
        run());
  }

  @Test
  public void testStepOutInline() throws Throwable {
    String methodName = "singleInline";
    runDebugTestKotlin("KotlinInline",
        breakpoint("KotlinInline", methodName),
        run(),
        inspect(s -> {
          assertEquals("KotlinInline", s.getClassName());
          assertEquals(methodName, s.getMethodName());
          assertEquals("KotlinInline.kt", s.getSourceFile());
          assertEquals(41, s.getLineNumber());
          s.checkLocal("this");
        }),
        stepOver(),
        inspect(s -> {
          assertEquals("KotlinInline", s.getClassName());
          assertEquals(methodName, s.getMethodName());
          assertEquals("KotlinInline.kt", s.getSourceFile());
          assertEquals(42, s.getLineNumber());
          s.checkLocal("this");
        }),
        stepInto(),
        inspect(s -> {
          assertEquals("KotlinInline", s.getClassName());
          assertEquals(methodName, s.getMethodName());
        }),
        kotlinStepOut(),
        inspect(s -> {
          assertEquals("KotlinInline", s.getClassName());
          assertEquals(methodName, s.getMethodName());
          assertEquals("KotlinInline.kt", s.getSourceFile());
          assertEquals(43, s.getLineNumber());
          s.checkLocal("this");
        }),
        run());
  }

  @Test
  public void testKotlinInline() throws Throwable {
    final String inliningMethodName = "invokeInlinedFunctions";
    runDebugTestKotlin("KotlinInline",
        breakpoint("KotlinInline", inliningMethodName),
        run(),
        inspect(s -> {
          assertEquals(inliningMethodName, s.getMethodName());
          assertEquals(16, s.getLineNumber());
          s.checkLocal("this");
        }),
        stepInto(),
        inspect(s -> {
          // We must have stepped into the code of the inlined method but the actual current method
          // did not change.
          assertEquals(inliningMethodName, s.getMethodName());
          // TODO(shertz) get the original line if JSR45 is supported by the targeted ART runtime.
          s.checkLocal("this");
        }),
        stepInto(),
        inspect(s -> {
          assertEquals(inliningMethodName, s.getMethodName());
          assertEquals(17, s.getLineNumber());
          s.checkLocal("this");
        }),
        stepInto(),
        inspect(s -> {
          assertEquals(inliningMethodName, s.getMethodName());
          assertEquals(18, s.getLineNumber());
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
          assertEquals(inliningMethodName, s.getMethodName());
          // TODO(shertz) get the original line if JSR45 is supported by the targeted ART runtime.
          s.checkLocal("this");
        }),
        stepInto(),
        inspect(s -> {
          assertEquals(inliningMethodName, s.getMethodName());
          assertEquals(19, s.getLineNumber());
          s.checkLocal("this");
        }),
        stepInto(),
        inspect(s -> {
          assertEquals(inliningMethodName, s.getMethodName());
          assertEquals(20, s.getLineNumber());
          s.checkLocal("this");
          s.checkLocal("inB", Value.createInt(2));
          // This is a "hidden" lv added by Kotlin (which is neither initialized nor used).
          s.checkLocal("$i$f$inlinedB");
          s.checkLocal("$i$a$1$inlinedB");
        }),
        run());
  }

}
