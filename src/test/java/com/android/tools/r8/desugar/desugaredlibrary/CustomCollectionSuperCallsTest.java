// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import com.android.tools.r8.D8TestRunResult;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
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
  private final boolean shrinkDesugaredLibrary;

  @Parameters(name = "{1}, shrinkDesugaredLibrary: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withDexRuntimes().withAllApiLevels().build());
  }

  public CustomCollectionSuperCallsTest(boolean shrinkDesugaredLibrary, TestParameters parameters) {
    this.shrinkDesugaredLibrary = shrinkDesugaredLibrary;
    this.parameters = parameters;
  }

  @Test
  public void testCustomCollectionSuperCallsD8() throws Exception {
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    D8TestRunResult d8TestRunResult =
        testForD8()
            .addInnerClasses(CustomCollectionSuperCallsTest.class)
            .setMinApi(parameters.getApiLevel())
            .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
            .compile()
            .addDesugaredCoreLibraryRunClassPath(
                this::buildDesugaredLibrary,
                parameters.getApiLevel(),
                keepRuleConsumer.get(),
                shrinkDesugaredLibrary)
            .run(parameters.getRuntime(), Executor.class)
            .assertSuccess();
    assertLines2By2Correct(d8TestRunResult.getStdOut());
  }

  @Test
  public void testCustomCollectionSuperCallsR8() throws Exception {
    expectThrowsWithHorizontalClassMergingIf(
        parameters.getApiLevel().isLessThan(AndroidApiLevel.N));
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    R8TestRunResult r8TestRunResult =
        testForR8(parameters.getBackend())
            .addInnerClasses(CustomCollectionSuperCallsTest.class)
            .addKeepMainRule(Executor.class)
            .setMinApi(parameters.getApiLevel())
            .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
            .compile()
            .addDesugaredCoreLibraryRunClassPath(
                this::buildDesugaredLibrary,
                parameters.getApiLevel(),
                keepRuleConsumer.get(),
                shrinkDesugaredLibrary)
            .run(parameters.getRuntime(), Executor.class)
            .assertSuccess();
    assertLines2By2Correct(r8TestRunResult.getStdOut());
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
