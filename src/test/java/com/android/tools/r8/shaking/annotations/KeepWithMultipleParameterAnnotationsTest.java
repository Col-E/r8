// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.annotations;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class KeepWithMultipleParameterAnnotationsTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public KeepWithMultipleParameterAnnotationsTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testA() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(KeepWithMultipleParameterAnnotationsTest.class)
        .addKeepRules(
            "-keepclasseswithmembers class * {",
            "  @" + A.class.getTypeName() + " public static void main(java.lang.String[]);",
            "}")
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  @Test
  public void testAB() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(KeepWithMultipleParameterAnnotationsTest.class)
        .addKeepRules(
            "-keepclasseswithmembers class * {",
            "  @"
                + A.class.getTypeName()
                + " @"
                + B.class.getTypeName()
                + " public static void main(java.lang.String[]);",
            "}")
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  @Test
  public void testABC() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(KeepWithMultipleParameterAnnotationsTest.class)
        .addKeepRules(
            "-keepclasseswithmembers class * {",
            "  @"
                + A.class.getTypeName()
                + " @"
                + B.class.getTypeName()
                + " @"
                + C.class.getTypeName()
                + " public static void main(java.lang.String[]);",
            "}")
        .allowUnusedProguardConfigurationRules()
        .setMinApi(parameters)
        .compile()
        .inspect(inspector -> assertTrue(inspector.allClasses().isEmpty()));
  }

  static class TestClass {

    public static void main(@A @B String[] args) {
      System.out.println("Hello world!");
    }
  }

  @Retention(RetentionPolicy.CLASS)
  @Target({ElementType.PARAMETER})
  @interface A {}

  @Retention(RetentionPolicy.CLASS)
  @Target({ElementType.PARAMETER})
  @interface B {}

  @Retention(RetentionPolicy.CLASS)
  @Target({ElementType.PARAMETER})
  @interface C {}
}
