// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.movestringconstants;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.ForceInline;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.CfInstructionSubject;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject.JumboStringMode;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MoveStringConstantsTest extends TestBase {

  private Backend backend;

  @Parameters(name = "Backend: {0}")
  public static Collection<Backend> data() {
    return Arrays.asList(Backend.values());
  }

  private void runTest(Consumer<CodeInspector> inspection) throws Exception {
    R8Command.Builder builder = R8Command.builder();
    builder.addProgramFiles(ToolHelper.getClassFileForTestClass(ForceInline.class));
    builder.addProgramFiles(ToolHelper.getClassFileForTestClass(TestClass.class));
    builder.addProgramFiles(ToolHelper.getClassFileForTestClass(Utils.class));
    builder.addLibraryFiles(runtimeJar(backend));
    builder.setProgramConsumer(emptyConsumer(backend));
    builder.setMode(CompilationMode.RELEASE);
    builder.addProguardConfiguration(
        ImmutableList.of(
            "-forceinline class * { @com.android.tools.r8.ForceInline *; }",
            "-keep class " + TestClass.class.getCanonicalName() + "{ *; }",
            "-dontobfuscate",
            "-allowaccessmodification"
        ),
        Origin.unknown());
    ToolHelper.allowTestProguardOptions(builder);
    AndroidApp app = ToolHelper.runR8(builder.build());
    inspection.accept(new CodeInspector(app));

    if (backend == Backend.DEX) {
      // Run on Art to check generated code against verifier.
      runOnArt(app, TestClass.class);
    } else {
      assert backend == Backend.CF;
      runOnJava(app, TestClass.class);
    }
  }

  public MoveStringConstantsTest(Backend backend) {
    this.backend = backend;
  }

  private void validate(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz(TestClass.class);
    assertTrue(clazz.isPresent());

    // Check the instruction used for testInlinedIntoVoidMethod
    MethodSubject methodThrowToBeInlined =
        clazz.method("void", "foo", ImmutableList.of(
            "java.lang.String", "java.lang.String", "java.lang.String", "java.lang.String"));
    assertTrue(methodThrowToBeInlined.isPresent());
    assert (backend == Backend.DEX || backend == Backend.CF);
    Predicate<InstructionSubject> nullCheck =
        backend == Backend.DEX
            ? InstructionSubject::isIfEqz
            : insn -> ((CfInstructionSubject) insn).isIfNull();

    // CF should not canonicalize strings or lower them. This test ensures that strings are moved
    // down and make assertions based on throwing branches, which we do not care about in CF.
    // See (r8g/30163) and (r8g/30320).
    assumeTrue(backend == Backend.DEX);
    validateSequence(
        methodThrowToBeInlined.iterateInstructions(),
        // 'if' with "foo#1" is flipped.
        nullCheck,

        // 'if' with "foo#2" is removed along with the constant.

        // 'if' with "foo#3" is removed so now we have unconditional call.
        insn -> insn.isConstString("StringConstants::foo#3", JumboStringMode.DISALLOW),
        InstructionSubject::isInvokeStatic,
        InstructionSubject::isThrow,

        // 'if's with "foo#4" and "foo#5" are flipped, but their throwing branches
        // are not moved to the end of the code (area for improvement?).
        insn -> insn.isConstString("StringConstants::foo#4", JumboStringMode.DISALLOW),
        nullCheck, // Flipped if
        InstructionSubject::isGoto, // Jump around throwing branch.
        InstructionSubject::isInvokeStatic, // Throwing branch.
        InstructionSubject::isThrow,
        insn -> insn.isConstString("StringConstants::foo#5", JumboStringMode.DISALLOW),
        nullCheck, // Flipped if
        InstructionSubject::isReturnVoid, // Final return statement.
        InstructionSubject::isInvokeStatic, // Throwing branch.
        InstructionSubject::isThrow,

        // After 'if' with "foo#1" flipped, always throwing branch
        // moved here along with the constant.
        insn -> insn.isConstString("StringConstants::foo#1", JumboStringMode.DISALLOW),
        InstructionSubject::isInvokeStatic,
        InstructionSubject::isThrow);
  }

  @SafeVarargs
  private final void validateSequence(
      Iterator<InstructionSubject> instructions, Predicate<InstructionSubject>... checks) {
    int index = 0;

    while (instructions.hasNext()) {
      if (index >= checks.length) {
        return;
      }
      if (checks[index].test(instructions.next())) {
        index++;
      }
    }

    assertTrue("Not all checks processed", index >= checks.length);
  }

  @Test
  public void test() throws Exception {
    runTest(this::validate);
  }
}
