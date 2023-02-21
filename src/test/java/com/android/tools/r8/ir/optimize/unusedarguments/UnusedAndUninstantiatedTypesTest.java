// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.unusedarguments;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class UnusedAndUninstantiatedTypesTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public UnusedAndUninstantiatedTypesTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testUnusedAndUninstantiatedTypes() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(UnusedAndUninstantiatedTypesTest.class)
        .addKeepMainRule(Main.class)
        .addDontObfuscate()
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::assertMethodsAreThere)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(
            "nothing",
            "uninstantiatedThenUnused null",
            "unusedThenUninstantiated null",
            "doubleUninstantiatedThenUnused null and null",
            "doubleUnusedThenUninstantiated null and null",
            "tripleUninstantiatedThenUnused null and null and null",
            "tripleUnusedThenUninstantiated null and null and null",
            "withWideParameters null and null and null");
  }

  private void assertMethodsAreThere(CodeInspector i) {
    List<FoundMethodSubject> methods = i.clazz(Main.class).allMethods();
    assertEquals(9, methods.size());
    for (FoundMethodSubject method : methods) {
      if (!method.getMethod().getReference().name.toString().equals("main")) {
        assertEquals(0, method.getMethod().getReference().getArity());
      }
    }
  }

  @SuppressWarnings("SameParameterValue")
  static class Main {

    public static void main(String[] args) {
      uninstantiatedAndUnused(null);
      uninstantiatedThenUnused(null, new Unused());
      unusedThenUninstantiated(new Unused(), null);
      doubleUninstantiatedThenUnused(null, new Unused(), null, new Unused());
      doubleUnusedThenUninstantiated(new Unused(), null, new Unused(), null);
      tripleUninstantiatedThenUnused(null, new Unused(), new Unused(), null, null, new Unused());
      tripleUnusedThenUninstantiated(new Unused(), null, null, new Unused(), new Unused(), null);
      withWideParameters(0L, 1L, null, null, 2L, 3L, null);
    }

    @NeverInline
    static void uninstantiatedAndUnused(UnInstantiated uninstantiated) {
      System.out.println("nothing");
    }

    @NeverInline
    static void uninstantiatedThenUnused(UnInstantiated uninstantiated, Unused unused) {
      System.out.println("uninstantiatedThenUnused " + uninstantiated);
    }

    @NeverInline
    static void unusedThenUninstantiated(Unused unused, UnInstantiated uninstantiated) {
      System.out.println("unusedThenUninstantiated " + uninstantiated);
    }

    @NeverInline
    static void doubleUninstantiatedThenUnused(
        UnInstantiated uninstantiated1,
        Unused unused1,
        UnInstantiated uninstantiated2,
        Unused unused2) {
      System.out.println(
          "doubleUninstantiatedThenUnused " + uninstantiated1 + " and " + uninstantiated2);
    }

    @NeverInline
    static void doubleUnusedThenUninstantiated(
        Unused unused1,
        UnInstantiated uninstantiated1,
        Unused unused2,
        UnInstantiated uninstantiated2) {
      System.out.println(
          "doubleUnusedThenUninstantiated " + uninstantiated1 + " and " + uninstantiated2);
    }

    @NeverInline
    static void tripleUninstantiatedThenUnused(
        UnInstantiated uninstantiated1,
        Unused unused0,
        Unused unused1,
        UnInstantiated uninstantiated0,
        UnInstantiated uninstantiated2,
        Unused unused2) {
      System.out.println(
          "tripleUninstantiatedThenUnused "
              + uninstantiated1
              + " and "
              + uninstantiated2
              + " and "
              + uninstantiated0);
    }

    @NeverInline
    static void tripleUnusedThenUninstantiated(
        Unused unused1,
        UnInstantiated uninstantiated0,
        UnInstantiated uninstantiated1,
        Unused unused0,
        Unused unused2,
        UnInstantiated uninstantiated2) {
      System.out.println(
          "tripleUnusedThenUninstantiated "
              + uninstantiated1
              + " and "
              + uninstantiated2
              + " and "
              + uninstantiated0);
    }

    @NeverInline
    static void withWideParameters(
        long longUnused0,
        long longUnused1,
        UnInstantiated uninstantiated0,
        UnInstantiated uninstantiated1,
        long longUnused2,
        long longUnused3,
        UnInstantiated uninstantiated2) {
      System.out.println(
          "withWideParameters "
              + uninstantiated1
              + " and "
              + uninstantiated2
              + " and "
              + uninstantiated0);
    }
  }

  private static class UnInstantiated {}

  @NeverClassInline
  private static class Unused {}
}
