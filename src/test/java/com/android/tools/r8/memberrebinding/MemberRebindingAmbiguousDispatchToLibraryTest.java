// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.memberrebinding;


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
public class MemberRebindingAmbiguousDispatchToLibraryTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameter(1)
  public boolean abstractMethodOnSuperClass;

  @Parameter(2)
  public boolean interfaceAsSymbolicReference;

  @Parameters(name = "{0}, abstractMethodOnSuperClass: {1}, interfaceAsSymbolicReference {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevelsAlsoForCf().build(),
        BooleanUtils.values(),
        BooleanUtils.values());
  }

  private void setupInput(TestBuilder<?, ?> testBuilder) throws Exception {
    testBuilder
        .addProgramClasses(Main.class)
        .addProgramClassFileData(getProgramClass())
        .addDefaultRuntimeLibrary(parameters)
        .addLibraryClasses(SuperInterface.class)
        .addLibraryClassFileData(getSuperClass());
  }

  @Test
  public void testReference() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .apply(this::setupInput)
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput);
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .apply(this::setupInput)
        .setMinApi(parameters)
        .compile()
        .addRunClasspathClasses(SuperInterface.class)
        .addRunClasspathClassFileData(getSuperClass())
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput);
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .apply(this::setupInput)
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .compile()
        .addRunClasspathClasses(SuperInterface.class)
        .addRunClasspathClassFileData(getSuperClass())
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput);
  }

  private void checkOutput(TestRunResult<?> result) {
    if (parameters.isDexRuntime() && interfaceAsSymbolicReference) {
      if (parameters.getDexRuntimeVersion().isDalvik()) {
        result.assertFailureWithErrorThatThrows(VerifyError.class);
      } else if (parameters.getDexRuntimeVersion().isOlderThan(Version.V7_0_0)) {
        result.assertFailureWithErrorThatThrows(IncompatibleClassChangeError.class);
      } else {
        // TODO(b/259227990): If the SuperClass.foo() is not abstract we produce a working program.
        result.assertFailureWithErrorThatThrows(AbstractMethodError.class);
      }
    } else if (abstractMethodOnSuperClass || interfaceAsSymbolicReference) {
      result.assertFailureWithErrorThatThrows(AbstractMethodError.class);
    } else {
      result.assertSuccessWithOutputLines("SuperClass::foo");
    }
  }

  private byte[] getSuperClass() throws Exception {
    return abstractMethodOnSuperClass
        ? transformer(SuperClassAbstract.class)
            .setClassDescriptor(descriptor(SuperClass.class))
            .transform()
        : transformer(SuperClass.class).transform();
  }

  private byte[] getProgramClass() throws Exception {
    return interfaceAsSymbolicReference
        ? transformer(ProgramClass.class)
            .transformMethodInsnInMethod(
                "foo",
                (opcode, owner, name, descriptor, isInterface, visitor) ->
                    visitor.visitMethodInsn(
                        opcode, binaryName(SuperInterface.class), name, descriptor, true))
            .transform()
        : transformer(ProgramClass.class).transform();
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
