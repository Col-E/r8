// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.maindexlist;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoReturnTypeStrengthening;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CheckCastInstructionSubject;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MainDexDevirtualizerTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public MainDexDevirtualizerTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    assumeTrue(parameters.isCfRuntime() || !parameters.getDexRuntimeVersion().isDalvik());
    runTest(
        testBuilder -> {},
        (inspector, mainDexClasses) -> {
          assertTrue(mainDexClasses.isEmpty());
          // Verify that the call to I.foo in Main.class has been changed to A.foo by checking for a
          // cast.
          ClassSubject clazz = inspector.clazz(Main.class);
          assertThat(clazz, isPresentAndNotRenamed());
          MethodSubject main = clazz.uniqueMethodWithOriginalName("main");
          assertThat(main, isPresent());
          List<CheckCastInstructionSubject> checkCasts =
              main.streamInstructions()
                  .filter(InstructionSubject::isCheckCast)
                  .map(InstructionSubject::asCheckCast)
                  .collect(Collectors.toList());
          assertEquals(1, checkCasts.size());
          ClassSubject a = inspector.clazz(A.class);
          assertThat(a, isPresentAndRenamed());
          assertEquals(
              a.getFinalDescriptor(), checkCasts.get(0).getType().getDescriptor().toString());
        });
  }

  // TODO(b/181858113): This test is likely obsolete once main-dex-list support is removed.
  @Test
  public void testMainDexClasses() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    assumeTrue(parameters.getDexRuntimeVersion().isDalvik());
    runTest(
        r8FullTestBuilder ->
            r8FullTestBuilder
                .addMainDexListClasses(I.class, Provider.class, Main.class)
                .allowDiagnosticWarningMessages(),
        this::inspect);
  }

  @Test
  public void testMainDexTracing() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    assumeTrue(parameters.getDexRuntimeVersion().isDalvik());
    runTest(
        r8FullTestBuilder -> r8FullTestBuilder.addMainDexKeepClassRules(Main.class, I.class),
        this::inspect);
  }

  private void runTest(
      ThrowableConsumer<R8FullTestBuilder> setMainDexConsumer,
      BiConsumer<CodeInspector, List<String>> resultConsumer)
      throws Exception {
    Box<String> mainDexStringList = new Box<>("");
    testForR8(parameters.getBackend())
        .addProgramClasses(I.class, Provider.class, A.class, Main.class)
        .enableNoReturnTypeStrengtheningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .addKeepMainRule(Main.class)
        .addKeepClassRulesWithAllowObfuscation(I.class)
        .setMinApi(parameters)
        .applyIf(
            parameters.isDexRuntime() && parameters.getDexRuntimeVersion().isDalvik(),
            builder ->
                builder.setMainDexListConsumer(ToolHelper.consumeString(mainDexStringList::set)))
        .apply(setMainDexConsumer)
        .run(parameters.getRuntime(), Main.class)
        .apply(
            result ->
                resultConsumer.accept(
                    result.inspector(),
                    mainDexStringList.get().equals("")
                        ? new ArrayList<>()
                        : StringUtils.splitLines(mainDexStringList.get())));
  }

  private void inspect(CodeInspector inspector, List<String> mainDexClasses) {
    assertEquals(4, inspector.allClasses().size());
    assertEquals(3, mainDexClasses.size());
    inspectClassInMainDex(Main.class, inspector, mainDexClasses);
    inspectClassInMainDex(I.class, inspector, mainDexClasses);
    inspectClassInMainDex(Provider.class, inspector, mainDexClasses);
    ClassSubject aClass = inspector.clazz(A.class);
    assertThat(aClass, isPresentAndRenamed());
    assertThat(mainDexClasses, not(hasItem(aClass.getFinalBinaryName() + ".class")));
  }

  private void inspectClassInMainDex(
      Class<?> clazz, CodeInspector inspector, List<String> mainDexClasses) {
    ClassSubject classSubject = inspector.clazz(clazz);
    assertThat(classSubject, isPresent());
    assertThat(mainDexClasses, hasItem(classSubject.getFinalBinaryName() + ".class"));
  }

  @NoVerticalClassMerging
  public interface I {

    @NeverInline
    void foo();
  }

  public static class Provider {
    @NeverInline
    @NoReturnTypeStrengthening
    public static I getImpl() {
      return new A(); // <-- We will call-site optimize getImpl() to always return A.
    }
  }

  @NeverClassInline
  public static class A implements I {

    @Override
    @NeverInline
    public void foo() {
      System.out.println("A::foo");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      // The de-virtualizer will try and rebind from I.foo to A.foo.
      Provider.getImpl().foo();
    }
  }
}
