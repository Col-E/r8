// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.maindexlist;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoMethodStaticizing;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MainDexUnusedArgumentRewriteWithLensTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDexRuntimes()
        .withApiLevelsEndingAtExcluding(apiLevelWithNativeMultiDexSupport())
        .build();
  }

  public MainDexUnusedArgumentRewriteWithLensTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    Box<Set<String>> mainDex = new Box<>();
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoMethodStaticizingAnnotations()
        .setMinApi(parameters.getApiLevel())
        .addKeepClassRules(Dependency.class)
        .addMainDexRules(
            "-keep class " + A.class.getTypeName() + " { void foo(java.lang.Object,int); }")
        .addKeepMainRule(Main.class)
        .collectMainDexClasses()
        .compile()
        .inspectMainDexClasses(mainDex::set)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A::foo")
        .inspect(
            inspector -> {
              ClassSubject aSubject = inspector.clazz(A.class);
              assertThat(aSubject, isPresent());
              ClassSubject bSubject = inspector.clazz(B.class);
              assertThat(bSubject, isPresent());
              ClassSubject mainSubject = inspector.clazz(Main.class);
              assertThat(mainSubject, isPresentAndNotRenamed());
              MethodSubject mainMethodSubject = mainSubject.uniqueMethodWithOriginalName("main");
              assertThat(mainMethodSubject, isPresentAndNotRenamed());
              assertTrue(
                  mainMethodSubject
                      .streamInstructions()
                      .anyMatch(
                          instructionSubject ->
                              instructionSubject.isNewInstance(Dependency.class.getTypeName())));
              assertEquals(
                  mainDex.get(),
                  ImmutableSet.of(
                      aSubject.getFinalName(),
                      bSubject.getFinalName(),
                      Dependency.class.getTypeName()));
            });
  }

  public static class Dependency {}

  public static class B {

    @NeverInline
    public static void foo(Object obj) {
      if (!(obj instanceof Dependency)) {
        System.out.println("A::foo");
      }
    }
  }

  @NeverClassInline
  public static class A {

    // Will be rewritten because it has an unused argument
    @NeverInline
    @NoMethodStaticizing
    public void foo(Object obj, int argumentUnused) {
      B.foo(obj);
    }
  }

  public static class Main {

    public static void main(String[] args) {
      new A().foo(args.length > 0 ? new Dependency() : new Object(), 42);
    }
  }
}
