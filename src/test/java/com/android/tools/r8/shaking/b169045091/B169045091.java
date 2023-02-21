// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.b169045091;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.b169045091.testclasses.HelloGreeter;
import com.android.tools.r8.shaking.b169045091.testclasses.WorldGreeterBase;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class B169045091 extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public B169045091(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(getProgramClasses())
        .addProgramClassFileData(getWorldGreeterClassFileData())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(getProgramClasses())
        .addProgramClassFileData(getWorldGreeterClassFileData())
        .addKeepMainRule(TestClass.class)
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .addDontObfuscate()
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  private List<Class<?>> getProgramClasses() {
    return ImmutableList.of(
        TestClass.class, HelloGreeter.class, HelloGreeterBase.class, WorldGreeterBase.class);
  }

  private byte[] getWorldGreeterClassFileData() throws Exception {
    return transformer(WorldGreeter.class)
        .removeMethods(
            (int access, String name, String descriptor, String signature, String[] exceptions) ->
                name.equals("world"))
        .transform();
  }

  @Test
  public void testAccessibility() throws Exception {
    assumeTrue(parameters.isOrSimulateNoneRuntime());
    AppView<AppInfoWithLiveness> appView =
        computeAppViewWithLiveness(
            buildClassesWithTestingAnnotations(getProgramClasses())
                .addClassProgramData(getWorldGreeterClassFileData())
                .addLibraryFile(parameters.getDefaultRuntimeLibrary())
                .build(),
            TestClass.class);
    AppInfoWithLiveness appInfo = appView.appInfo();
    DexItemFactory dexItemFactory = appView.dexItemFactory();

    DexProgramClass context =
        appView
            .contextIndependentDefinitionFor(buildType(TestClass.class, dexItemFactory))
            .asProgramClass();

    // Test that HelloGreeter.greet() is accessible to TestClass.
    DexMethod helloReference = buildNullaryVoidMethod(HelloGreeter.class, "hello", dexItemFactory);
    assertTrue(
        appInfo
            .resolveMethodOnClassHolderLegacy(helloReference)
            .isAccessibleFrom(context, appView)
            .isTrue());

    // Test that WorldGreeter.greet() is inaccessible to TestClass.
    DexMethod worldReference = buildNullaryVoidMethod(WorldGreeter.class, "world", dexItemFactory);
    assertTrue(
        appInfo
            .resolveMethodOnClassHolderLegacy(worldReference)
            .isAccessibleFrom(context, appView)
            .isFalse());
  }

  public static class TestClass {

    public static void main(String[] args) {
      // HelloGreeterBase.greet() is accessible to TestClass because they are in the same package.
      new HelloGreeter().hello();

      try {
        // WorldGreeterBase.world() is inaccessible to TestClass.
        new WorldGreeter().world();
        throw new RuntimeException();
      } catch (IllegalAccessError e) {
        System.out.println(" world!");
      }
    }
  }

  @NoVerticalClassMerging
  public static class HelloGreeterBase {
    @NeverInline
    protected void hello() {
      System.out.print("Hello");
    }
  }

  @NeverClassInline
  public static class WorldGreeter extends WorldGreeterBase {

    // Removed by a transformer.
    @Override
    public void world() {
      super.world();
    }
  }
}
