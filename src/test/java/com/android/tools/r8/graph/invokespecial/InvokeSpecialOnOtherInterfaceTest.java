// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.invokespecial;

import static com.android.tools.r8.utils.DescriptorUtils.getBinaryNameFromJavaType;
import static org.junit.Assert.assertEquals;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;

import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoMethodStaticizing;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.codeinspector.HorizontallyMergedClassesInspector;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvokeSpecialOnOtherInterfaceTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public InvokeSpecialOnOtherInterfaceTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters.getRuntime(), parameters.getApiLevel())
        .addProgramClassesAndInnerClasses(Main.class)
        .addProgramClasses(I.class)
        .addProgramClassFileData(getClassWithTransformedInvoked())
        .run(parameters.getRuntime(), Main.class)
        // TODO(b/144410139): Consider making this a compilation failure when generating DEX.
        .applyIf(
            parameters.isCfRuntime(),
            r -> r.assertFailureWithErrorThatThrows(VerifyError.class),
            !(parameters.isDexRuntimeVersionNewerThanOrEqual(Version.V13_0_0)
                && parameters.canUseDefaultAndStaticInterfaceMethods()),
            r -> r.assertSuccessWithOutputLines("Hello World!"),
            r -> r.assertFailureWithErrorThatThrows(NoSuchMethodError.class));
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassesAndInnerClasses(Main.class)
        .addProgramClasses(I.class)
        .addProgramClassFileData(getClassWithTransformedInvoked())
        .addKeepMainRule(Main.class)
        .addHorizontallyMergedClassesInspector(
            HorizontallyMergedClassesInspector::assertNoClassesMerged)
        .enableNoHorizontalClassMergingAnnotations()
        .enableNoMethodStaticizingAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        // TODO(b/144410139): Consider making this a compilation failure when generating DEX.
        .applyIf(
            parameters.isCfRuntime(),
            r -> r.assertFailureWithErrorThatThrows(VerifyError.class),
            parameters.isDexRuntime() && !parameters.canUseDefaultAndStaticInterfaceMethods(),
            r -> r.assertSuccessWithOutputLines("Hello World!"),
            parameters.isDexRuntime()
                && !parameters.isDexRuntimeVersionNewerThanOrEqual(Version.V13_0_0)
                && parameters.canUseDefaultAndStaticInterfaceMethods(),
            r -> r.assertFailureWithErrorThatThrows(NullPointerException.class),
            r -> r.assertFailureWithErrorThatThrows(NoSuchMethodError.class));
  }

  private byte[] getClassWithTransformedInvoked() throws IOException {
    return transformer(A.class)
        .transformMethodInsnInMethod(
            "bar",
            (opcode, owner, name, descriptor, isInterface, continuation) -> {
              assertEquals(getBinaryNameFromJavaType(I.class.getTypeName()), owner);
              continuation.visitMethodInsn(INVOKESPECIAL, owner, name, descriptor, isInterface);
            })
        .transform();
  }

  public interface I {

    @NoMethodStaticizing
    default void foo() {
      System.out.println("Hello World!");
    }
  }

  @NoHorizontalClassMerging
  public static class A {

    public void bar(I i) {
      i.foo(); // Will be rewritten to invoke-special I.foo()
    }
  }

  public static class Main {

    public static void main(String[] args) {
      new A().bar(new I() {});
    }
  }
}
