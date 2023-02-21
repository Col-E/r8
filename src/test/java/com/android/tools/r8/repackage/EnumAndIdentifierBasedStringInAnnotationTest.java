// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestParameters;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class EnumAndIdentifierBasedStringInAnnotationTest extends RepackageTestBase {

  public EnumAndIdentifierBasedStringInAnnotationTest(
      String flattenPackageHierarchyOrRepackageClasses, TestParameters parameters) {
    super(flattenPackageHierarchyOrRepackageClasses, parameters);
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addInnerClasses(EnumAndIdentifierBasedStringInAnnotationTest.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("TEST_ONE");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(EnumAndIdentifierBasedStringInAnnotationTest.class)
        .addKeepMainRule(Main.class)
        .addKeepClassAndMembersRules(MyAnnotation.class)
        .addKeepClassAndMembersRulesWithAllowObfuscation(Enum.class)
        .addKeepRules("-keepclassmembers,allowshrinking class ** { *; }")
        .setMinApi(parameters)
        .addKeepRuntimeVisibleAnnotations()
        .apply(this::configureRepackaging)
        .compile()
        .inspect(inspector -> assertThat(inspector.clazz(Enum.class), isPresentAndRenamed()))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("TEST_ONE");
  }

  public enum Enum {
    TEST_ONE,
    TEST_TWO
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.TYPE})
  public @interface MyAnnotation {

    Enum value() default Enum.TEST_TWO;
  }

  @MyAnnotation(value = Enum.TEST_ONE)
  public static class Main {

    public static void main(String[] args) {
      System.out.println(Main.class.getAnnotation(MyAnnotation.class).value());
    }
  }
}
