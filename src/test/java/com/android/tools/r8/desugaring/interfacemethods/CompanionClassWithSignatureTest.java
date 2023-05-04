// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugaring.interfacemethods;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.graph.GenericSignature.ClassSignature;
import com.android.tools.r8.utils.StringUtils;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class CompanionClassWithSignatureTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().enableApiLevelsForCf().build();
  }

  public CompanionClassWithSignatureTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  public boolean isDesugaring() {
    return parameters.getApiLevel().isLessThan(apiLevelWithDefaultInterfaceMethodsSupport());
  }

  @Test
  public void testD8() throws Exception {
    assumeFalse(
        "Art 7 crashes when resolving the default method on I.",
        parameters.isDexRuntimeVersion(Version.V7_0_0) && !isDesugaring());
    boolean resolvedBug280356274 = true;
    String expected = StringUtils.lines(resolvedBug280356274 && isDesugaring() ? "[]" : "[T]");
    testForD8(parameters.getBackend())
        .setMinApi(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(expected)
        .inspect(
            inspector -> {
              // Interface I must retain its signature.
              assertTrue(
                  inspector.clazz(I.class).getDexProgramClass().getClassSignature().hasSignature());
              // If desugaring the companion class should not have a signature.
              if (isDesugaring()) {
                ClassSignature signature =
                    inspector.companionClassFor(I.class).getDexProgramClass().getClassSignature();
                assertTrue(
                    "Expected no signature, got: " + signature.toString(),
                    signature.hasNoSignature());
              }
            });
  }

  interface J {}

  interface I<T extends I> extends J {
    T self();

    default T foo() {
      Object o = new Object() {};
      // The class constant here is either the interface I or its companion class.
      // The desugared companion class is *not* I, thus its type parameters are not the same as
      // those of I.
      Class<?> interfaceOrCompanion = o.getClass().getEnclosingMethod().getDeclaringClass();
      System.out.println(Arrays.toString(interfaceOrCompanion.getTypeParameters()));
      return self();
    }
  }

  static class A implements I<A> {

    @Override
    public A self() {
      return this;
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      new A().foo();
    }
  }
}
