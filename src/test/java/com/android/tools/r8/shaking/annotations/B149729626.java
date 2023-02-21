// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.annotations;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class B149729626 extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection params() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public B149729626(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8WithKeepClassMembersRule() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(B149729626.class)
        .addKeepMainRule(TestClass.class)
        .addKeepRules(
            "-keepclassmembers @" + Marker.class.getTypeName() + " class * {",
            "  <init>(...);",
            "}")
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccess();
  }

  @Test
  public void testR8WithIfRule() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(B149729626.class)
        .addKeepMainRule(TestClass.class)
        .addKeepRules(
            "-if @" + Marker.class.getTypeName() + " class *",
            "-keep class <1> {",
            "  <init>(...);",
            "}")
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccess();
  }

  @Test
  public void testCompat() throws Exception {
    testForR8Compat(parameters.getBackend())
        .addInnerClasses(B149729626.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccess();
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject markedClassSubject = inspector.clazz(Marked.class);
    assertThat(markedClassSubject, isPresent());
    assertThat(markedClassSubject.init(), isPresent());
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(Marked.class);
    }
  }

  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  @interface Marker {}

  @Marker
  static class Marked {}
}
