// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.debug.DebugTestBase.JUnit3Wrapper.Command;
import com.android.tools.r8.kotlin.AbstractR8KotlinTestBase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.harmony.jpda.tests.framework.jdwp.Value;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KotlinInlineTest extends KotlinDebugTestBase {

  public static final String DEBUGGEE_CLASS = "KotlinInline";
  public static final String SOURCE_FILE = "KotlinInline.kt";

  private final TestParameters parameters;
  private final KotlinTestParameters kotlinParameters;

  @Parameters(name = "{0}, {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        getKotlinTestParameters().withAllCompilersAndTargetVersions().build());
  }

  public KotlinInlineTest(TestParameters parameters, KotlinTestParameters kotlinParameters) {
    this.parameters = parameters;
    this.kotlinParameters = kotlinParameters;
  }

  protected KotlinDebugD8Config getD8Config() {
    return KotlinDebugD8Config.build(
        kotlinParameters, parameters.getApiLevel(), parameters.getRuntime().asDex());
  }

  @Test
  public void testStepOverInline() throws Throwable {
    String methodName = "singleInline";
    runDebugTest(
        getD8Config(),
        DEBUGGEE_CLASS,
        breakpoint(DEBUGGEE_CLASS, methodName),
        run(),
        inspect(s -> {
          assertEquals(DEBUGGEE_CLASS, s.getClassName());
          assertEquals(methodName, s.getMethodName());
          assertEquals(SOURCE_FILE, s.getSourceFile());
          assertEquals(41, s.getLineNumber());
          s.checkLocal("this");
        }),
        stepOver(),
        inspect(s -> {
          assertEquals(DEBUGGEE_CLASS, s.getClassName());
          assertEquals(methodName, s.getMethodName());
          assertEquals(SOURCE_FILE, s.getSourceFile());
          assertEquals(42, s.getLineNumber());
          s.checkLocal("this");
        }),
        kotlinStepOver(),
        inspect(s -> {
          assertEquals(DEBUGGEE_CLASS, s.getClassName());
          assertEquals(methodName, s.getMethodName());
          assertEquals(SOURCE_FILE, s.getSourceFile());
          assertEquals(43, s.getLineNumber());
          s.checkLocal("this");
        }),
        run());
  }

  @Test
  public void testStepIntoInline() throws Throwable {
    String methodName = "singleInline";
    runDebugTest(
        getD8Config(),
        DEBUGGEE_CLASS,
        breakpoint(DEBUGGEE_CLASS, methodName),
        run(),
        inspect(s -> {
          assertEquals(DEBUGGEE_CLASS, s.getClassName());
          assertEquals(methodName, s.getMethodName());
          assertEquals(SOURCE_FILE, s.getSourceFile());
          assertEquals(41, s.getLineNumber());
          s.checkLocal("this");
        }),
        stepOver(),
        inspect(s -> {
          assertEquals(DEBUGGEE_CLASS, s.getClassName());
          assertEquals(methodName, s.getMethodName());
          assertEquals(SOURCE_FILE, s.getSourceFile());
          assertEquals(42, s.getLineNumber());
          s.checkLocal("this");
        }),
        stepInto(),
        inspect(s -> {
          assertEquals(DEBUGGEE_CLASS, s.getClassName());
          assertEquals(methodName, s.getMethodName());
          assertEquals(SOURCE_FILE, s.getSourceFile());
          // The actual line number (the one encoded in debug information) is different than the
          // source file one.
          // TODO(shertz) extract original line number from JSR-45's SMAP (only supported on
          // Android O+).
          assertTrue(42 != s.getLineNumber());
          s.checkLocal("this");
        }),
        run());
  }

  @Test
  public void testStepOutInline() throws Throwable {
    String methodName = "singleInline";
    runDebugTest(
        getD8Config(),
        DEBUGGEE_CLASS,
        breakpoint(DEBUGGEE_CLASS, methodName),
        run(),
        inspect(s -> {
          assertEquals(DEBUGGEE_CLASS, s.getClassName());
          assertEquals(methodName, s.getMethodName());
          assertEquals(SOURCE_FILE, s.getSourceFile());
          assertEquals(41, s.getLineNumber());
          s.checkLocal("this");
        }),
        stepOver(),
        inspect(s -> {
          assertEquals(DEBUGGEE_CLASS, s.getClassName());
          assertEquals(methodName, s.getMethodName());
          assertEquals(SOURCE_FILE, s.getSourceFile());
          assertEquals(42, s.getLineNumber());
          s.checkLocal("this");
        }),
        stepInto(),
        inspect(s -> {
          assertEquals(DEBUGGEE_CLASS, s.getClassName());
          assertEquals(methodName, s.getMethodName());
        }),
        kotlinStepOut(),
        inspect(s -> {
          assertEquals(DEBUGGEE_CLASS, s.getClassName());
          assertEquals(methodName, s.getMethodName());
          assertEquals(SOURCE_FILE, s.getSourceFile());
          assertEquals(43, s.getLineNumber());
          s.checkLocal("this");
        }),
        run());
  }

  private static String mangleFunctionNameFromInlineScope(String functionName) {
    return "$i$f$" + functionName;
  }

  private static String mangleLambdaNameFromInlineScope(String functionName, int lambdaId) {
    assert lambdaId > 0;
    return "$i$a$" + functionName + "$" + lambdaId;
  }

  private static String mangleLvNameFromInlineScope(String lvName, int inlineDepth) {
    assert inlineDepth > 0;
    StringBuilder builder = new StringBuilder(lvName);
    for (int i = 0; i < inlineDepth; ++i) {
      builder.append("$iv");
    }
    return builder.toString();
  }

  @Test
  public void testKotlinInline() throws Throwable {
    final String inliningMethodName = "invokeInlinedFunctions";
    runDebugTest(
        getD8Config(),
        DEBUGGEE_CLASS,
        breakpoint(DEBUGGEE_CLASS, inliningMethodName),
        run(),
        inspect(
            s -> {
              assertEquals(inliningMethodName, s.getMethodName());
              assertEquals(16, s.getLineNumber());
              s.checkLocal("this");
            }),
        stepInto(),
        inspect(
            s -> {
              // We must have stepped into the code of the inlined method but the actual current
              // method
              // did not change.
              assertEquals(inliningMethodName, s.getMethodName());
              // TODO(shertz) get the original line if JSR45 is supported by the targeted ART
              // runtime.
              s.checkLocal("this");
            }),
        stepInto(),
        inspect(
            s -> {
              assertEquals(inliningMethodName, s.getMethodName());
              assertEquals(17, s.getLineNumber());
              s.checkLocal("this");
            }),
        stepInto(),
        inspect(
            s -> {
              assertEquals(inliningMethodName, s.getMethodName());
              assertEquals(18, s.getLineNumber());
              s.checkLocal("this");
              s.checkLocal("inA", Value.createInt(1));
              // This is a "hidden" lv added by Kotlin (which is neither initialized nor used).
              s.checkLocal(mangleFunctionNameFromInlineScope("inlinedA"));
              s.checkLocal(
                  mangleLambdaNameFromInlineScope(
                      "-inlinedA-KotlinInline$invokeInlinedFunctions", 1));
            }),
        stepInto(),
        inspect(
            s -> {
              // We must have stepped into the code of the second inlined method but the actual
              // current
              // method did not change.
              assertEquals(inliningMethodName, s.getMethodName());
              // TODO(shertz) get the original line if JSR45 is supported by the targeted ART
              // runtime.
              s.checkLocal("this");
            }),
        stepInto(),
        inspect(
            s -> {
              assertEquals(inliningMethodName, s.getMethodName());
              assertEquals(19, s.getLineNumber());
              s.checkLocal("this");
            }),
        stepInto(),
        inspect(
            s -> {
              assertEquals(inliningMethodName, s.getMethodName());
              assertEquals(20, s.getLineNumber());
              s.checkLocal("this");
              s.checkLocal("inB", Value.createInt(2));
              // This is a "hidden" lv added by Kotlin (which is neither initialized nor used).
              s.checkLocal(mangleFunctionNameFromInlineScope("inlinedB"));
              s.checkLocal(
                  mangleLambdaNameFromInlineScope(
                      "-inlinedB-KotlinInline$invokeInlinedFunctions$1", 1));
            }),
        run());
  }

  @Test
  public void testNestedInlining() throws Throwable {
    // Count the number of lines in the source file. This is needed to check that inlined code
    // refers to non-existing line numbers.
    Path sourceFilePath =
        AbstractR8KotlinTestBase.getKotlinFileInResource("debug", SOURCE_FILE.replace(".kt", ""));
    assert sourceFilePath.toFile().exists();
    final int maxLineNumber = Files.readAllLines(sourceFilePath).size();
    final String inliningMethodName = "testNestedInlining";

    // Local variables that represent the scope (start,end) of function's code that has been
    // inlined.
    final String inlinee1_inlineScope = mangleFunctionNameFromInlineScope("inlinee1");
    final String inlinee2_inlineScope = mangleFunctionNameFromInlineScope("inlinee2");

    // Local variables that represent the scope (start,end) of lambda's code that has been inlined.
    final String inlinee2_lambda1_inlineScope = mangleLambdaNameFromInlineScope("inlinee2", 1);
    final String inlinee2_lambda2_inlineScope = "$i$a$-inlinee2-KotlinInline$inlinee1$1$iv";
    final String c_mangledLvName = mangleLvNameFromInlineScope("c", 1);
    final String left_mangledLvName = mangleLvNameFromInlineScope("left", 1);
    final String right_mangledLvName = mangleLvNameFromInlineScope("right", 1);
    final String p_mangledLvName = mangleLvNameFromInlineScope("p", 2);

    runDebugTest(
        getD8Config(),
        DEBUGGEE_CLASS,
        breakpoint(DEBUGGEE_CLASS, inliningMethodName),
        run(),
        inspect(
            s -> {
              assertEquals(inliningMethodName, s.getMethodName());
              assertEquals(52, s.getLineNumber());
              s.checkLocal("this");
            }),
        checkLocal("this"),
        checkNoLocals("l1", "l2"),
        stepOver(),
        checkLine(SOURCE_FILE, 53),
        checkLocals("this", "l1"),
        checkNoLocal("l2"),
        stepOver(),
        checkLine(SOURCE_FILE, 54),
        checkLocals("this", "l1", "l2"),
        stepInto(),
        // We jumped into 1st inlinee but the current method is the same
        checkMethod(DEBUGGEE_CLASS, inliningMethodName),
        checkLocal(inlinee1_inlineScope),
        inspect(
            state -> {
              assertEquals(SOURCE_FILE, state.getSourceFile());
              assertTrue(state.getLineNumber() > maxLineNumber);
            }),
        checkNoLocal(c_mangledLvName),
        stepInto(),
        checkLocal(c_mangledLvName),
        stepInto(),
        // We jumped into 2nd inlinee which is nested in the 1st inlinee
        checkLocal(inlinee2_inlineScope),
        checkLocal(inlinee1_inlineScope),
        inspect(
            state -> {
              assertEquals(SOURCE_FILE, state.getSourceFile());
              assertTrue(state.getLineNumber() > maxLineNumber);
            }),
        // We must see the local variable "p" with a 2-level inline depth.
        checkLocal(p_mangledLvName),
        checkNoLocals(left_mangledLvName, right_mangledLvName),
        // Enter the if block of inlinee2
        stepInto(),
        checkLocal(p_mangledLvName),
        checkNoLocals(left_mangledLvName, right_mangledLvName),
        // Enter the inlined lambda
        stepInto(),
        checkLocal(p_mangledLvName),
        checkLocal(inlinee2_lambda2_inlineScope),
        checkNoLocals(left_mangledLvName, right_mangledLvName),
        stepInto(),
        checkLocal(inlinee2_lambda2_inlineScope),
        checkLocal(left_mangledLvName),
        checkNoLocal(right_mangledLvName),
        stepInto(),
        checkLocals(left_mangledLvName, right_mangledLvName),
        // Enter "foo"
        stepIntoFooWithWorkaroundKotlin16(),
        stepOut(),
        // We're back to the inline section, at the end of the lambda
        inspect(
            state -> {
              assertEquals(SOURCE_FILE, state.getSourceFile());
              assertTrue(state.getLineNumber() > maxLineNumber);
            }),
        checkLocal(inlinee1_inlineScope),
        checkLocal(inlinee2_inlineScope),
        checkLocal(inlinee2_lambda2_inlineScope),
        stepInto(),
        // We're in inlinee2, after the call to the inlined lambda.
        checkLocal(inlinee1_inlineScope),
        checkLocal(inlinee2_inlineScope),
        checkNoLocal(inlinee2_lambda1_inlineScope),
        checkNoLocal(inlinee2_lambda2_inlineScope),
        checkLocal(p_mangledLvName),
        stepInto(),
        // We're out of inlinee2
        // For kotlin dev (18.alpha) we need an additional step, see b/238067321.
        applyIf(
            kotlinParameters
                .getCompilerVersion()
                .isGreaterThan(KotlinCompilerVersion.KOTLINC_1_7_0),
            this::stepInto),
        checkMethod(DEBUGGEE_CLASS, inliningMethodName),
        checkLocal(inlinee1_inlineScope),
        checkNoLocal(inlinee2_inlineScope),
        checkNoLocal(inlinee2_lambda1_inlineScope),
        // Enter the new call to "inlinee2"
        stepInto(),
        checkMethod(DEBUGGEE_CLASS, inliningMethodName),
        checkLocal(inlinee1_inlineScope),
        checkLocal(inlinee2_inlineScope),
        checkNoLocal(inlinee2_lambda1_inlineScope),
        checkNoLocal(inlinee2_lambda2_inlineScope),
        checkNoLocal(p_mangledLvName),
        stepInto(),
        checkMethod(DEBUGGEE_CLASS, inliningMethodName),
        checkLocal(inlinee1_inlineScope),
        checkLocal(inlinee2_inlineScope),
        checkNoLocal(inlinee2_lambda1_inlineScope),
        checkNoLocal(inlinee2_lambda2_inlineScope),
        checkNoLocal(p_mangledLvName),
        // We enter the 2nd lambda
        stepInto(),
        checkMethod(DEBUGGEE_CLASS, inliningMethodName),
        checkLocal(inlinee1_inlineScope),
        checkLocal(inlinee2_inlineScope),
        checkNoLocal(inlinee2_lambda1_inlineScope),
        checkNoLocal(inlinee2_lambda2_inlineScope),
        stepIntoFooWithWorkaroundKotlin16(),
        run());
  }

  // See b/207743106 for incorrect debug info on Kotlin 1.6.
  private Command stepIntoFooWithWorkaroundKotlin16() {
    boolean is16 = kotlinParameters.getCompilerVersion() == KotlinCompilerVersion.KOTLINC_1_6_0;
    boolean stepHitsStringBuilder = parameters.isDexRuntimeVersionNewerThanOrEqual(Version.V13_0_0);
    List<Command> commands = new ArrayList<>();
    // Enter the call to "foo"
    commands.add(stepInto());
    // The code on kotlin 1.6 does not have an active line on entry, so advance to hit it.
    if (is16) {
      commands.add(stepInto());
      // On newer VMs the stepInput will enter StringBuilder, so step out of that again.
      if (stepHitsStringBuilder) {
        commands.add(stepOut());
      }
    }
    commands.add(checkMethod(DEBUGGEE_CLASS, "foo"));
    commands.add(checkLine(SOURCE_FILE, 34));
    return subcommands(commands);
  }
}
