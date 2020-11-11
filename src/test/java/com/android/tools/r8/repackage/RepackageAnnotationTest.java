// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RepackageAnnotationTest extends RepackageTestBase {

  private final String EXPECTED = "Hello World";

  public RepackageAnnotationTest(
      String flattenPackageHierarchyOrRepackageClasses, TestParameters parameters) {
    super(flattenPackageHierarchyOrRepackageClasses, parameters);
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(Main.class, Annotation.class, A.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, A.class, Annotation.class)
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .addKeepRuntimeVisibleAnnotations()
        .addKeepRules(
            "-keep,allowobfuscation @interface " + Annotation.class.getTypeName() + " {",
            "  *;",
            "}")
        .apply(this::configureRepackaging)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  public static class Main {

    public static void main(String[] args) {
      System.out.println(new A().getAnnotationValues());
    }
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.TYPE})
  public @interface Annotation {

    String f1();

    String f2();
  }

  @Annotation(f1 = "Hello", f2 = "World")
  @NeverClassInline
  public static class A {

    @NeverInline
    public String getAnnotationValues() {
      Annotation annotation = A.class.getAnnotation(Annotation.class);
      if (annotation == null) {
        return null;
      }
      return annotation.f1() + " " + annotation.f2();
    }
  }
}
