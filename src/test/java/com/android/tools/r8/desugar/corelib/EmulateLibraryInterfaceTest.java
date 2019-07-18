// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.corelib;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.D8TestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.code.Instruction;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.ir.desugar.InterfaceMethodRewriter;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class EmulateLibraryInterfaceTest extends CoreLibDesugarTestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public EmulateLibraryInterfaceTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testDispatchClasses() throws Exception {
    CodeInspector inspector = new CodeInspector(buildDesugaredLibrary(parameters.getApiLevel()));
    List<FoundClassSubject> dispatchClasses =
        inspector.allClasses().stream()
            .filter(
                clazz ->
                    clazz
                        .getOriginalName()
                        .contains(InterfaceMethodRewriter.EMULATE_LIBRARY_CLASS_NAME_SUFFIX))
            .collect(Collectors.toList());
    int numDispatchClasses = requiresCoreLibDesugaring(parameters) ? 9 : 0;
    assertEquals(numDispatchClasses, dispatchClasses.size());
    for (FoundClassSubject clazz : dispatchClasses) {
      assertTrue(
          clazz.allMethods().stream()
              .allMatch(
                  method ->
                      method.isStatic()
                          && method
                              .streamInstructions()
                              .anyMatch(InstructionSubject::isInstanceOf)));
    }
    if (requiresCoreLibDesugaring(parameters)) {
      DexClass collectionDispatch = inspector.clazz("j$.util.Collection$-EL").getDexClass();
      for (DexEncodedMethod method : collectionDispatch.methods()) {
        int numCheckCast =
            (int)
                Stream.of(method.getCode().asDexCode().instructions)
                    .filter(Instruction::isCheckCast)
                    .count();
        if (method.qualifiedName().contains("spliterator")) {
          assertEquals(5, numCheckCast);
        } else {
          assertEquals(1, numCheckCast);
        }
      }
    }
  }

  private void checkRewrittenInvokes(CodeInspector inspector) {
    if (!requiresCoreLibDesugaring(parameters)) {
      return;
    }
    ClassSubject classSubject = inspector.clazz("stream.TestClass");
    assertThat(classSubject, isPresent());
    List<InstructionSubject> invokes =
        classSubject
            .uniqueMethodWithName("main")
            .streamInstructions()
            .filter(instr -> instr.isInvokeInterface() || instr.isInvokeStatic())
            .collect(Collectors.toList());
    assertEquals(23, invokes.size());
    assertTrue(invokes.get(0).isInvokeStatic());
    assertTrue(invokes.get(0).toString().contains("Set$-EL;->spliterator"));
    assertTrue(invokes.get(1).isInvokeStatic());
    assertTrue(invokes.get(1).toString().contains("List$-EL;->spliterator"));
    assertTrue(invokes.get(2).isInvokeStatic());
    assertTrue(invokes.get(2).toString().contains("Collection$-EL;->stream"));
    assertTrue(invokes.get(3).isInvokeInterface());
    assertTrue(invokes.get(3).toString().contains("Set;->iterator"));
    assertTrue(invokes.get(4).isInvokeStatic());
    assertTrue(invokes.get(4).toString().contains("Collection$-EL;->stream"));
    assertTrue(invokes.get(5).isInvokeStatic());
    assertTrue(invokes.get(5).toString().contains("DesugarLinkedHashSet;->spliterator"));
    assertTrue(invokes.get(9).isInvokeInterface());
    assertTrue(invokes.get(9).toString().contains("Iterator;->remove"));
    assertTrue(invokes.get(10).isInvokeStatic());
    assertTrue(invokes.get(10).toString().contains("DesugarArrays;->spliterator"));
    assertTrue(invokes.get(11).isInvokeStatic());
    assertTrue(invokes.get(11).toString().contains("DesugarArrays;->spliterator"));
    assertTrue(invokes.get(12).isInvokeStatic());
    assertTrue(invokes.get(12).toString().contains("DesugarArrays;->stream"));
    assertTrue(invokes.get(13).isInvokeStatic());
    assertTrue(invokes.get(13).toString().contains("DesugarArrays;->stream"));
    assertTrue(invokes.get(14).isInvokeStatic());
    assertTrue(invokes.get(14).toString().contains("Collection$-EL;->stream"));
    assertTrue(invokes.get(15).isInvokeStatic());
    assertTrue(invokes.get(15).toString().contains("IntStream$-CC;->range"));
    assertTrue(invokes.get(17).isInvokeStatic());
    assertTrue(invokes.get(17).toString().contains("Comparator$-CC;->comparingInt"));
    assertTrue(invokes.get(18).isInvokeStatic());
    assertTrue(invokes.get(18).toString().contains("List$-EL;->sort"));
    assertTrue(invokes.get(20).isInvokeStatic());
    assertTrue(invokes.get(20).toString().contains("Comparator$-CC;->comparingInt"));
    assertTrue(invokes.get(21).isInvokeStatic());
    assertTrue(invokes.get(21).toString().contains("List$-EL;->sort"));
    assertTrue(invokes.get(22).isInvokeStatic());
    assertTrue(invokes.get(22).toString().contains("Collection$-EL;->stream"));
    // TODO (b/134732760): Support Java 9 Stream APIs
    // assertTrue(invokes.get(17).isInvokeStatic());
    // assertTrue(invokes.get(17).toString().contains("Stream$-CC;->iterate"));
  }

  @Test
  public void testProgram() throws Exception {
    Assume.assumeTrue("No desugaring for high API levels", requiresCoreLibDesugaring(parameters));
    D8TestRunResult d8TestRunResult =
        testForD8()
            .addProgramFiles(Paths.get(ToolHelper.EXAMPLES_JAVA9_BUILD_DIR + "stream.jar"))
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
            .setMinApi(parameters.getApiLevel())
            .enableCoreLibraryDesugaring()
            .compile()
            .inspect(this::checkRewrittenInvokes)
            .addRunClasspathFiles(buildDesugaredLibrary(parameters.getApiLevel()))
            .run(parameters.getRuntime(), "stream.TestClass")
            .assertSuccess();
    assertLines2By2Correct(d8TestRunResult.getStdOut());
    String stdErr = d8TestRunResult.getStdErr();
    if (parameters.getRuntime().asDex().getVm().isOlderThanOrEqual(DexVm.ART_4_4_4_HOST)) {
      // Flaky: There might be a missing method on lambda deserialization.
      assertTrue(
          !stdErr.contains("Could not find method")
              || stdErr.contains("Could not find method java.lang.invoke.SerializedLambda"));
    } else {
      assertFalse(stdErr.contains("Could not find method"));
    }
  }

  private void assertLines2By2Correct(String stdOut) {
    String[] lines = stdOut.split("\n");
    assert lines.length % 2 == 0;
    for (int i = 0; i < lines.length; i += 2) {
      assertEquals(lines[i], lines[i + 1]);
    }
  }
}
