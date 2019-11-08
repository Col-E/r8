// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.util.LinkedHashSet;
import java.util.Spliterator;
import java.util.Spliterators;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class LinkedHashSetTest extends CoreLibDesugarTestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public LinkedHashSetTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testLinkedHashSetOverrides() throws Exception {
    String stdOut =
        testForD8()
            .addInnerClasses(LinkedHashSetTest.class)
            .setMinApi(parameters.getApiLevel())
            .enableCoreLibraryDesugaring(parameters.getApiLevel())
            .compile()
            .addDesugaredCoreLibraryRunClassPath(
                this::buildDesugaredLibrary, parameters.getApiLevel())
            .run(parameters.getRuntime(), Executor.class)
            .assertSuccess()
            .getStdOut();
    assertLines2By2Correct(stdOut);
  }

  static class Executor {
    @SuppressWarnings("RedundantOperationOnEmptyContainer")
    public static void main(String[] args) {
      Spliterator<String> spliterator;

      // Spliterator of Set is only distinct.
      // Spliterator of LinkedHashSet is distinct and ordered.
      // Spliterator of CustomLinkedHashSetOverride is distinct, ordered and immutable.
      // If an incorrect method is found, characteristics are incorrect.

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
}
