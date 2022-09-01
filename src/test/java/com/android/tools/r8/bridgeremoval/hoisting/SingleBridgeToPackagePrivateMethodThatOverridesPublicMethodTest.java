// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.bridgeremoval.hoisting;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.notIf;
import static com.android.tools.r8.utils.codeinspector.Matchers.onlyIf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeFalse;

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
import com.google.common.collect.Lists;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SingleBridgeToPackagePrivateMethodThatOverridesPublicMethodTest extends TestBase {

  private static final String TRANSFORMED_B_DESCRIPTOR = "LB;";

  @Parameter(0)
  public boolean enableBridgeHoistingFromB;

  @Parameter(1)
  public TestParameters parameters;

  @Parameters(name = "{1}, enable bridge hoisting from B: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  @Test
  public void testRuntime() throws Exception {
    assumeFalse(enableBridgeHoistingFromB);
    testForRuntime(parameters)
        .addProgramClassFileData(getProgramClassFileData())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(getExpectedOutput());
  }

  @Test
  public void testR8() throws Exception {
    List<String> seenClassesInBridgeHoisting = new ArrayList<>();
    testForR8(parameters.getBackend())
        .addProgramClassFileData(getProgramClassFileData())
        .addKeepMainRule(Main.class)
        .applyIf(
            !enableBridgeHoistingFromB,
            testBuilder ->
                testBuilder.addOptionsModification(
                    options ->
                        options.testing.isEligibleForBridgeHoisting =
                            clazz -> {
                              String classDescriptor = clazz.getType().toDescriptorString();
                              seenClassesInBridgeHoisting.add(classDescriptor);
                              if (classDescriptor.equals(TRANSFORMED_B_DESCRIPTOR)) {
                                return false;
                              }
                              return true;
                            }))
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(
            inspector -> {
              // Inspect A.
              ClassSubject aClassSubject = inspector.clazz(A.class);
              assertThat(aClassSubject, isPresent());

              MethodSubject bridgeOnAMethodSubject = aClassSubject.uniqueMethodWithName("bridge");
              assertThat(bridgeOnAMethodSubject, onlyIf(enableBridgeHoistingFromB, isPresent()));

              // Inspect B.
              ClassSubject bClassSubject =
                  inspector.clazz(DescriptorUtils.descriptorToJavaType(TRANSFORMED_B_DESCRIPTOR));
              assertThat(bClassSubject, isPresent());

              MethodSubject bridgeOnBMethodSubject = bClassSubject.uniqueMethodWithName("bridge");
              assertThat(bridgeOnBMethodSubject, notIf(isPresent(), enableBridgeHoistingFromB));

              // Inspect C.
              ClassSubject cClassSubject = inspector.clazz(C.class);
              assertThat(cClassSubject, isPresent());

              MethodSubject bridgeOnCMethodSubject = cClassSubject.uniqueMethodWithName("bridge");
              assertThat(bridgeOnCMethodSubject, isAbsent());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(getExpectedOutput());

    // Verify that the there was the expected calls to isEligibleForBridgeHoisting().
    if (!enableBridgeHoistingFromB) {
      assertEquals(
          Lists.newArrayList(descriptor(C.class), TRANSFORMED_B_DESCRIPTOR),
          seenClassesInBridgeHoisting);
    }
  }

  private List<String> getExpectedOutput() {
    return ImmutableList.of("A.m()", "C.m()", "C.m()");
  }

  private List<byte[]> getProgramClassFileData() throws IOException, NoSuchMethodException {
    return ImmutableList.of(
        transformer(Main.class)
            .replaceClassDescriptorInMethodInstructions(
                descriptor(B.class), TRANSFORMED_B_DESCRIPTOR)
            .transform(),
        transformer(A.class).setPublic(A.class.getDeclaredMethod("m")).transform(),
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
      new C().callM();
      new C().bridge();
    }
  }

  @NeverClassInline
  @NoVerticalClassMerging
  public static class A {

    @NeverInline
    /*public*/ void m() {
      System.out.println("A.m()");
    }

    @NeverInline
    public void callM() {
      m();
    }
  }

  @NeverClassInline
  @NoVerticalClassMerging
  public static class /*otherpackage.*/ B extends A {}

  @NeverClassInline
  public static class C extends B {

    @NeverInline
    void m() {
      System.out.println("C.m()");
    }

    @NeverInline
    public /*bridge*/ void bridge() {
      this.m();
    }
  }
}
