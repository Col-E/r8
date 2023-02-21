// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution.interfacediamonds;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestAppViewBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class TwoDefaultMethodsWithoutTopTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public TwoDefaultMethodsWithoutTopTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private static final List<Class<?>> CLASSES =
      ImmutableList.of(I.class, J.class, A.class, Main.class);

  @Test
  public void testResolution() throws Exception {
    // The resolution is runtime independent, so just run it on the default CF VM.
    assumeTrue(parameters.isOrSimulateNoneRuntime());
    for (AndroidApiLevel minApi :
        ImmutableList.of(AndroidApiLevel.B, apiLevelWithDefaultInterfaceMethodsSupport())) {
      AppInfoWithLiveness appInfo =
          TestAppViewBuilder.builder()
              .addProgramClasses(CLASSES)
              .addProgramClassFileData(transformB())
              .addLibraryFiles(parameters.getDefaultRuntimeLibrary())
              .addKeepMainRule(Main.class)
              .setMinApi(minApi)
              .buildWithLiveness()
              .appInfo();
      DexMethod method = buildNullaryVoidMethod(B.class, "f", appInfo.dexItemFactory());
      MethodResolutionResult resolutionResult = appInfo.resolveMethodOnClassHolderLegacy(method);
      if (minApi.isLessThan(apiLevelWithDefaultInterfaceMethodsSupport())) {
        // When desugaring a forwarding method throwing ICCE is inserted.
        // Check that the resolved method throws such an exception.
        assertTrue(
            resolutionResult
                .asSingleResolution()
                .getResolvedMethod()
                .getCode()
                .asCfCode()
                .getInstructions()
                .stream()
                .anyMatch(
                    i ->
                        i.isTypeInstruction()
                            && i.asTypeInstruction().getType()
                                == appInfo.dexItemFactory().icceType));
      } else {
        // When not desugaring resolution should fail. Check the failure dependencies are the two
        // default methods in conflict.
        Set<String> holders = new HashSet<>();
        Set<String> failedTypes = new HashSet<>();
        resolutionResult
            .asFailedResolution()
            .forEachFailureDependency(
                type -> failedTypes.add(type.toSourceString()),
                m -> holders.add(m.getHolderType().toSourceString()));
        assertEquals(holders, failedTypes);
        assertEquals(ImmutableSet.of(I.class.getTypeName(), J.class.getTypeName()), holders);
      }
    }
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(CLASSES)
        .addProgramClassFileData(transformB())
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatMatches(containsString("IncompatibleClassChangeError"));
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(CLASSES)
        .addProgramClassFileData(transformB())
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatMatches(containsString("IncompatibleClassChangeError"));
  }

  public interface I {
    default void f() {
      System.out.println("I::f");
    }
  }

  public interface J {
    default void f() {
      System.out.println("J::f");
    }
  }

  public static class A implements I {}

  public static class B extends A /* implements J via ASM */ {
    // Intentionally empty.
  }

  static class Main {
    public static void main(String[] args) {
      new B().f();
    }
  }

  private static byte[] transformB() throws Exception {
    return transformer(B.class).setImplements(J.class).transform();
  }
}
