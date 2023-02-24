// Copyright (c) 2018 the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.reachabilitysensitive;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.dex.Marker.Tool;
import com.android.tools.r8.dex.code.DexAddIntLit8;
import com.android.tools.r8.dex.code.DexConst4;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexDebugEvent.StartLocal;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import dalvik.annotation.optimization.ReachabilitySensitive;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

class TestClass {
  public void method() {
    int i = 2;
    int j = i + 1;
    int k = j + 2;
    System.out.println(k);
  }
}

class TestClassWithAnnotatedField {
  @ReachabilitySensitive private final long field = 0;

  public void method() {
    int i = 2;
    int j = i + 1;
    int k = j + 2;
    System.out.println(k);
  }
}

class TestClassWithAnnotatedMethod {

  @ReachabilitySensitive
  public void unrelatedAnnotatedMethod() {}

  public void method() {
    int i = 2;
    int j = i + 1;
    int k = j + 2;
    System.out.println(k);
  }
}

@RunWith(Parameterized.class)
public class ReachabilitySensitiveTest extends TestBase {

  private final TestParameters parameters;
  private final Tool tool;

  @Parameters(name = "{0} tool: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        ImmutableList.of(Tool.D8, Tool.R8));
  }

  public ReachabilitySensitiveTest(TestParameters parameters, Tool tool) {
    this.parameters = parameters;
    this.tool = tool;
  }

  private int getNumRegisters() {
    // With API level >= Q we are allowed to re-use the receiver's register.
    // See also InternalOptions.canHaveThisJitCodeDebuggingBug().
    assert parameters.isDexRuntime();
    return parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.Q) ? 2 : 3;
  }

  @Test
  public void testNoAnnotation()
      throws IOException, CompilationFailedException, ExecutionException, NoSuchMethodException {
    CodeInspector inspector =
        tool == Tool.R8 ? compileR8(TestClass.class) : compile(TestClass.class);
    DexCode code =
        inspector.method(TestClass.class.getMethod("method")).getMethod().getCode().asDexCode();
    // Computation of k is constant folded and the value takes up one register. System.out takes
    // up another register and the receiver is the last, unless on Q+.
    assertEquals(getNumRegisters(), code.registerSize);
    checkNoLocals(code);
  }

  @Test
  public void testFieldAnnotation()
      throws IOException, CompilationFailedException, ExecutionException, NoSuchMethodException {
    CodeInspector inspector =
        tool == Tool.R8
            ? compileR8(TestClassWithAnnotatedField.class)
            : compile(TestClassWithAnnotatedField.class);
    checkAnnotatedCode(
        inspector
            .method(TestClassWithAnnotatedField.class.getMethod("method"))
            .getMethod()
            .getCode()
            .asDexCode());
  }

  @Test
  public void testMethodAnnotation()
      throws IOException, CompilationFailedException, ExecutionException, NoSuchMethodException {
    CodeInspector inspector =
        tool == Tool.R8
            ? compileR8(TestClassWithAnnotatedMethod.class)
            : compile(TestClassWithAnnotatedMethod.class);
    checkAnnotatedCode(
        inspector
            .method(TestClassWithAnnotatedMethod.class.getMethod("method"))
            .getMethod()
            .getCode()
            .asDexCode());
  }

  private void checkNoLocals(DexCode code) {
    // Even if we preserve live range of locals, we do not output locals information
    // as this is a release build.
    assertTrue(
        (code.getDebugInfo() == null)
            || Arrays.stream(code.getDebugInfo().asEventBasedInfo().events)
                .allMatch(event -> !(event instanceof StartLocal)));
  }

  private void checkAnnotatedCode(DexCode code) {
    // All live at the same time: receiver, i, j, k, System.out.
    assertEquals(5, code.registerSize);
    DexInstruction first = code.instructions[0];
    DexInstruction second = code.instructions[1];
    DexInstruction third = code.instructions[2];
    // None of the local declarations overwrite other locals.
    assertTrue(first instanceof DexConst4);
    assertTrue(second instanceof DexAddIntLit8);
    assertTrue(third instanceof DexAddIntLit8);
    int firstRegister = ((DexConst4) first).A;
    int secondRegister = ((DexAddIntLit8) second).AA;
    int thirdRegister = ((DexAddIntLit8) third).AA;
    assertFalse(firstRegister == secondRegister);
    assertFalse(firstRegister == thirdRegister);
    assertFalse(secondRegister == thirdRegister);
    checkNoLocals(code);
  }

  private CodeInspector compile(Class... classes) throws CompilationFailedException, IOException {
    return testForD8()
        .addProgramClasses(classes)
        .setMinApi(parameters)
        .setMode(CompilationMode.RELEASE)
        .compile()
        .inspector();
  }

  private CodeInspector compileR8(Class... classes) throws CompilationFailedException, IOException {
    List<String> keepRules =
        Arrays.stream(classes)
            .map(c -> "-keep class " + c.getCanonicalName() + " { <methods>; }")
            .collect(Collectors.toList());
    return testForR8(Backend.DEX)
        .addProgramClasses(classes)
        // TODO(ager): This will be in android.jar over time. For now, make it part of the app.
        .addProgramClasses(ReachabilitySensitive.class)
        .setMinApi(parameters)
        .setMode(CompilationMode.RELEASE)
        // Keep the input class and its methods.
        .addKeepRules(keepRules)
        // Keep the annotation class.
        .addKeepRules(
            "-keep class dalvik.annotation.optimization.ReachabilitySensitive",
            "-keep,allowshrinking,allowobfuscation class * {",
            "  @dalvik.annotation.optimization.ReachabilitySensitive <fields>;",
            "  @dalvik.annotation.optimization.ReachabilitySensitive <methods>;",
            "}")
        // Keep the annotation so R8 can find it and honor it. It also needs to be available
        // at runtime so that the Art runtime can honor it as well, so if it is not kept we
        // do not have to honor it as the runtime will not know to do so in any case.
        .addKeepRuntimeVisibleAnnotations()
        .compile()
        .inspector();
  }
}
