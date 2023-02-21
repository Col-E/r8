// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.peephole.suffixsharing;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Regression test for b/129410384. */
@RunWith(Parameterized.class)
public class IdenticalBlockSuffixSharingWithArrayTypesTest extends TestBase {

  static class ClassTestParameter {

    private final Class<?> value;

    ClassTestParameter(Class<?> value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return value.getSimpleName();
    }
  }

  private final Class<?> clazz;
  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{1}, test: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        ImmutableList.of(
            new ClassTestParameter(ArrayPutTestClass.class),
            new ClassTestParameter(InstancePutTestClass.class),
            new ClassTestParameter(InvokeTestClass.class),
            new ClassTestParameter(StaticPutTestClass.class)),
        getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public IdenticalBlockSuffixSharingWithArrayTypesTest(
      ClassTestParameter clazz, TestParameters parameters) {
    this.clazz = clazz.value;
    this.parameters = parameters;
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue("Only run D8 for DEX backend", parameters.isDexRuntime());

    String expectedOutput = StringUtils.lines("42");
    testForD8()
        .addInnerClasses(IdenticalBlockSuffixSharingWithArrayTypesTest.class)
        .setMinApi(parameters)
        .compile()
        .inspect(this::verifyInstructionCount)
        .run(parameters.getRuntime(), clazz)
        .assertSuccessWithOutput(expectedOutput);
  }

  @Test
  public void testR8() throws Exception {
    String expectedOutput = StringUtils.lines("42");
    testForR8(parameters.getBackend())
        .addInnerClasses(IdenticalBlockSuffixSharingWithArrayTypesTest.class)
        .addKeepMainRule(clazz)
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::verifyInstructionCount)
        .run(parameters.getRuntime(), clazz)
        .assertSuccessWithOutput(expectedOutput);
  }

  private void verifyInstructionCount(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(clazz);
    assertThat(classSubject, isPresent());

    MethodSubject methodSubject = classSubject.mainMethod();
    assertThat(methodSubject, isPresent());

    if (clazz == ArrayPutTestClass.class) {
      assertEquals(
          4, methodSubject.streamInstructions().filter(InstructionSubject::isArrayPut).count());
    } else if (clazz == InstancePutTestClass.class) {
      assertEquals(
          4, methodSubject.streamInstructions().filter(InstructionSubject::isInstancePut).count());
    } else if (clazz == InvokeTestClass.class) {
      assertEquals(
          4, methodSubject.streamInstructions().filter(InstructionSubject::isInvokeStatic).count());
    } else if (clazz == StaticPutTestClass.class) {
      assertEquals(
          4, methodSubject.streamInstructions().filter(InstructionSubject::isStaticPut).count());
    } else {
      fail();
    }
  }

  static class ArrayPutTestClass {

    private static final boolean unknown = System.currentTimeMillis() >= 0;

    public static void main(String[] args) {
      I[][] classArray = new I[1][];
      if (unknown) {
        classArray[0] = new A[4];
      } else {
        classArray[0] = new B[4];
      }
      // <- the array-put instructions above are candidates for identical block suffix sharing.
      System.out.print(classArray[0].length);

      I[][] interfaceArray = new I[1][];
      if (unknown) {
        interfaceArray[0] = new J[2];
      } else {
        interfaceArray[0] = new K[2];
      }
      // <- the array-put instructions above are candidates for identical block suffix sharing.
      System.out.println(interfaceArray[0].length);
    }
  }

  static class InstancePutTestClass {

    @NeverClassInline
    static class Box {

      I[] value;
    }

    private static final boolean unknown = System.currentTimeMillis() >= 0;

    public static void main(String[] args) {
      Box classBox = new Box();
      if (unknown) {
        classBox.value = new A[4];
      } else {
        classBox.value = new B[4];
      }
      // <- the instance-put instructions above are candidates for identical block suffix sharing.
      System.out.print(classBox.value.length);

      Box interfaceBox = new Box();
      if (unknown) {
        interfaceBox.value = new J[2];
      } else {
        interfaceBox.value = new K[2];
      }
      // <- the instance-put instructions above are candidates for identical block suffix sharing.
      System.out.println(interfaceBox.value.length);
    }
  }

  static class InvokeTestClass {

    private static final boolean unknown = System.currentTimeMillis() >= 0;

    public static void main(String[] args) {
      if (unknown) {
        foo(new A[4]);
      } else {
        foo(new B[4]);
      }
      // <- the invoke instructions above are candidates for identical block suffix sharing.

      if (unknown) {
        foo(new J[2]);
      } else {
        foo(new K[2]);
      }
      // <- the invoke instructions above are candidates for identical block suffix sharing.
      System.out.println();
    }

    @NeverInline
    private static void foo(I[] array) {
      System.out.print(array.length);
    }
  }

  static class StaticPutTestClass {

    static class Box {

      static I[] value;
    }

    private static final boolean unknown = System.currentTimeMillis() >= 0;

    public static void main(String[] args) {
      if (unknown) {
        Box.value = new A[4];
      } else {
        Box.value = new B[4];
      }
      // <- the static-put instructions above are candidates for identical block suffix sharing.
      System.out.print(Box.value.length);

      if (unknown) {
        Box.value = new J[2];
      } else {
        Box.value = new K[2];
      }
      // <- the static-put instructions above are candidates for identical block suffix sharing.
      System.out.println(Box.value.length);
    }
  }

  interface I {}

  interface J extends I {}

  interface K extends I {}

  @NoHorizontalClassMerging
  class A implements I {}

  @NoHorizontalClassMerging
  class B implements I {}
}
