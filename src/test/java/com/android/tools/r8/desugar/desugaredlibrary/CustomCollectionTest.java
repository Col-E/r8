// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.D8TestRunResult;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject.JumboStringMode;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CustomCollectionTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final boolean shrinkDesugaredLibrary;

  @Parameters(name = "{1}, shrinkDesugaredLibrary: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withDexRuntimes().withAllApiLevels().build());
  }

  public CustomCollectionTest(boolean shrinkDesugaredLibrary, TestParameters parameters) {
    this.shrinkDesugaredLibrary = shrinkDesugaredLibrary;
    this.parameters = parameters;
  }

  private final String EXECUTOR =
      "com.android.tools.r8.desugar.desugaredlibrary.CustomCollectionTest$Executor";

  @Test
  public void testCustomCollectionD8() throws Exception {
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    D8TestRunResult d8TestRunResult =
        testForD8()
            .addInnerClasses(CustomCollectionTest.class)
            .setMinApi(parameters.getApiLevel())
            .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
            .compile()
            .assertNoMessages()
            .inspect(
                inspector -> {
                  this.assertCustomCollectionCallsCorrect(inspector, false);
                })
            .addDesugaredCoreLibraryRunClassPath(
                this::buildDesugaredLibrary,
                parameters.getApiLevel(),
                keepRuleConsumer.get(),
                shrinkDesugaredLibrary)
            .run(parameters.getRuntime(), EXECUTOR)
            .assertSuccess();
    assertResultCorrect(d8TestRunResult.getStdOut(), d8TestRunResult.getStdErr());
  }

  private void assertResultCorrect(String stdOut, String stdErr) {
    if (requiresEmulatedInterfaceCoreLibDesugaring(parameters) && !shrinkDesugaredLibrary) {
      // When shrinking the class names are not printed correctly anymore due to minification.
      // Expected output is emulated interfaces expected output.
      assertLines2By2Correct(stdOut);
    }
  }

  @Test
  public void testCustomCollectionR8() throws Exception {
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    R8TestRunResult r8TestRunResult =
        testForR8(Backend.DEX)
            .addInnerClasses(CustomCollectionTest.class)
            .setMinApi(parameters.getApiLevel())
            .addKeepClassAndMembersRules(Executor.class)
            .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
            .compile()
            .inspect(
                inspector -> {
                  this.assertCustomCollectionCallsCorrect(inspector, true);
                })
            .addDesugaredCoreLibraryRunClassPath(
                this::buildDesugaredLibrary,
                parameters.getApiLevel(),
                keepRuleConsumer.get(),
                shrinkDesugaredLibrary)
            .run(parameters.getRuntime(), EXECUTOR)
            .assertSuccess();
    assertResultCorrect(r8TestRunResult.getStdOut(), r8TestRunResult.getStdErr());
  }

  private void assertCustomCollectionCallsCorrect(CodeInspector inspector, boolean r8) {
    MethodSubject direct = inspector.clazz(EXECUTOR).uniqueMethodWithName("directTypes");
    // TODO(b/134732760): Due to memberRebinding, R8 is not as precise as D8 regarding
    // desugaring of invokes. This will be fixed when creation of desugared method is moved
    // ahead of R8 compilation pipeline.
    if (!r8) {
      Assert.assertFalse(
          direct.streamInstructions().anyMatch(instr -> instr.toString().contains("$-EL")));
    } else if (requiresEmulatedInterfaceCoreLibDesugaring(parameters)) {
      assertTrue(
          direct
              .streamInstructions()
              .filter(InstructionSubject::isInvokeStatic)
              .allMatch(
                  instr ->
                      instr.toString().contains("$-EL")
                          || instr.toString().contains("Comparator$-CC")));
    } else {
      assertTrue(direct.streamInstructions().noneMatch(instr -> instr.toString().contains("$-EL")));
    }
    MethodSubject inherited = inspector.clazz(EXECUTOR).uniqueMethodWithName("inheritedTypes");
    if (!requiresEmulatedInterfaceCoreLibDesugaring(parameters)) {
      assertTrue(
          inherited.streamInstructions().noneMatch(instr -> instr.toString().contains("$-EL")));
      return;
    }
    assertTrue(
        inherited
            .streamInstructions()
            .filter(InstructionSubject::isInvokeStatic)
            .allMatch(
                instr ->
                    instr.toString().contains("$-EL")
                        || instr.toString().contains("Comparator$-CC")));
    inherited.streamInstructions().forEach(x -> assertInheritedDispatchCorrect(x, r8));
  }

  private void assertInheritedDispatchCorrect(InstructionSubject instructionSubject, boolean r8) {
    if (!instructionSubject.isConstString(JumboStringMode.ALLOW)) {
      for (String s : new String[] {"stream", "parallelStream", "spliterator", "sort"}) {
        if (instructionSubject.toString().contains(s)) {
          if (!r8 || instructionSubject.isInvokeStatic()) {
            assertTrue(instructionSubject.isInvokeStatic());
            assertTrue(
                instructionSubject.toString().contains("$-EL")
                    || instructionSubject.toString().contains("Comparator$-CC"));
          } else {
            // Has been devirtualized.
            assertTrue(instructionSubject.isInvokeVirtual());
          }
        }
      }
    }
  }


  static class Executor {

    // In directTypes() the collections use directly their type which implements a j$ interface
    // (Program classes
    // implementing emulated interfaces are rewritten to also implement the j$ interface). The
    // invokes
    // can therefore remain (desugared though companion classes).
    static void directTypes() {
      CustomCollection<Object> ccollection = new CustomCollection<>();
      CustomArrayList<Object> cArrayList = new CustomArrayList<>();
      CustomSortedSet<Object> cSortedSet = new CustomSortedSet<>();
      CustomSortedSetWithReverseChain<Object> customSortedSetWithReverseChain =
          new CustomSortedSetWithReverseChain<>();

      System.out.println(ccollection.stream().getClass().getName());
      System.out.println("j$.util.stream.ReferencePipeline$Head");
      System.out.println(cArrayList.stream().getClass().getName());
      System.out.println("j$.util.stream.ReferencePipeline$Head");
      System.out.println(cSortedSet.stream().getClass().getName());
      System.out.println("j$.util.stream.ReferencePipeline$Head");
      System.out.println(customSortedSetWithReverseChain.stream().getClass().getName());
      System.out.println("j$.util.stream.ReferencePipeline$Head");

      cArrayList.sort(Comparator.comparingInt(Object::hashCode));

      System.out.println(ccollection.parallelStream().getClass().getName());
      System.out.println("j$.util.stream.ReferencePipeline$Head");

      System.out.println(ccollection.spliterator().getClass().getName());
      System.out.println("j$.util.Spliterators$IteratorSpliterator");
      System.out.println(cArrayList.spliterator().getClass().getName());
      System.out.println("j$.util.Spliterators$IteratorSpliterator");
      System.out.println(cSortedSet.spliterator().getClass().getName());
      System.out.println("j$.util.SortedSet$1");
      System.out.println(customSortedSetWithReverseChain.spliterator().getClass().getName());
      System.out.println("j$.util.SortedSet$1");
    }

    // In inherited types the collection use core library types. The invokes have to be rewritten to
    // call the $-EL
    // class to dispatch the call, we do not know if the resulting class is program or core library.
    static void inheritedTypes() {
      Collection<Object> ccollection = new CustomCollection<>();
      ArrayList<Object> cArrayList = new CustomArrayList<>();
      SortedSet<Object> cSortedSet = new CustomSortedSet<>();
      SortedSet<Object> customSortedSetWithReverseChain = new CustomSortedSetWithReverseChain<>();
      Collection<Object> cSortedSetCol = new CustomSortedSet<>();
      Collection<Object> customSortedSetWithReverseChainCol =
          new CustomSortedSetWithReverseChain<>();

      System.out.println(ccollection.stream().getClass().getName());
      System.out.println("j$.util.stream.ReferencePipeline$Head");
      System.out.println(cArrayList.stream().getClass().getName());
      System.out.println("j$.util.stream.ReferencePipeline$Head");
      System.out.println(cSortedSet.stream().getClass().getName());
      System.out.println("j$.util.stream.ReferencePipeline$Head");
      System.out.println(customSortedSetWithReverseChain.stream().getClass().getName());
      System.out.println("j$.util.stream.ReferencePipeline$Head");
      System.out.println(cSortedSetCol.stream().getClass().getName());
      System.out.println("j$.util.stream.ReferencePipeline$Head");
      System.out.println(customSortedSetWithReverseChainCol.stream().getClass().getName());
      System.out.println("j$.util.stream.ReferencePipeline$Head");

      cArrayList.sort(Comparator.comparingInt(Object::hashCode));

      System.out.println(ccollection.parallelStream().getClass().getName());
      System.out.println("j$.util.stream.ReferencePipeline$Head");

      System.out.println(ccollection.spliterator().getClass().getName());
      System.out.println("j$.util.Spliterators$IteratorSpliterator");
      System.out.println(cArrayList.spliterator().getClass().getName());
      System.out.println("j$.util.Spliterators$IteratorSpliterator");
      System.out.println(cSortedSet.spliterator().getClass().getName());
      System.out.println("j$.util.SortedSet$1");
      System.out.println(customSortedSetWithReverseChain.spliterator().getClass().getName());
      System.out.println("j$.util.SortedSet$1");
      System.out.println(cSortedSetCol.spliterator().getClass().getName());
      System.out.println("j$.util.SortedSet$1");
      System.out.println(customSortedSetWithReverseChainCol.spliterator().getClass().getName());
      System.out.println("j$.util.SortedSet$1");
    }

    public static void main(String[] args) {
      directTypes();
      System.out.println();
      System.out.println();
      inheritedTypes();
    }
  }

  // Implements directly a core library interface which does not implement other library interfaces.
  // Among the default methods, only parallelStream is overriden.
  static class CustomCollection<E> implements Collection<E> {

    // Custom override
    @Override
    public Stream<E> parallelStream() {
      return Stream.empty();
    }

    @Override
    public int size() {
      return 0;
    }

    @Override
    public boolean isEmpty() {
      return false;
    }

    @Override
    public boolean contains(Object o) {
      return false;
    }

    @NotNull
    @Override
    public Iterator<E> iterator() {
      return Collections.emptyIterator();
    }

    @NotNull
    @Override
    public Object[] toArray() {
      return new Object[0];
    }

    @NotNull
    @Override
    public <T> T[] toArray(@NotNull T[] a) {
      return a;
    }

    @Override
    public boolean add(E e) {
      return false;
    }

    @Override
    public boolean remove(Object o) {
      return false;
    }

    @Override
    public boolean containsAll(@NotNull Collection<?> c) {
      return false;
    }

    @Override
    public boolean addAll(@NotNull Collection<? extends E> c) {
      return false;
    }

    @Override
    public boolean removeAll(@NotNull Collection<?> c) {
      return false;
    }

    @Override
    public boolean retainAll(@NotNull Collection<?> c) {
      return false;
    }

    @Override
    public void clear() {}
  }

  // Extends directly a core library class which implements other library interfaces.
  private static class CustomArrayList<E> extends ArrayList<E> {}

  // Implements directly a core library interface which implements other library interfaces.
  static class CustomSortedSet<E> implements SortedSet<E> {

    @Nullable
    @Override
    public Comparator<? super E> comparator() {
      return null;
    }

    @NotNull
    @Override
    public SortedSet<E> subSet(E fromElement, E toElement) {
      return new CustomSortedSet<>();
    }

    @NotNull
    @Override
    public SortedSet<E> headSet(E toElement) {
      return new CustomSortedSet<>();
    }

    @NotNull
    @Override
    public SortedSet<E> tailSet(E fromElement) {
      return new CustomSortedSet<>();
    }

    @Override
    public E first() {
      return null;
    }

    @Override
    public E last() {
      return null;
    }

    @Override
    public int size() {
      return 0;
    }

    @Override
    public boolean isEmpty() {
      return false;
    }

    @Override
    public boolean contains(Object o) {
      return false;
    }

    @NotNull
    @Override
    public Iterator<E> iterator() {
      return Collections.emptyIterator();
    }

    @NotNull
    @Override
    public Object[] toArray() {
      return new Object[0];
    }

    @NotNull
    @Override
    public <T> T[] toArray(@NotNull T[] a) {
      return a;
    }

    @Override
    public boolean add(Object o) {
      return false;
    }

    @Override
    public boolean remove(Object o) {
      return false;
    }

    @Override
    public boolean addAll(@NotNull Collection c) {
      return false;
    }

    @Override
    public void clear() {}

    @Override
    public boolean removeAll(@NotNull Collection c) {
      return false;
    }

    @Override
    public boolean retainAll(@NotNull Collection c) {
      return false;
    }

    @Override
    public boolean containsAll(@NotNull Collection c) {
      return false;
    }
  }

  // Extends a custom class implementing a core library interface which is a subinterface of
  // the core library interface implemented here.
  // This tests some edge case in nearestEmulatedInterfaceImplementing.
  private static class CustomSortedSetWithReverseChain<E> extends CustomSortedSet<E>
      implements Collection<E> {}
}
