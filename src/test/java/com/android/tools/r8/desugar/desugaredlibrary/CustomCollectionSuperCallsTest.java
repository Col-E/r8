// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.SPECIFICATIONS_WITH_CF2CF;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import java.util.ArrayList;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CustomCollectionSuperCallsTest extends DesugaredLibraryTestBase {

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

  public CustomCollectionSuperCallsTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.compilationSpecification = compilationSpecification;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
  }

  @Test
  public void testCollection() throws Exception {
    String stdOut =
        testForDesugaredLibrary(
                parameters, libraryDesugaringSpecification, compilationSpecification)
            .addInnerClasses(getClass())
            .addKeepMainRule(Executor.class)
            .run(parameters.getRuntime(), Executor.class)
            .assertSuccess()
            .getStdOut();
    assertLines2By2Correct(stdOut);
  }

  static class Executor {

    // ArrayList spliterator is Spliterator.ORDERED | Spliterator.SIZED | Spliterator.SUBSIZED.

    public static void main(String[] args) {
      rawTypes();
      inheritedTypes();
    }

    public static void rawTypes() {
      Spliterator<String> stringSpliterator;

      stringSpliterator = new MyArrayListOverride().superSpliterator();
      System.out.println(stringSpliterator.hasCharacteristics(Spliterator.ORDERED));
      System.out.println(true);
      System.out.println(stringSpliterator.hasCharacteristics(Spliterator.IMMUTABLE));
      System.out.println(false);

      stringSpliterator = new MyArrayListOverrideSubclass().superSpliterator();
      System.out.println(stringSpliterator.hasCharacteristics(Spliterator.ORDERED));
      System.out.println(false);
      System.out.println(stringSpliterator.hasCharacteristics(Spliterator.IMMUTABLE));
      System.out.println(true);

      stringSpliterator = new MyArrayListNoOverride().superSpliterator();
      System.out.println(stringSpliterator.hasCharacteristics(Spliterator.ORDERED));
      System.out.println(true);
      System.out.println(stringSpliterator.hasCharacteristics(Spliterator.IMMUTABLE));
      System.out.println(false);

      stringSpliterator = new MyArrayListSubclassNoOverride().superSpliterator();
      System.out.println(stringSpliterator.hasCharacteristics(Spliterator.ORDERED));
      System.out.println(true);
      System.out.println(stringSpliterator.hasCharacteristics(Spliterator.IMMUTABLE));
      System.out.println(false);
    }

    public static void inheritedTypes() {
      Spliterator<String> stringSpliterator;

      stringSpliterator =
          ((MyArrayListOverride) new MyArrayListOverrideSubclass()).superSpliterator();
      System.out.println(stringSpliterator.hasCharacteristics(Spliterator.ORDERED));
      System.out.println(false);
      System.out.println(stringSpliterator.hasCharacteristics(Spliterator.IMMUTABLE));
      System.out.println(true);

      stringSpliterator =
          ((MyArrayListNoOverride) new MyArrayListSubclassNoOverride()).superSpliterator();
      System.out.println(stringSpliterator.hasCharacteristics(Spliterator.ORDERED));
      System.out.println(true);
      System.out.println(stringSpliterator.hasCharacteristics(Spliterator.IMMUTABLE));
      System.out.println(false);
    }
  }

  static class MyArrayListOverride extends ArrayList<String> {

    @Override
    public Spliterator<String> spliterator() {
      return Spliterators.spliterator(this, Spliterator.IMMUTABLE);
    }

    public Spliterator<String> superSpliterator() {
      return super.spliterator();
    }
  }

  static class MyArrayListOverrideSubclass extends MyArrayListOverride {

    @Override
    public Spliterator<String> superSpliterator() {
      return super.spliterator();
    }

    // Unused, but prove the super invoke won't resolve into it.
    @Override
    public Spliterator<String> spliterator() {
      return Spliterators.spliterator(this, Spliterator.IMMUTABLE | Spliterator.ORDERED);
    }
  }

  static class MyArrayListNoOverride extends ArrayList<String> {

    public Spliterator<String> superSpliterator() {
      return super.spliterator();
    }
  }

  static class MyArrayListSubclassNoOverride extends MyArrayListNoOverride {
    public Spliterator<String> superSpliterator() {
      return super.spliterator();
    }

    // Unused, but prove the super invoke won't resolve into it.
    @Override
    public Spliterator<String> spliterator() {
      return Spliterators.spliterator(this, Spliterator.IMMUTABLE | Spliterator.ORDERED);
    }
  }
}
