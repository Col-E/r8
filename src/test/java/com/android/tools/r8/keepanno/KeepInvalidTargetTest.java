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
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.keepanno.annotations.KeepOption;
import com.android.tools.r8.keepanno.annotations.KeepTarget;
import com.android.tools.r8.keepanno.annotations.UsesReflection;
import com.android.tools.r8.keepanno.asm.KeepEdgeReader;
import com.android.tools.r8.keepanno.ast.KeepDeclaration;
import com.android.tools.r8.keepanno.ast.KeepEdgeException;
import com.android.tools.r8.keepanno.keeprules.KeepRuleExtractor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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

  private static List<String> extractRuleForClass(Class<?> clazz) throws IOException {
    List<KeepDeclaration> keepEdges =
        KeepEdgeReader.readKeepEdges(ToolHelper.getClassAsBytes(clazz));
    List<String> rules = new ArrayList<>();
    KeepRuleExtractor extractor = new KeepRuleExtractor(rules::add);
    keepEdges.forEach(extractor::extract);
    return rules;
  }

  private void assertThrowsWith(ThrowingRunnable fn, Matcher<String> matcher) {
    try {
      fn.run();
    } catch (KeepEdgeException e) {
      assertThat(e.getMessage(), matcher);
      return;
    } catch (Throwable e) {
      fail("Expected run to fail with KeepEdgeException, but failed with: " + e);
    }
    fail("Expected run to fail");
  }

  @Test
  public void testInvalidClassDecl() {
    assertThrowsWith(
        () -> extractRuleForClass(MultipleClassDeclarations.class),
        allOf(
            containsString("Multiple declarations"),
            containsString("className"),
            containsString("classConstant")));
  }

  static class MultipleClassDeclarations {

    @UsesReflection(@KeepTarget(className = "foo", classConstant = MultipleClassDeclarations.class))
    public static void main(String[] args) {
      System.out.println("Hello, world");
    }
  }

  @Test
  public void testInvalidClassDeclWithBinding() {
    assertThrowsWith(
        () -> extractRuleForClass(BindingAndClassDeclarations.class),
        allOf(containsString("class binding"), containsString("class patterns")));
  }

  static class BindingAndClassDeclarations {

    // Both properties are using the "default" value of an empty string, but should still fail.
    @UsesReflection({@KeepTarget(classFromBinding = "", className = "")})
    public static void main(String[] args) {
      System.out.println("Hello, world");
    }
  }

  @Test
  public void testInvalidExtendsDecl() {
    assertThrowsWith(
        () -> extractRuleForClass(MultipleExtendsDeclarations.class),
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
    public static void main(String[] args) {
      System.out.println("Hello, world");
    }
  }

  @Test
  public void testInvalidMemberDecl() {
    assertThrowsWith(
        () -> extractRuleForClass(MultipleMemberDeclarations.class),
        allOf(containsString("field"), containsString("method")));
  }

  static class MultipleMemberDeclarations {

    @UsesReflection(@KeepTarget(classConstant = A.class, methodName = "foo", fieldName = "bar"))
    public static void main(String[] args) {
      System.out.println("Hello, world");
    }
  }

  @Test
  public void testInvalidOptionsDecl() {
    assertThrowsWith(
        () -> extractRuleForClass(MultipleOptionDeclarations.class),
        allOf(containsString("options"), containsString("allow"), containsString("disallow")));
  }

  static class MultipleOptionDeclarations {

    @UsesReflection(
        @KeepTarget(
            classConstant = A.class,
            allow = {KeepOption.OPTIMIZATION},
            disallow = {KeepOption.SHRINKING}))
    public static void main(String[] args) {
      System.out.println("Hello, world");
    }
  }

  static class A {
    // just a target.
  }
}
