// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.corelib;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;

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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
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
    ClassSubject classSubject = inspector.clazz(TestClass.class);
    assertThat(classSubject, isPresent());
    List<InstructionSubject> invokes =
        classSubject
            .uniqueMethodWithName("main")
            .streamInstructions()
            .filter(instr -> instr.isInvokeInterface() || instr.isInvokeStatic())
            .collect(Collectors.toList());
    assertEquals(14, invokes.size());
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
            "j$.util.stream.ReferencePipeline$Head",
            "j$.util.Spliterators$IteratorSpliterator",
            "j$.util.Spliterators$ArraySpliterator",
            "j$.util.Spliterators$ArraySpliterator",
            "j$.util.stream.ReferencePipeline$Head",
            "j$.util.stream.ReferencePipeline$Head");
    String stdErr =
        testForD8()
            .addProgramClasses(TestClass.class)
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
            .setMinApi(parameters.getRuntime())
            .addOptionsModification(this::configureCoreLibDesugarForProgramCompilation)
            .compile()
            .inspect(this::checkRewrittenInvokes)
            .addRunClasspathFiles(buildDesugaredLibrary(parameters.getRuntime()))
            .run(parameters.getRuntime(), TestClass.class)
            .assertSuccessWithOutput(expectedOutput)
            .getStdErr();
    assertFalse(stdErr.contains("Could not find method"));
  }

  static class TestClass {
    public static void main(String[] args) {
      Set<Object> set = new HashSet<>();
      List<Object> list = new ArrayList<>();
      Queue<Object> queue = new LinkedList<>();
      LinkedHashSet<Object> lhs = new LinkedHashSet<>();
      // They both should be rewritten to invokeStatic to the dispatch class.
      System.out.println(set.spliterator().getClass().getName());
      System.out.println(list.spliterator().getClass().getName());
      // Following should be rewritten to invokeStatic to Collection dispatch class.
      System.out.println(set.stream().getClass().getName());
      // Following should not be rewritten.
      System.out.println(set.iterator().getClass().getName());
      // Following should be rewritten to invokeStatic to Collection dispatch class.
      System.out.println(queue.stream().getClass().getName());
      // Following should be rewritten as retarget core lib member.
      System.out.println(lhs.spliterator().getClass().getName());
      // Remove follows the don't rewrite rule.
      list.add(new Object());
      Iterator iterator = list.iterator();
      iterator.next();
      iterator.remove();
      // Static methods (same name, different signatures).
      System.out.println(Arrays.spliterator(new Object[] {new Object()}).getClass().getName());
      System.out.println(
          Arrays.spliterator(new Object[] {new Object()}, 0, 0).getClass().getName());
      System.out.println(Arrays.stream(new Object[] {new Object()}).getClass().getName());
      System.out.println(Arrays.stream(new Object[] {new Object()}, 0, 0).getClass().getName());
    }
  }
}
