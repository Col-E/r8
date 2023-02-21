// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.ifrule;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IfRuleWithKeepAllTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public IfRuleWithKeepAllTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8WithKeepOnAllMembers() throws Exception {
    runTest("-if class ** { *; }");
  }

  @Test
  public void testR8WithKeepOnClass() throws Exception {
    runTest("-if class **");
  }

  private void runTest(String precondition) throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(DirectlyKept.class, KeptByIf.class)
        .addKeepClassAndMembersRules(DirectlyKept.class)
        .addKeepRules(precondition + "\n-keep class " + KeptByIf.class.getTypeName() + " { *; }")
        .setMinApi(parameters)
        .compile()
        .inspect(
            codeInspector -> {
              assertThat(codeInspector.clazz(DirectlyKept.class), isPresent());
              final ClassSubject keptByIf = codeInspector.clazz(KeptByIf.class);
              assertThat(keptByIf, isPresent());
              assertEquals(1, keptByIf.allMethods().size());
            })
        .addRunClasspathFiles(buildOnDexRuntime(parameters, Main.class))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello World!");
  }

  @KeptByIf()
  public static class DirectlyKept {
    final int foo = 42;
  }

  @Retention(RetentionPolicy.RUNTIME)
  public @interface KeptByIf {
    String key() default "";
  }

  public static class Main implements KeptByIf {

    public static void main(String[] args) {
      runKeptByIf(new Main());
    }

    public static void runKeptByIf(KeptByIf keptByIf) {
      System.out.println(keptByIf.key());
    }

    @Override
    public String key() {
      return "Hello World!";
    }

    @Override
    public Class<? extends Annotation> annotationType() {
      return null;
    }
  }
}
