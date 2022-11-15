// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.memberrebinding;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DiagnosticsMatcher;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.BooleanUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MemberRebindingAmbiguousDispatchTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameter(1)
  public boolean abstractMethodOnSuperClass;

  @Parameter(2)
  public boolean interfaceAsSymbolicReference;

  @Parameters(name = "{0}, abstractMethodOnSuperClass: {1}, interfaceAsSymbolicReference {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        BooleanUtils.values(),
        BooleanUtils.values());
  }

  private void setupInput(TestBuilder<?, ?> testBuilder) {
    testBuilder
        .addProgramClasses(Main.class, SuperInterface.class)
        .applyIf(
            abstractMethodOnSuperClass,
            b -> b.addProgramClassFileData(getSuperClassWithFooAsAbstract()),
            b -> b.addProgramClasses(SuperClass.class))
        .applyIf(
            interfaceAsSymbolicReference,
            b -> b.addProgramClassFileData(getProgramClassWithInvokeToInterface()),
            b -> b.addProgramClasses(ProgramClass.class));
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .apply(this::setupInput)
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput);
  }

  private boolean desugaringWithoutSupport() {
    return parameters.isDexRuntime()
        && interfaceAsSymbolicReference
        && !parameters.canUseDefaultAndStaticInterfaceMethods();
  }

  @Test
  public void testR8() throws Exception {
    assumeFalse(desugaringWithoutSupport());
    testForR8(parameters.getBackend())
        .apply(this::setupInput)
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput);
  }

  @Test
  public void testR8AssertionError() {
    assumeTrue(desugaringWithoutSupport());
    // TODO(b/259227990): We should not fail compilation.
    assertThrows(
        CompilationFailedException.class,
        () ->
            testForR8(parameters.getBackend())
                .apply(this::setupInput)
                .setMinApi(parameters.getApiLevel())
                .addKeepMainRule(Main.class)
                .compileWithExpectedDiagnostics(
                    diagnostics ->
                        diagnostics.assertErrorThatMatches(
                            DiagnosticsMatcher.diagnosticException(AssertionError.class))));
  }

  private void checkOutput(TestRunResult<?> result) {
    if (parameters.isDexRuntime()
        && parameters.getDexRuntimeVersion().isDalvik()
        && interfaceAsSymbolicReference) {
      result.assertFailureWithErrorThatThrows(VerifyError.class);
    } else if (parameters.isDexRuntime()
        && parameters.getDexRuntimeVersion().isNewerThanOrEqual(Version.V7_0_0)
        && interfaceAsSymbolicReference
        && !parameters.canUseDefaultAndStaticInterfaceMethods()) {
      result.assertFailureWithErrorThatThrows(ClassNotFoundException.class);
    } else if (abstractMethodOnSuperClass || interfaceAsSymbolicReference) {
      result.assertFailureWithErrorThatThrows(AbstractMethodError.class);
    } else {
      result.assertSuccessWithOutputLines("SuperClass::foo");
    }
  }

  private byte[] getSuperClassWithFooAsAbstract() throws Exception {
    return transformer(SuperClassAbstract.class)
        .setClassDescriptor(descriptor(SuperClass.class))
        .transform();
  }

  private byte[] getProgramClassWithInvokeToInterface() throws Exception {
    return transformer(ProgramClass.class)
        .transformMethodInsnInMethod(
            "foo",
            (opcode, owner, name, descriptor, isInterface, visitor) ->
                visitor.visitMethodInsn(
                    opcode, binaryName(SuperInterface.class), name, descriptor, true))
        .transform();
  }

  public abstract static class SuperClassAbstract {

    public abstract void foo();
  }

  public abstract static class SuperClass {

    public void foo() {
      System.out.println("SuperClass::foo");
    }
  }

  public interface SuperInterface {

    void foo();
  }

  public static class ProgramClass extends SuperClass implements SuperInterface {

    @Override
    public void foo() {
      super.foo();
    }
  }

  public static class Main {

    public static void main(String[] args) {
      new ProgramClass().foo();
    }
  }
}
