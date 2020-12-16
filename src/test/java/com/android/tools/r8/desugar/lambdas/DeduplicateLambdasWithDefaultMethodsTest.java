// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.lambdas;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.google.common.collect.ImmutableSet;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DeduplicateLambdasWithDefaultMethodsTest extends TestBase {

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public DeduplicateLambdasWithDefaultMethodsTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void test() throws Exception {
    assertEquals(
        ImmutableSet.of(
            Reference.classFromClass(I.class),
            Reference.classFromClass(TestClass.class),
            SyntheticItemsTestUtils.syntheticCompanionClass(I.class),
            SyntheticItemsTestUtils.syntheticLambdaClass(TestClass.class, 0)),
        testForD8(Backend.CF)
            .addInnerClasses(getClass())
            .setIntermediate(true)
            .setMinApi(AndroidApiLevel.B)
            .compile()
            .inspector()
            .allClasses()
            .stream()
            .map(FoundClassSubject::getFinalReference)
            .collect(Collectors.toSet()));
  }

  interface I {
    void foo();

    // Lots of methods which may cause the ordering of methods on a class to change between builds.
    default void a() {
      System.out.print("a");
    }

    default void b() {
      System.out.print("b");
    }

    default void c() {
      System.out.print("c");
    }

    default void x() {
      System.out.print("x");
    }

    default void y() {
      System.out.print("y");
    }

    default void z() {
      System.out.print("z");
    }
  }

  public static class TestClass {
    private static void foo() {
      System.out.println("foo");
    }

    private static void pI(I i) {
      i.a();
      i.b();
      i.c();
      i.x();
      i.y();
      i.z();
      i.foo();
    }

    public static void main(String[] args) {
      // Duplication of the same lambda, each of which should become a shared instance.
      pI(TestClass::foo);
      pI(TestClass::foo);
      pI(TestClass::foo);
      pI(TestClass::foo);
      pI(TestClass::foo);
      pI(TestClass::foo);
    }
  }
}
