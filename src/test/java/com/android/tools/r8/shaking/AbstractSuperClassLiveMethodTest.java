// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AbstractSuperClassLiveMethodTest extends TestBase {

  private final TestParameters parameters;
  private final String NEW_DESCRIPTOR = "Lfoo/A;";
  private final String[] EXPECTED = new String[] {"A::foo", "Base::foo"};
  private final String[] EXPECTED_DALVIK = new String[] {"A::foo", "A::foo"};

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public AbstractSuperClassLiveMethodTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  public List<byte[]> getProgramClassFileData() throws Exception {
    return ImmutableList.of(
        transformer(A.class).setClassDescriptor(NEW_DESCRIPTOR).transform(),
        transformer(Main.class)
            .replaceClassDescriptorInMethodInstructions(descriptor(A.class), NEW_DESCRIPTOR)
            .transform());
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(Base.class)
        .addProgramClassFileData(getProgramClassFileData())
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            parameters.isDexRuntime() && parameters.getDexRuntimeVersion().isDalvik(),
            r -> r.assertSuccessWithOutputLines(EXPECTED_DALVIK),
            r -> r.assertSuccessWithOutputLines(EXPECTED));
  }

  @Test
  public void testForR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Base.class)
        .addProgramClassFileData(getProgramClassFileData())
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .enableNeverClassInliningAnnotations()
        .addOptionsModification(options -> options.enableDevirtualization = false)
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            parameters.isDexRuntime() && parameters.getDexRuntimeVersion().isDalvik(),
            r -> r.assertSuccessWithOutputLines(EXPECTED_DALVIK),
            // TODO(b/182444403): Should succeed with EXPECTED.
            r -> r.assertFailureWithErrorThatThrows(AbstractMethodError.class));
  }

  @NoVerticalClassMerging
  public abstract static class Base {

    @NeverInline
    void foo() {
      System.out.println("Base::foo");
    }
  }

  @NeverClassInline
  public static class /* will be foo.A */ A extends Base {

    @Override
    @NeverInline
    public void foo() {
      System.out.println("A::foo");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      Base a = new A();
      ((A) a).foo();
      a.foo();
    }
  }
}
