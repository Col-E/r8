// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import java.util.AbstractList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class LibraryMethodOverrideInInterfaceMarkingTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public LibraryMethodOverrideInInterfaceMarkingTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(LibraryMethodOverrideInInterfaceMarkingTest.class)
        .addKeepMainRule(TestClass.class)
        .addOptionsModification(
            options -> options.testing.enqueuerInspector = this::verifyLibraryOverrideInformation)
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("true", "true");
  }

  private void verifyLibraryOverrideInformation(AppInfoWithLiveness appInfo, Enqueuer.Mode mode) {
    DexItemFactory dexItemFactory = appInfo.dexItemFactory();
    verifyIsEmptyMarkedAsOverridingLibraryMethod(
        appInfo, dexItemFactory.createType(descriptor(A.class)));
    verifyIsEmptyMarkedAsOverridingLibraryMethod(
        appInfo, dexItemFactory.createType(descriptor(I.class)));
  }

  private void verifyIsEmptyMarkedAsOverridingLibraryMethod(
      AppInfoWithLiveness appInfo, DexType type) {
    DexProgramClass clazz = appInfo.definitionFor(type).asProgramClass();
    DexEncodedMethod method =
        clazz.lookupVirtualMethod(m -> m.getReference().name.toString().equals("isEmpty"));
    assertTrue(method.isLibraryMethodOverride().isTrue());
  }

  static class TestClass {

    static void onA(A a) {
      System.out.println(a.isEmpty());
    }

    static void onI(I i) {
      System.out.println(i.isEmpty());
    }

    public static void main(String[] args) {
      Object object = args.length == 42 ? null : new B();
      onA((A) object);
      onI((I) object);
    }
  }

  @NoVerticalClassMerging
  abstract static class A extends AbstractList<Object> {

    @Override
    public boolean isEmpty() {
      return true;
    }

    @Override
    public int size() {
      return 0;
    }

    @Override
    public A get(int index) {
      return null;
    }
  }

  @NoVerticalClassMerging
  interface I {

    boolean isEmpty();
  }

  @NoVerticalClassMerging
  static class B extends A implements I {
    // Intentionally empty.
  }
}
