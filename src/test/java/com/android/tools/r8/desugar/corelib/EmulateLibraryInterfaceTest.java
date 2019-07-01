// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.corelib;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.code.Instruction;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.ir.desugar.InterfaceMethodRewriter;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
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
    return getTestParameters().withDexRuntimes().build();
  }

  public EmulateLibraryInterfaceTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testDispatchClasses() throws Exception {
    CodeInspector inspector = new CodeInspector(buildDesugaredLibrary(parameters.getRuntime()));
    List<FoundClassSubject> dispatchClasses =
        inspector.allClasses().stream()
            .filter(
                clazz ->
                    clazz
                        .getOriginalName()
                        .contains(InterfaceMethodRewriter.EMULATE_LIBRARY_CLASS_NAME_SUFFIX))
            .collect(Collectors.toList());
    List<FoundClassSubject> java =
        inspector.allClasses().stream()
            .filter(x -> x.getOriginalName().startsWith("java"))
            .collect(Collectors.toList());
    System.out.println(java);
    int numDispatchClasses =
        requiresCoreLibDesugaring(parameters) ? buildEmulateLibraryInterface().size() : 0;
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
          assertTrue(numCheckCast > 1);
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
    ClassSubject classSubject = inspector.clazz(TestClass.class);
    assertThat(classSubject, isPresent());
    List<InstructionSubject> invokes =
        classSubject
            .uniqueMethodWithName("main")
            .streamInstructions()
            .filter(instr -> instr.isInvokeInterface() || instr.isInvokeStatic())
            .collect(Collectors.toList());
    assertTrue(invokes.get(0).isInvokeStatic());
    assertTrue(invokes.get(0).toString().contains("Set$-EL;->"));
    assertTrue(invokes.get(1).isInvokeStatic());
    assertTrue(invokes.get(1).toString().contains("List$-EL;->"));
    assertTrue(invokes.get(2).isInvokeStatic());
    assertTrue(invokes.get(2).toString().contains("Collection$-EL;->"));
    assertTrue(invokes.get(3).isInvokeInterface());
    assertTrue(invokes.get(3).toString().contains("Set;->"));
    assertTrue(invokes.get(4).isInvokeStatic());
    assertTrue(invokes.get(4).toString().contains("Collection$-EL;->"));
  }

  @Test
  public void testProgram() throws Exception {
    Assume.assumeTrue("TODO(134732760): Fix Android 7+.", requiresCoreLibDesugaring(parameters));
    String expectedOutput =
        StringUtils.lines(
            "j$.util.Spliterators$IteratorSpliterator",
            "j$.util.Spliterators$IteratorSpliterator",
            "j$.util.stream.ReferencePipeline$Head",
            "java.util.HashMap$KeyIterator",
            "j$.util.stream.ReferencePipeline$Head");
    testForD8()
        .addProgramClasses(TestClass.class)
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
        .setMinApi(parameters.getRuntime())
        .addOptionsModification(this::configureCoreLibDesugarForProgramCompilation)
        .compile()
        .inspect(this::checkRewrittenInvokes)
        .addRunClasspathFiles(buildDesugaredLibrary(parameters.getRuntime()))
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(expectedOutput);
  }

  static class TestClass {
    public static void main(String[] args) {
      Set<Object> set = new HashSet<>();
      List<Object> list = new ArrayList<>();
      Queue<Object> queue = new LinkedList<>();
      // They both should be rewritten to invokeStatic to the dispatch class.
      System.out.println(set.spliterator().getClass().getName());
      System.out.println(list.spliterator().getClass().getName());
      // Following should be rewritten to invokeStatic to Collection dispatch class.
      System.out.println(set.stream().getClass().getName());
      // Following should not be rewritten.
      System.out.println(set.iterator().getClass().getName());
      // Following should be rewritten to invokeStatic to Collection dispatch class.
      System.out.println(queue.stream().getClass().getName());
    }
  }
}
