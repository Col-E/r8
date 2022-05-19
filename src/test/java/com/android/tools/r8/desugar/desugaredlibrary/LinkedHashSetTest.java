// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8DEBUG;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.google.common.collect.ImmutableList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Predicate;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LinkedHashSetTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        getJdk8Jdk11(),
        ImmutableList.of(D8_L8DEBUG));
  }

  public LinkedHashSetTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @Test
  public void testLinkedHashSet() throws Throwable {
    String stdOut =
        testForDesugaredLibrary(
                parameters, libraryDesugaringSpecification, compilationSpecification)
            .addInnerClasses(getClass())
            .run(parameters.getRuntime(), Executor.class)
            .assertSuccess()
            .getStdOut();
    assertLines2By2Correct(stdOut);
  }

  @SuppressWarnings("WeakerAccess")
  static class Executor {

    public static void main(String[] args) {
      // Spliterator of Set is only distinct.
      // Spliterator of LinkedHashSet is distinct and ordered.
      // Spliterator of CustomLinkedHashSetOverride is distinct, ordered and immutable.
      // If an incorrect method is found, characteristics are incorrect.
      rawTypes();
      linkedHashSetType();
      setType();
    }

    @SuppressWarnings("RedundantOperationOnEmptyContainer")
    public static void rawTypes() {
      Spliterator<String> spliterator;

      spliterator = new LinkedHashSet<String>().spliterator();
      System.out.println(spliterator.hasCharacteristics(Spliterator.DISTINCT));
      System.out.println("true");
      System.out.println(spliterator.hasCharacteristics(Spliterator.ORDERED));
      System.out.println("true");
      System.out.println(spliterator.hasCharacteristics(Spliterator.IMMUTABLE));
      System.out.println("false");

      spliterator = new CustomLinkedHashSetOverride<String>().spliterator();
      System.out.println(spliterator.hasCharacteristics(Spliterator.DISTINCT));
      System.out.println("true");
      System.out.println(spliterator.hasCharacteristics(Spliterator.ORDERED));
      System.out.println("true");
      System.out.println(spliterator.hasCharacteristics(Spliterator.IMMUTABLE));
      System.out.println("true");

      spliterator = new CustomLinkedHashSetNoOverride<String>().spliterator();
      System.out.println(spliterator.hasCharacteristics(Spliterator.DISTINCT));
      System.out.println("true");
      System.out.println(spliterator.hasCharacteristics(Spliterator.ORDERED));
      System.out.println("true");
      System.out.println(spliterator.hasCharacteristics(Spliterator.IMMUTABLE));
      System.out.println("false");

      spliterator = new CustomLinkedHashSetOverride<String>().superSpliterator();
      System.out.println(spliterator.hasCharacteristics(Spliterator.DISTINCT));
      System.out.println("true");
      System.out.println(spliterator.hasCharacteristics(Spliterator.ORDERED));
      System.out.println("true");
      System.out.println(spliterator.hasCharacteristics(Spliterator.IMMUTABLE));
      System.out.println("false");

      spliterator = new SubclassOverride<String>().superSpliterator();
      System.out.println(spliterator.hasCharacteristics(Spliterator.DISTINCT));
      System.out.println("true");
      System.out.println(spliterator.hasCharacteristics(Spliterator.ORDERED));
      System.out.println("true");
      System.out.println(spliterator.hasCharacteristics(Spliterator.IMMUTABLE));
      System.out.println("true");

      spliterator = new SubclassNoOverride<String>().superSpliterator();
      System.out.println(spliterator.hasCharacteristics(Spliterator.DISTINCT));
      System.out.println("true");
      System.out.println(spliterator.hasCharacteristics(Spliterator.ORDERED));
      System.out.println("true");
      System.out.println(spliterator.hasCharacteristics(Spliterator.IMMUTABLE));
      System.out.println("false");
    }

    @SuppressWarnings("RedundantOperationOnEmptyContainer")
    public static void linkedHashSetType() {
      System.out.println("-");
      System.out.println("-");
      Spliterator<String> spliterator;

      spliterator =
          ((LinkedHashSet<String>) new CustomLinkedHashSetOverride<String>()).spliterator();
      System.out.println(spliterator.hasCharacteristics(Spliterator.DISTINCT));
      System.out.println("true");
      System.out.println(spliterator.hasCharacteristics(Spliterator.ORDERED));
      System.out.println("true");
      System.out.println(spliterator.hasCharacteristics(Spliterator.IMMUTABLE));
      System.out.println("true");

      spliterator =
          ((LinkedHashSet<String>) new CustomLinkedHashSetNoOverride<String>()).spliterator();
      System.out.println(spliterator.hasCharacteristics(Spliterator.DISTINCT));
      System.out.println("true");
      System.out.println(spliterator.hasCharacteristics(Spliterator.ORDERED));
      System.out.println("true");
      System.out.println(spliterator.hasCharacteristics(Spliterator.IMMUTABLE));
      System.out.println("false");
    }

    @SuppressWarnings("RedundantOperationOnEmptyContainer")
    public static void setType() {
      System.out.println("-");
      System.out.println("-");
      Spliterator<String> spliterator;

      spliterator = ((Set<String>) new LinkedHashSet<String>()).spliterator();
      System.out.println(spliterator.hasCharacteristics(Spliterator.DISTINCT));
      System.out.println("true");
      System.out.println(spliterator.hasCharacteristics(Spliterator.ORDERED));
      System.out.println("true");
      System.out.println(spliterator.hasCharacteristics(Spliterator.IMMUTABLE));
      System.out.println("false");

      spliterator = ((Set<String>) new CustomLinkedHashSetOverride<String>()).spliterator();
      System.out.println(spliterator.hasCharacteristics(Spliterator.DISTINCT));
      System.out.println("true");
      System.out.println(spliterator.hasCharacteristics(Spliterator.ORDERED));
      System.out.println("true");
      System.out.println(spliterator.hasCharacteristics(Spliterator.IMMUTABLE));
      System.out.println("true");

      spliterator = ((Set<String>) new CustomLinkedHashSetNoOverride<String>()).spliterator();
      System.out.println(spliterator.hasCharacteristics(Spliterator.DISTINCT));
      System.out.println("true");
      System.out.println(spliterator.hasCharacteristics(Spliterator.ORDERED));
      System.out.println("true");
      System.out.println(spliterator.hasCharacteristics(Spliterator.IMMUTABLE));
      System.out.println("false");

      spliterator = ((Set<String>) new CustomLinkedHashSetForceForwarding<String>()).spliterator();
      System.out.println(spliterator.hasCharacteristics(Spliterator.DISTINCT));
      System.out.println("true");
      System.out.println(spliterator.hasCharacteristics(Spliterator.ORDERED));
      System.out.println("true");
      System.out.println(spliterator.hasCharacteristics(Spliterator.IMMUTABLE));
      System.out.println("false");
    }
  }

  static class CustomLinkedHashSetOverride<E> extends LinkedHashSet<E> {

    @Override
    public Spliterator<E> spliterator() {
      return Spliterators.spliterator(
          this, Spliterator.DISTINCT | Spliterator.ORDERED | Spliterator.IMMUTABLE);
    }

    public Spliterator<E> superSpliterator() {
      return super.spliterator();
    }
  }

  static class SubclassOverride<E> extends CustomLinkedHashSetOverride<E> {

    @Override
    public Spliterator<E> superSpliterator() {
      return super.spliterator();
    }
  }

  @SuppressWarnings("WeakerAccess")
  static class CustomLinkedHashSetNoOverride<E> extends LinkedHashSet<E> {}

  static class SubclassNoOverride<E> extends CustomLinkedHashSetNoOverride<E> {

    public Spliterator<E> superSpliterator() {
      return super.spliterator();
    }
  }

  // The over method force the forwarding methods to be installed.
  static class CustomLinkedHashSetForceForwarding<E> extends LinkedHashSet<E> {

    @Override
    public boolean removeIf(Predicate<? super E> filter) {
      return super.removeIf(filter);
    }
  }
}
