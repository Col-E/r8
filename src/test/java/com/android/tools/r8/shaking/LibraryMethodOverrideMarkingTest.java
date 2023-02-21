// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static com.google.common.base.Predicates.alwaysTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class LibraryMethodOverrideMarkingTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public LibraryMethodOverrideMarkingTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(LibraryMethodOverrideMarkingTest.class)
        .addKeepMainRule(TestClass.class)
        .addOptionsModification(
            options -> options.testing.enqueuerInspector = this::verifyLibraryOverrideInformation)
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile();
  }

  private void verifyLibraryOverrideInformation(AppInfoWithLiveness appInfo, Enqueuer.Mode mode) {
    DexItemFactory dexItemFactory = appInfo.dexItemFactory();
    verifySingleVirtualMethodMarkedAsOverridingLibraryMethod(
        appInfo, dexItemFactory.createType(descriptor(A.class)));
    verifySingleVirtualMethodMarkedAsOverridingLibraryMethod(
        appInfo, dexItemFactory.createType(descriptor(B.class)));
  }

  private void verifySingleVirtualMethodMarkedAsOverridingLibraryMethod(
      AppInfoWithLiveness appInfo, DexType type) {
    DexProgramClass clazz = appInfo.definitionFor(type).asProgramClass();
    assertEquals(1, clazz.getMethodCollection().numberOfVirtualMethods());
    DexEncodedMethod method = clazz.lookupVirtualMethod(alwaysTrue());
    assertTrue(method.isLibraryMethodOverride().isTrue());
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(new C());
    }
  }

  @NoVerticalClassMerging
  static class A {

    @Override
    public String toString() {
      return super.toString();
    }
  }

  @NoVerticalClassMerging
  static class B extends A {

    @Override
    public String toString() {
      return super.toString();
    }
  }

  static class C extends B {}
}
