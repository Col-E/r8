// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import static org.junit.Assert.assertEquals;

import org.apache.harmony.jpda.tests.framework.jdwp.Value;
import org.junit.Test;

public class KotlinTest extends KotlinDebugTestBase {

  // TODO(shertz) simplify test
  // TODO(shertz) add more variables ?
  @Test
  public void testStepOver() throws Throwable {
    runDebugTest(
        getD8Config(),
        "KotlinApp",
        breakpoint("KotlinApp$Companion", "main"),
        run(),
        inspect(s -> {
          assertEquals("KotlinApp$Companion", s.getClassName());
          assertEquals("KotlinApp.kt", s.getSourceFile());
          assertEquals(24, s.getLineNumber());
          s.checkLocal("this");
          s.checkLocal("args");
          checkNoLocal("instance");
        }),
        stepOver(),
        inspect(s -> {
          assertEquals(25, s.getLineNumber());
          s.checkLocal("this");
          s.checkLocal("args");
          s.checkLocal("instance");
        }),
        stepOver(),
        inspect(s -> {
          assertEquals(26, s.getLineNumber());
          s.checkLocal("this");
          s.checkLocal("args");
          s.checkLocal("instance");
        }),
        run());
  }

  @Test
  public void testStepIntoAndOut() throws Throwable {
    runDebugTest(
        getD8Config(),
        "KotlinApp",
        breakpoint("KotlinApp$Companion", "main"),
        run(),
        inspect(s -> {
          assertEquals("KotlinApp$Companion", s.getClassName());
          assertEquals("KotlinApp.kt", s.getSourceFile());
          assertEquals(24, s.getLineNumber());
          s.checkLocal("this");
          s.checkLocal("args");
          checkNoLocal("instance");
        }),
        stepOver(),
        inspect(s -> {
          assertEquals(25, s.getLineNumber());
          s.checkLocal("this");
          s.checkLocal("args");
          s.checkLocal("instance");
        }),
        // Step into 1st invoke of ifElse
        stepInto(),
        inspect(s -> {
          assertEquals("KotlinApp", s.getClassName());
          assertEquals("KotlinApp.kt", s.getSourceFile());
          assertEquals(8, s.getLineNumber());
          s.checkLocal("this");
          s.checkLocal("cond", Value.createBoolean(true));
          checkNoLocal("a");
          checkNoLocal("b");
          checkNoLocal("c");
        }),
        stepInto(),
        inspect(s -> {
          assertEquals("KotlinApp", s.getClassName());
          assertEquals(9, s.getLineNumber());
          s.checkLocal("this");
          s.checkLocal("cond", Value.createBoolean(true));
          s.checkLocal("a", Value.createInt(10));
          checkNoLocal("b");
          checkNoLocal("c");
        }),
        stepInto(),
        inspect(s -> {
          // We should be into the 'then' statement.
          assertEquals("KotlinApp", s.getClassName());
          assertEquals(10, s.getLineNumber());
          s.checkLocal("this");
          s.checkLocal("cond", Value.createBoolean(true));
          s.checkLocal("a", Value.createInt(10));
          checkNoLocal("b");
          checkNoLocal("c");
        }),
        stepInto(),
        inspect(s -> {
          assertEquals("KotlinApp", s.getClassName());
          assertEquals(11, s.getLineNumber());
          s.checkLocal("this");
          s.checkLocal("cond", Value.createBoolean(true));
          s.checkLocal("a", Value.createInt(10));
          s.checkLocal("b", Value.createInt(20));
          checkNoLocal("c");
        }),
        // Go back to the main method
        stepOut(),
        inspect(s -> {
          assertEquals("KotlinApp$Companion", s.getClassName());
          assertEquals("KotlinApp.kt", s.getSourceFile());
          assertEquals(26, s.getLineNumber());
          s.checkLocal("this");
          s.checkLocal("args");
          checkNoLocal("instance");
        }),
        // Step into 2nd invoke of ifElse
        stepInto(),
        inspect(s -> {
          assertEquals("KotlinApp", s.getClassName());
          assertEquals("KotlinApp.kt", s.getSourceFile());
          assertEquals(8, s.getLineNumber());
          s.checkLocal("this");
          s.checkLocal("cond", Value.createBoolean(false));
          checkNoLocal("a");
          checkNoLocal("b");
          checkNoLocal("c");
        }),
        stepInto(),
        inspect(s -> {
          assertEquals("KotlinApp", s.getClassName());
          assertEquals(9, s.getLineNumber());
          s.checkLocal("this");
          s.checkLocal("cond", Value.createBoolean(false));
          s.checkLocal("a", Value.createInt(10));
          checkNoLocal("b");
          checkNoLocal("c");
        }),
        stepInto(),
        inspect(s -> {
          // We should be into the 'else' statement this time.
          assertEquals("KotlinApp", s.getClassName());
          assertEquals(13, s.getLineNumber());
          s.checkLocal("this");
          s.checkLocal("cond", Value.createBoolean(false));
          s.checkLocal("a", Value.createInt(10));
          checkNoLocal("b");
          checkNoLocal("c");
        }),
        stepInto(),
        inspect(s -> {
          assertEquals("KotlinApp", s.getClassName());
          assertEquals(14, s.getLineNumber());
          s.checkLocal("this");
          s.checkLocal("cond", Value.createBoolean(false));
          s.checkLocal("a", Value.createInt(10));
          checkNoLocal("b");
          s.checkLocal("c", Value.createInt(5));
        }),
        run());
  }

}
