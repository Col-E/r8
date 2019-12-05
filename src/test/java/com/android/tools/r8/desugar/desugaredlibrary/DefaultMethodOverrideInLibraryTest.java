// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.desugaredlibrary;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import com.android.tools.r8.D8TestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Spliterator;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * This test checks that if a default interface method in a library is overridden by a class method
 * also in the library, then that class method remains the target of resolution and dispatch.
 *
 * <p>Concretely, List (and Collection) define a default spliterator() method which is overridden in
 * the ArrayList class. Thus, any class deriving ArrayList for which spliterator is not overridden
 * should end up targeting that of ArrayList and not other potential non-library default interface
 * methods.
 */
@RunWith(Parameterized.class)
public class DefaultMethodOverrideInLibraryTest extends DesugaredLibraryTestBase {

  static final String EXPECTED = StringUtils.lines("0", "42");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  public DefaultMethodOverrideInLibraryTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    TestRuntime systemRuntime = TestRuntime.getSystemRuntime();
    if (systemRuntime.isCf() && systemRuntime.asCf().isNewerThanOrEqual(CfVm.JDK8)) {
      // This test assumes that the library defines an ArrayList class with a declared spliterator.
      // For that reason, resolution will find that definition prior to a default interface method.
      Method spliterator = ArrayList.class.getDeclaredMethod("spliterator");
      assertNotNull(spliterator);
      assertFalse(spliterator.isDefault());
    }
    if (parameters.isCfRuntime()) {
      testForJvm()
          .addInnerClasses(DefaultMethodOverrideInLibraryTest.class)
          .run(parameters.getRuntime(), Main.class)
          .assertSuccessWithOutput(EXPECTED);
    } else {
      testForD8()
          .setMinApi(parameters.getApiLevel())
          .addInnerClasses(DefaultMethodOverrideInLibraryTest.class)
          .enableCoreLibraryDesugaring(parameters.getApiLevel())
          .compile()
          .addDesugaredCoreLibraryRunClassPath(
              this::buildDesugaredLibrary, parameters.getApiLevel())
          .run(parameters.getRuntime(), Main.class)
          .apply(this::checkResult);
    }
  }

  private void checkResult(D8TestRunResult result) {
    // TODO(b/145504401): Execution on Art 7.0.0 has the wrong runtime behavior (non-desugared).
    if (parameters.isDexRuntime()
        && parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.N)
        && parameters.getRuntime().asDex().getVm().getVersion().equals(Version.V7_0_0)) {
      result.assertSuccessWithOutputLines("42", "42");
      return;
    }
    result.assertSuccessWithOutput(EXPECTED);
  }

  // Custom spliterator, just returns 42 in estimateSize, otherwise unused.
  static class MySpliterator implements Spliterator<Integer> {

    @Override
    public boolean tryAdvance(Consumer<? super Integer> action) {
      return false;
    }

    @Override
    public Spliterator<Integer> trySplit() {
      return null;
    }

    @Override
    public long estimateSize() {
      return 42; // Overridden to differ from the default.
    }

    @Override
    public int characteristics() {
      return 0;
    }
  }

  // Custom list interface with a default method for spliterator.
  interface MyIntegerList extends List<Integer> {

    @Override
    default Spliterator<Integer> spliterator() {
      return new MySpliterator();
    }
  }

  // Derived list with no override of spliterator. The call will thus go to the super class, not
  // the default method!
  static class MyIntegerArrayListWithoutOverride extends ArrayList<Integer>
      implements MyIntegerList {
    // No override of spliterator.
  }

  // Derived list with an override of spliterator. The call must hit the classes override and that
  // will explictly call the custom default method.
  static class MyIntegerArrayListWithOverride extends ArrayList<Integer> implements MyIntegerList {

    @Override
    public Spliterator<Integer> spliterator() {
      return MyIntegerList.super.spliterator();
    }
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println(new MyIntegerArrayListWithoutOverride().spliterator().estimateSize());
      System.out.println(new MyIntegerArrayListWithOverride().spliterator().estimateSize());
    }
  }
}
