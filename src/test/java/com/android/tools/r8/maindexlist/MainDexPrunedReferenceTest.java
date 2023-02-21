// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.maindexlist;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MainDexPrunedReferenceTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public MainDexPrunedReferenceTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    assumeFalse(parameters.getDexRuntimeVersion().isDalvik());
    testMainDex(builder -> {}, Assert::assertNull);
  }

  // TODO(b/181858113): This test is likely obsolete once main-dex-list support is removed.
  @Test
  public void testMainDexClassesList() throws Exception {
    assumeTrue(parameters.getDexRuntimeVersion().isDalvik());
    testMainDex(
        builder -> builder.addMainDexListClasses(Main.class).allowDiagnosticWarningMessages(),
        mainDexClasses -> assertEquals(ImmutableSet.of(Main.class.getTypeName()), mainDexClasses));
  }

  @Test
  public void testMainDexTracing() throws Exception {
    assumeTrue(parameters.getDexRuntimeVersion().isDalvik());
    testMainDex(
        builder ->
            builder.addMainDexRules(
                "-keep class " + Main.class.getTypeName() + " { public static void notMain(); }"),
        mainDexClasses -> assertEquals(ImmutableSet.of(Main.class.getTypeName()), mainDexClasses));
  }

  private void testMainDex(
      ThrowableConsumer<R8FullTestBuilder> configureMainDex,
      Consumer<Set<String>> mainDexListConsumer)
      throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, Outside.class)
        .enableInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .addKeepMainRule(Main.class)
        .addKeepClassRules(Outside.class)
        .setMinApi(parameters)
        .apply(configureMainDex)
        .applyIf(
            parameters.getDexRuntimeVersion().isDalvik(),
            TestCompilerBuilder::collectMainDexClasses)
        .compile()
        .inspectMainDexClasses(mainDexListConsumer)
        .run(parameters.getRuntime(), Main.class)
        .inspect(
            inspector -> {
              ClassSubject mainClassSubject = inspector.clazz(Main.class);
              assertThat(mainClassSubject, isPresent());
              ClassSubject outsideClassSubject = inspector.clazz(Outside.class);
              assertThat(outsideClassSubject, isPresent());
            })
        .assertSuccessWithOutputLines("Hello World");
  }

  @NoHorizontalClassMerging
  public static class Outside {

    @NeverInline
    public static void foo() {
      System.out.println("Outside::foo");
    }
  }

  @NoHorizontalClassMerging
  public static class Main {

    public static void main(String[] args) {
      int val = 0;
      if (val != 0) {
        notMain();
      }
      System.out.println("Hello World");
    }

    // If we trace before second round of enqueueing, we do not observe notMain being pruned.
    public static void notMain() {
      Outside.foo();
    }
  }
}
