// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.keepanno.annotations.KeepTarget;
import com.android.tools.r8.keepanno.annotations.UsesReflection;
import com.android.tools.r8.keepanno.ast.KeepEdgeException;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class KeepInvalidTargetTest extends TestBase {

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public KeepInvalidTargetTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  private void assertThrowsWith(ThrowingRunnable fn, Matcher<String> matcher) {
    try {
      fn.run();
      fail("Expected run to fail");
    } catch (KeepEdgeException e) {
      assertThat(e.getMessage(), matcher);
    } catch (Throwable e) {
      fail("Expected run to fail with KeepEdgeException");
    }
  }

  @Test
  public void testInvalidClassDecl() throws Exception {
    assertThrowsWith(
        () -> KeepEdgeAnnotationsTest.getKeepRulesForClass(MultipleClassDeclarations.class),
        allOf(
            containsString("Multiple declarations"),
            containsString("className"),
            containsString("classConstant")));
  }

  static class MultipleClassDeclarations {

    @UsesReflection(@KeepTarget(className = "foo", classConstant = MultipleClassDeclarations.class))
    public static void main(String[] args) throws Exception {
      System.out.println("Hello, world");
    }
  }

  @Test
  public void testInvalidExtendsDecl() throws Exception {
    assertThrowsWith(
        () -> KeepEdgeAnnotationsTest.getKeepRulesForClass(MultipleExtendsDeclarations.class),
        allOf(
            containsString("Multiple declarations"),
            containsString("extendsClassName"),
            containsString("extendsClassConstant")));
  }

  static class MultipleExtendsDeclarations {

    @UsesReflection(
        @KeepTarget(
            extendsClassName = "foo",
            extendsClassConstant = MultipleClassDeclarations.class))
    public static void main(String[] args) throws Exception {
      System.out.println("Hello, world");
    }
  }

  @Test
  public void testInvalidMemberDecl() throws Exception {
    assertThrowsWith(
        () -> KeepEdgeAnnotationsTest.getKeepRulesForClass(MultipleMemberDeclarations.class),
        allOf(containsString("field"), containsString("method")));
  }

  static class MultipleMemberDeclarations {

    @UsesReflection(
        @KeepTarget(
            classConstant = MultipleClassDeclarations.class,
            methodName = "foo",
            fieldName = "bar"))
    public static void main(String[] args) throws Exception {
      System.out.println("Hello, world");
    }
  }
}
