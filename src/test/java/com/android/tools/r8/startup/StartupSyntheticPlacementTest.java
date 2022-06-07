// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.startup;

import static com.android.tools.r8.startup.utils.StartupTestingMatchers.isEqualToClassDataLayout;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.startup.utils.MixedSectionLayoutInspector;
import com.android.tools.r8.startup.utils.StartupTestingUtils;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StartupSyntheticPlacementTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean enableMinimalStartupDex;

  @Parameter(2)
  public boolean useLambda;

  @Parameters(name = "{0}, minimal startup dex: {1}, use lambda: {2}")
  public static List<Object[]> data() {
    return buildParameters(
        // N so that java.util.function.Consumer is present.
        getTestParameters().withDexRuntimes().withApiLevel(AndroidApiLevel.N).build(),
        BooleanUtils.values(),
        BooleanUtils.values());
  }

  @Test
  public void test() throws Exception {
    List<ClassReference> startupList = new ArrayList<>();
    testForD8(parameters.getBackend())
        .addInnerClasses(getClass())
        .apply(StartupTestingUtils.enableStartupInstrumentation(parameters))
        .setMinApi(parameters.getApiLevel())
        .compile()
        .addRunClasspathFiles(StartupTestingUtils.getAndroidUtilLog(temp))
        .run(parameters.getRuntime(), Main.class, Boolean.toString(useLambda))
        .apply(StartupTestingUtils.removeStartupClassesFromStdout(startupList::add))
        .assertSuccessWithOutputLines(getExpectedOutput());
    assertEquals(getExpectedStartupList(), startupList);

    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepClassAndMembersRules(A.class, B.class, C.class)
        .addOptionsModification(
            options -> {
              options.getStartupOptions().setEnableMinimalStartupDex(enableMinimalStartupDex);
              options
                  .getTestingOptions()
                  .setMixedSectionLayoutStrategyInspector(getMixedSectionLayoutInspector());
            })
        .apply(testBuilder -> StartupTestingUtils.setStartupConfiguration(testBuilder, startupList))
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspectMultiDex(this::inspectPrimaryDex, this::inspectSecondaryDex)
        .run(parameters.getRuntime(), Main.class, Boolean.toString(useLambda))
        .assertSuccessWithOutputLines(getExpectedOutput());
  }

  private List<String> getExpectedOutput() {
    return ImmutableList.of("A", "B", "C");
  }

  private List<ClassReference> getExpectedStartupList() {
    // TODO(b/235181186): We should include a "synthetic-of" B in the startup list.
    return ImmutableList.of(
        Reference.classFromClass(Main.class),
        Reference.classFromClass(A.class),
        Reference.classFromClass(B.class),
        Reference.classFromClass(C.class));
  }

  private List<ClassReference> getExpectedClassDataLayout(int virtualFile) {
    // TODO(b/235181186): When the synthetic lambda is used, there should be two virtual files with
    //  minimal startup dex. Without minimal startup dex, the synthetic lambda should be after all
    //  startup classes in the layout.
    assertEquals(0, virtualFile);
    return ImmutableList.of(
        Reference.classFromClass(Main.class),
        Reference.classFromClass(A.class),
        Reference.classFromClass(B.class),
        getSyntheticLambdaClassReference(),
        Reference.classFromClass(C.class));
  }

  private MixedSectionLayoutInspector getMixedSectionLayoutInspector() {
    return new MixedSectionLayoutInspector() {
      @Override
      public void inspectClassDataLayout(int virtualFile, Collection<DexProgramClass> layout) {
        assertThat(layout, isEqualToClassDataLayout(getExpectedClassDataLayout(virtualFile)));
      }
    };
  }

  private void inspectPrimaryDex(CodeInspector inspector) {
    assertThat(inspector.clazz(Main.class), isPresent());
    assertThat(inspector.clazz(A.class), isPresent());
    assertThat(inspector.clazz(B.class), isPresent());
    assertThat(inspector.clazz(C.class), isPresent());
    // TODO(b/235181186): Synthetic lambda should be in classes2.dex when not used and minimal
    //  startup dex is enabled.
    assertThat(inspector.clazz(getSyntheticLambdaClassReference()), isPresent());
  }

  private void inspectSecondaryDex(CodeInspector inspector) {
    // TODO(b/235181186): Synthetic lambda should be in classes2.dex when not used and minimal
    //  startup dex is enabled.
    assertTrue(inspector.allClasses().isEmpty());
  }

  private static ClassReference getSyntheticLambdaClassReference() {
    return SyntheticItemsTestUtils.syntheticLambdaClass(B.class, 0);
  }

  static class Main {

    public static void main(String[] args) {
      boolean useLambda = args.length > 0 && args[0].equals("true");
      A.a();
      B.b(useLambda);
      C.c();
    }
  }

  static class A {

    static void a() {
      System.out.println("A");
    }
  }

  static class B {

    static void b(boolean useLambda) {
      String message = System.currentTimeMillis() > 0 ? "B" : null;
      if (useLambda) {
        Consumer<Object> consumer = obj -> {};
        consumer.accept(consumer);
      }
      System.out.println(message);
    }
  }

  static class C {

    static void c() {
      System.out.println("C");
    }
  }
}
