// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.annotations;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RetentionPolicyTest extends TestBase {

  private static final Collection<Class<?>> CLASSES =
      ImmutableList.of(ClassRetained.class, SourceRetained.class, RuntimeRetained.class, A.class);

  private static final String EXPECTED =
      StringUtils.lines("@" + RuntimeRetained.class.getName() + "()");

  @Parameters(name = "{0}, intermediate:{1}")
  public static List<Object> data() {
    return getTestParameters().withAllRuntimes().build().stream()
        .flatMap(
            parameters -> {
              if (parameters.isCfRuntime()) {
                return Stream.of((Object) new Object[] {parameters, true});
              }
              return Stream.of(new Object[] {parameters, true}, new Object[] {parameters, false});
            })
        .collect(Collectors.toList());
  }

  private final TestParameters parameters;
  private final boolean intermediate;

  public RetentionPolicyTest(TestParameters parameters, boolean intermediate) {
    this.parameters = parameters;
    this.intermediate = intermediate;
  }

  @Retention(RetentionPolicy.CLASS)
  @interface ClassRetained {}

  @Retention(RetentionPolicy.SOURCE)
  @interface SourceRetained {}

  @Retention(RetentionPolicy.RUNTIME)
  @interface RuntimeRetained {}

  @ClassRetained
  @SourceRetained
  @RuntimeRetained
  public static class A {

    public static void main(String[] args) {
      for (Annotation annotation : A.class.getAnnotations()) {
        System.out.println(annotation);
      }
    }
  }

  @Test
  public void test() throws Exception {
    if (parameters.isCfRuntime()) {
      assertTrue(intermediate);
      checkAnnotations(
          testForJvm()
              .addProgramClasses(CLASSES)
              .run(parameters.getRuntime(), A.class)
              .assertSuccessWithOutput(EXPECTED)
              .inspector(),
          intermediate);
    } else {
      D8TestCompileResult compile =
          testForD8()
              .setMinApi(parameters.getRuntime())
              .setIntermediate(intermediate)
              .addProgramClasses(CLASSES)
              .compile();
      checkAnnotations(
          compile
              .run(parameters.getRuntime(), A.class)
              .assertSuccessWithOutput(EXPECTED)
              .inspector(),
          intermediate);
      // If the first build was an intermediate, re-compile and check the final output.
      if (intermediate) {
        checkAnnotations(
            testForD8()
                .setMinApi(parameters.getRuntime())
                .addProgramFiles(compile.writeToZip())
                .run(parameters.getRuntime(), A.class)
                .inspector(),
            false);
      }
    }
  }

  private static void checkAnnotations(CodeInspector inspector, boolean isClassRetained) {
    ClassSubject clazz = inspector.clazz(A.class);
    assertThat(clazz, isPresent());
    // Source retained annotations are always gone, even in the CF inputs.
    assertFalse(clazz.annotation(SourceRetained.class.getName()).isPresent());
    // Class retained annotations are present in CF and in intermediate builds.
    assertEquals(isClassRetained, clazz.annotation(ClassRetained.class.getName()).isPresent());
    // Runtime retained annotations are present in all.
    assertTrue(clazz.annotation(RuntimeRetained.class.getName()).isPresent());
  }
}
