// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.methodhandles;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class InvalidBootstrapMethodHandleTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("Called foo!");

  static final Map<String, Integer> HANDLE_TYPES =
      ImmutableMap.<String, Integer>builder()
          .put("GETFIELD", Opcodes.H_GETFIELD)
          .put("GETSTATIC", Opcodes.H_GETSTATIC)
          .put("PUTFIELD", Opcodes.H_PUTFIELD)
          .put("PUTSTATIC", Opcodes.H_PUTSTATIC)
          .put("INVOKEVIRTUAL", Opcodes.H_INVOKEVIRTUAL)
          .put("INVOKESTATIC", Opcodes.H_INVOKESTATIC)
          .put("INVOKESPECIAL", Opcodes.H_INVOKESPECIAL)
          .put("NEWINVOKESPECIAL", Opcodes.H_NEWINVOKESPECIAL)
          .put("INVOKEINTERFACE", Opcodes.H_INVOKEINTERFACE)
          .build();

  private final TestParameters parameters;
  private final String handleTypeString;

  @Parameterized.Parameters(name = "{0}, {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withAllRuntimes()
            .withApiLevelsStartingAtIncluding(apiLevelWithInvokeCustomSupport())
            .build(),
        new TreeSet<>(HANDLE_TYPES.keySet()));
  }

  public InvalidBootstrapMethodHandleTest(TestParameters parameters, String handleTypeString) {
    this.parameters = parameters;
    this.handleTypeString = handleTypeString;
  }

  private int handleTypeOpcode() {
    return HANDLE_TYPES.get(handleTypeString);
  }

  @Test
  public void test() throws Exception {
    // The compiler will fail on opcodes different from 6 and 8.
    // Investigate why 8 is supposedly valid.
    if (parameters.isDexRuntime()
        && handleTypeOpcode() != Opcodes.H_INVOKESTATIC
        && handleTypeOpcode() != Opcodes.H_NEWINVOKESPECIAL) {
      try {
        testForD8()
            .addProgramClasses(InvalidBootstrapMethodHandleTestInterface.class)
            .addProgramClassFileData(getProgramClassFileData())
            .compileWithExpectedDiagnostics(
                diagnotics ->
                    diagnotics.assertErrorMessageThatMatches(
                        containsString("Bootstrap handle invalid")));
        fail("Expected compilation to fail");
      } catch (CompilationFailedException e) {
        // Expected failure.
        return;
      }
    }
    TestRunResult<?> result =
        testForRuntime(parameters)
            .addProgramClasses(InvalidBootstrapMethodHandleTestInterface.class)
            .addProgramClassFileData(getProgramClassFileData())
            .run(parameters.getRuntime(), InvalidBootstrapMethodHandleTestClass.class);
    // The static target is valid and should run as expected.
    if (handleTypeOpcode() == Opcodes.H_INVOKESTATIC) {
      result.assertSuccessWithOutput(EXPECTED);
      return;
    }
    // The invalid targets will trigger an error due to the bootstrap method not being able to be
    // cast to the expected signature for a bootstrap method. This happens prior to invoking the
    // target of that handle.
    if (parameters.isCfRuntime()) {
      result.assertFailureWithErrorThatThrows(BootstrapMethodError.class);
      result.assertFailureWithErrorThatMatches(containsString("cannot convert"));
      result.assertFailureWithErrorThatMatches(
          containsString("to (Lookup,String,MethodType)Object"));
      return;
    }
    // D8 allows new-invoke-special type during compilation, but it is not accepted by ART.
    if (handleTypeOpcode() == Opcodes.H_NEWINVOKESPECIAL) {
      result.assertFailureWithErrorThatMatches(
          containsString("handle type is not InvokeStatic: 6"));
    }
  }

  // Each handle is returned such that the method handle itself is valid and will not cause an
  // error due to failed handle construction.
  private Handle getHandle() {
    String bootstrapSignature =
        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;";

    switch (handleTypeOpcode()) {
      case Opcodes.H_GETFIELD:
      case Opcodes.H_PUTFIELD:
        return new Handle(
            handleTypeOpcode(),
            binaryName(InvalidBootstrapMethodHandleTestClass.class),
            "nonStaticField",
            "I",
            false);

      case Opcodes.H_GETSTATIC:
      case Opcodes.H_PUTSTATIC:
        return new Handle(
            handleTypeOpcode(),
            binaryName(InvalidBootstrapMethodHandleTestClass.class),
            "staticField",
            "I",
            false);

      case Opcodes.H_INVOKESTATIC:
        return new Handle(
            handleTypeOpcode(),
            binaryName(InvalidBootstrapMethodHandleTestClass.class),
            "staticMethod",
            bootstrapSignature,
            handleTypeOpcode() == Opcodes.H_INVOKEINTERFACE);

      case Opcodes.H_INVOKEVIRTUAL:
      case Opcodes.H_INVOKESPECIAL:
        return new Handle(
            handleTypeOpcode(),
            binaryName(InvalidBootstrapMethodHandleTestClass.class),
            "virtualMethod",
            bootstrapSignature,
            false);

      case Opcodes.H_INVOKEINTERFACE:
        return new Handle(
            handleTypeOpcode(),
            binaryName(InvalidBootstrapMethodHandleTestInterface.class),
            "virtualMethod",
            bootstrapSignature,
            true);

      case Opcodes.H_NEWINVOKESPECIAL:
        return new Handle(
            handleTypeOpcode(),
            binaryName(InvalidBootstrapMethodHandleTestClass.class),
            "<init>",
            "()V",
            false);

      default:
        throw new RuntimeException("Unexpected handle type");
    }
  }

  private byte[] getProgramClassFileData() throws Exception {
    return transformer(InvalidBootstrapMethodHandleTestClass.class)
        .transformMethodInsnInMethod(
            "main",
            (opcode, owner, name, descriptor, isInterface, visitor) -> {
              if (opcode == Opcodes.INVOKESTATIC && name.equals("foo")) {
                visitor.visitInvokeDynamicInsn("foo", "()V", getHandle());
              } else {
                visitor.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
              }
            })
        .transform();
  }
}
