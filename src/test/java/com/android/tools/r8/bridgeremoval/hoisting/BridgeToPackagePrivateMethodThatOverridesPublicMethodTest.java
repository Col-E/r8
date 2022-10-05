// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.bridgeremoval.hoisting;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.notIf;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class BridgeToPackagePrivateMethodThatOverridesPublicMethodTest extends TestBase {

  private static final String TRANSFORMED_B_DESCRIPTOR = "LB;";

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean removeBridgeMethodFromA;

  @Parameters(name = "{0}, remove A.bridge(): {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(Base.class)
        .addProgramClassFileData(getProgramClassFileData())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(getExpectedOutput());
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Base.class)
        .addProgramClassFileData(getProgramClassFileData())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(
            inspector -> {
              // Inspect Base.
              ClassSubject baseClassSubject = inspector.clazz(Base.class);
              assertThat(baseClassSubject, isPresent());

              MethodSubject bridgeOnBaseMethodSubject =
                  baseClassSubject.uniqueMethodWithOriginalName("bridge");
              assertThat(bridgeOnBaseMethodSubject, isPresent());

              // Inspect A. This should have the bridge method unless it is removed by the
              // transformation.
              ClassSubject aClassSubject = inspector.clazz(A.class);
              assertThat(aClassSubject, isPresent());

              MethodSubject bridgeOnAMethodSubject =
                  aClassSubject.uniqueMethodWithOriginalName("bridge");
              assertThat(bridgeOnAMethodSubject, notIf(isPresent(), removeBridgeMethodFromA));

              // Inspect B. This should not have a bridge method, as the bridge in C is not eligible
              // for hoisting into B.
              ClassSubject bClassSubject =
                  inspector.clazz(DescriptorUtils.descriptorToJavaType(TRANSFORMED_B_DESCRIPTOR));
              assertThat(bClassSubject, isPresent());

              MethodSubject bridgeMethodSubject =
                  bClassSubject.uniqueMethodWithOriginalName("bridge");
              assertThat(bridgeMethodSubject, isAbsent());

              MethodSubject testMethodSubject = bClassSubject.uniqueMethodWithOriginalName("test");
              assertThat(testMethodSubject, isPresent());

              // Inspect C. The method C.bridge() is never eligible for hoisting.
              ClassSubject cClassSubject = inspector.clazz(C.class);
              assertThat(cClassSubject, isPresent());

              MethodSubject bridgeOnCMethodSubject =
                  cClassSubject.uniqueMethodWithOriginalName("bridge");
              assertThat(bridgeOnCMethodSubject, isPresent());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(getExpectedOutput());
  }

  private List<String> getExpectedOutput() {
    if (removeBridgeMethodFromA) {
      return ImmutableList.of("A.m()", "Base.bridge()", "C.m()", "C.m()");
    }
    return ImmutableList.of("A.m()", "A.bridge()", "C.m()", "C.m()");
  }

  private List<byte[]> getProgramClassFileData() throws IOException, NoSuchMethodException {
    return ImmutableList.of(
        transformer(Main.class)
            .replaceClassDescriptorInMethodInstructions(
                descriptor(B.class), TRANSFORMED_B_DESCRIPTOR)
            .transform(),
        transformer(A.class)
            .applyIf(
                removeBridgeMethodFromA, transformer -> transformer.removeMethodsWithName("bridge"))
            .setPublic(A.class.getDeclaredMethod("m"))
            .transform(),
        transformer(B.class).setClassDescriptor(TRANSFORMED_B_DESCRIPTOR).transform(),
        transformer(C.class)
            .setSuper(TRANSFORMED_B_DESCRIPTOR)
            .replaceClassDescriptorInMethodInstructions(
                descriptor(B.class), TRANSFORMED_B_DESCRIPTOR)
            .setBridge(C.class.getDeclaredMethod("bridge"))
            .transform());
  }

  static class Main {

    public static void main(String[] args) {
      new A().callM();
      B bAsB = System.currentTimeMillis() > 0 ? new B() : new C();
      B cAsB = System.currentTimeMillis() > 0 ? new C() : new B();
      Base cAsBase = System.currentTimeMillis() > 0 ? new C() : new Base();
      bAsB.test(bAsB);
      bAsB.test(cAsB);
      cAsBase.bridge();
    }
  }

  @NoVerticalClassMerging
  public static class Base {

    public void bridge() {
      System.out.println("Base.bridge()");
    }
  }

  @NeverClassInline
  @NoVerticalClassMerging
  public static class A extends Base {

    @NeverInline
    /*public*/ void m() {
      System.out.println("A.m()");
    }

    @Override
    public void bridge() {
      System.out.println("A.bridge()");
    }

    @NeverInline
    public void callM() {
      m();
    }
  }

  @NeverClassInline
  @NoVerticalClassMerging
  public static class /*otherpackage.*/ B extends A {

    @NeverInline
    public void test(B b) {
      b.bridge();
    }
  }

  @NeverClassInline
  public static class C extends B {

    @NeverInline
    void m() {
      System.out.println("C.m()");
    }

    // Not eligible for bridge hoisting, as the bridge will then dispatch to A.m instead of B.m.
    @NeverInline
    @Override
    public /*bridge*/ void bridge() {
      this.m();
    }
  }
}
