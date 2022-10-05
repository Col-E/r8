// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NoFieldTypeStrengthening;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class OverloadedReservedFieldNamingTest extends TestBase {

  private final boolean overloadAggressively;
  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{1}, overload aggressively: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public OverloadedReservedFieldNamingTest(
      boolean overloadAggressively, TestParameters parameters) {
    this.overloadAggressively = overloadAggressively;
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(OverloadedReservedFieldNamingTest.class)
        .addKeepMainRule(TestClass.class)
        .addKeepRules(
            "-keep class " + A.class.getTypeName() + " { boolean a; }",
            overloadAggressively ? "-overloadaggressively" : "")
        .enableNoFieldTypeStrengtheningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::verifyAggressiveOverloading)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(StringUtils.lines("Hello world!"));
  }

  private void verifyAggressiveOverloading(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(A.class);
    assertThat(classSubject, isPresent());

    FieldSubject fieldSubject =
        classSubject.asFoundClassSubject().uniqueFieldWithOriginalName("a", Reference.BOOL);
    assertThat(fieldSubject, isPresentAndNotRenamed());

    FieldSubject helloFieldSubject = classSubject.uniqueFieldWithOriginalName("hello");
    assertThat(helloFieldSubject, isPresent());
    assertEquals(overloadAggressively ? "a" : "b", helloFieldSubject.getFinalName());

    FieldSubject worldFieldSubject = classSubject.uniqueFieldWithOriginalName("world");
    assertThat(worldFieldSubject, isPresent());
    assertEquals(overloadAggressively ? "a" : "c", worldFieldSubject.getFinalName());
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(new A());
    }
  }

  static class A {

    static final boolean a = System.currentTimeMillis() >= 0;
    static final String hello = System.currentTimeMillis() >= 0 ? "Hello" : null;

    @NoFieldTypeStrengthening
    static final Object world = System.currentTimeMillis() >= 0 ? " world!" : null;

    @Override
    public String toString() {
      return a ? (hello + world) : null;
    }
  }
}
