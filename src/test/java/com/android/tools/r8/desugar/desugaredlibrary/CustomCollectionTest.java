// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.SPECIFICATIONS_WITH_CF2CF;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CustomCollectionTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final CompilationSpecification compilationSpecification;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        getJdk8Jdk11(),
        SPECIFICATIONS_WITH_CF2CF);
  }

  public CustomCollectionTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.compilationSpecification = compilationSpecification;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
  }

  @Test
  public void testCollection() throws Throwable {
    String stdOut =
        testForDesugaredLibrary(
                parameters, libraryDesugaringSpecification, compilationSpecification)
            .addInnerClasses(getClass())
            .addKeepMainRule(Executor.class)
            .compile()
            .inspect(this::assertCustomCollectionCallsCorrect)
            .run(parameters.getRuntime(), Executor.class)
            .assertSuccess()
            .getStdOut();
    assertResultCorrect(stdOut);
  }

  private void assertResultCorrect(String stdOut) {
    if (libraryDesugaringSpecification.hasEmulatedInterfaceDesugaring(parameters)
        && !compilationSpecification.isL8Shrink()) {
      // When shrinking the class names are not printed correctly anymore due to minification.
      // Expected output is emulated interfaces expected output.
      assertLines2By2Correct(stdOut);
    }
  }

  private void assertCustomCollectionCallsCorrect(CodeInspector inspector) {
    if (compilationSpecification.isProgramShrink()) {
      return;
    }
    MethodSubject direct =
        inspector.clazz(Executor.class).uniqueMethodWithOriginalName("directTypes");
    if (libraryDesugaringSpecification.hasEmulatedInterfaceDesugaring(parameters)) {
      assertTrue(
          direct
              .streamInstructions()
              .filter(InstructionSubject::isInvokeStatic)
              .allMatch(
                  instr ->
                      instr.toString().contains("$-EL")
                          || instr.toString().contains("Comparator$-CC")
                          || instr.toString().contains("Stream$-CC")));
    } else {
      assertTrue(direct.streamInstructions().noneMatch(instr -> instr.toString().contains("$-EL")));
    }
    MethodSubject inherited =
        inspector.clazz(Executor.class).uniqueMethodWithOriginalName("inheritedTypes");
    if (!libraryDesugaringSpecification.hasEmulatedInterfaceDesugaring(parameters)) {
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
                        || instr.toString().contains("Comparator$-CC")
                        || instr.toString().contains("Stream$-CC")));
    inherited.streamInstructions().forEach(this::assertInheritedDispatchCorrect);
  }

  private void assertInheritedDispatchCorrect(InstructionSubject instructionSubject) {
    if (!instructionSubject.isConstString(JumboStringMode.ALLOW)) {
      for (String s : new String[] {">stream", "spliterator", "sort"}) {
        if (instructionSubject.toString().contains(s)) {
            assertTrue(instructionSubject.isInvokeStatic());
            assertTrue(
                instructionSubject.toString().contains("$-EL")
                    || instructionSubject.toString().contains("Comparator$-CC"));
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
      // See SpliteratorTest for the concrete class name.
      System.out.println(cArrayList.spliterator().getClass().getName());
      System.out.println(new ArrayList<>().spliterator().getClass().getName());
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
      // See SpliteratorTest for the concrete class name.
      System.out.println(new ArrayList<>().spliterator().getClass().getName());
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

    @Override
    public Iterator<E> iterator() {
      return Collections.emptyIterator();
    }

    @Override
    public Object[] toArray() {
      return new Object[0];
    }

    @Override
    public <T> T[] toArray(T[] a) {
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
    public boolean containsAll(Collection<?> c) {
      return false;
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
      return false;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
      return false;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
      return false;
    }

    @Override
    public void clear() {}
  }

  // Extends directly a core library class which implements other library interfaces.
  private static class CustomArrayList<E> extends ArrayList<E> {}

  // Implements directly a core library interface which implements other library interfaces.
  static class CustomSortedSet<E> implements SortedSet<E> {

    @Override
    public Comparator<? super E> comparator() {
      return null;
    }

    @Override
    public SortedSet<E> subSet(E fromElement, E toElement) {
      return new CustomSortedSet<>();
    }

    @Override
    public SortedSet<E> headSet(E toElement) {
      return new CustomSortedSet<>();
    }

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

    @Override
    public Iterator<E> iterator() {
      return Collections.emptyIterator();
    }

    @Override
    public Object[] toArray() {
      return new Object[0];
    }

    @Override
    public <T> T[] toArray(T[] a) {
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
    public boolean addAll(Collection c) {
      return false;
    }

    @Override
    public void clear() {}

    @Override
    public boolean removeAll(Collection c) {
      return false;
    }

    @Override
    public boolean retainAll(Collection c) {
      return false;
    }

    @Override
    public boolean containsAll(Collection c) {
      return false;
    }
  }

  // Extends a custom class implementing a core library interface which is a subinterface of
  // the core library interface implemented here.
  // This tests some edge case in nearestEmulatedInterfaceImplementing.
  private static class CustomSortedSetWithReverseChain<E> extends CustomSortedSet<E>
      implements Collection<E> {}
}
