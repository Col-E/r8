// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.shaking.Enqueuer.Mode;
import java.util.Iterator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class LibraryMethodOverrideInLambdaMarkingTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public LibraryMethodOverrideInLambdaMarkingTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(LibraryMethodOverrideInLambdaMarkingTest.class)
        .addKeepMainRule(TestClass.class)
        // Keep J since redundant bridge removal will otherwise remove it.
        .addKeepClassAndMembersRules(J.class)
        .addOptionsModification(
            options -> options.testing.enqueuerInspector = this::verifyLibraryOverrideInformation)
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("null", "null");
  }

  private void verifyLibraryOverrideInformation(AppInfoWithLiveness appInfo, Mode mode) {
    DexItemFactory dexItemFactory = appInfo.dexItemFactory();
    verifyIteratorMethodMarkedAsOverridingLibraryMethod(
        appInfo, dexItemFactory.createType(descriptor(I.class)));
    verifyIteratorMethodMarkedAsOverridingLibraryMethod(
        appInfo, dexItemFactory.createType(descriptor(J.class)));
  }

  private void verifyIteratorMethodMarkedAsOverridingLibraryMethod(
      AppInfoWithLiveness appInfo, DexType type) {
    DexProgramClass clazz = appInfo.definitionFor(type).asProgramClass();
    DexEncodedMethod method =
        clazz.lookupVirtualMethod(m -> m.getName().toString().equals("iterator"));
    assertNotNull(method);
    // TODO(b/149976493): Mark library overrides from lambda instances.
    if (parameters.isCfRuntime()) {
      assertTrue(method.isLibraryMethodOverride().isFalse());
    } else {
      assertTrue(method.isLibraryMethodOverride().isTrue());
    }
  }

  static class TestClass {

    public static void onI(I i) {
      System.out.println(i.iterator());
    }

    public static void onJ(J j) {
      System.out.println(j.iterator());
    }

    public static void main(String[] args) {
      Object f = ((I & J) () -> null);
      onI((I) f);
      onJ((J) f);
    }
  }

  @NoVerticalClassMerging
  interface I {

    Iterator<Object> iterator();
  }

  @NoVerticalClassMerging
  interface J extends Iterable<Object> {

    @Override
    Iterator<Object> iterator();
  }
}
