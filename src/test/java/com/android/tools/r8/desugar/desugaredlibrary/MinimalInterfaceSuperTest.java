// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@SuppressWarnings("ALL")
@RunWith(Parameterized.class)
public class MinimalInterfaceSuperTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  public MinimalInterfaceSuperTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private static final String EXPECTED_OUTPUT = StringUtils.lines("removeIf from Col1Itf");

  @Test
  public void testCustomCollectionR8() throws Exception {
    if (parameters.isCfRuntime()) {
      testForJvm()
          .addInnerClasses(MinimalInterfaceSuperTest.class)
          .run(parameters.getRuntime(), Main.class)
          .assertSuccessWithOutput(EXPECTED_OUTPUT);
      return;
    }
    testForR8(parameters.getBackend())
        .addInnerClasses(MinimalInterfaceSuperTest.class)
        .addKeepMainRule(Main.class)
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring(parameters.getApiLevel())
        .compile()
        .assertNoMessages()
        .addDesugaredCoreLibraryRunClassPath(this::buildDesugaredLibrary, parameters.getApiLevel())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  static class Main {
    public static void main(String[] args) {
      Col1<Integer> ints1 = new Col1<>();
      ints1.removeIf(x -> x == 3);
    }
  }

  interface Col1Itf<E> extends Collection<E> {
    @Override
    default boolean removeIf(Predicate<? super E> filter) {
      System.out.println("removeIf from Col1Itf");
      return Collection.super.removeIf(filter);
    }
  }

  static class Col1<E> extends AbstractCollection<E> implements Col1Itf<E> {
    @NotNull
    @Override
    public Iterator<E> iterator() {
      return Collections.emptyIterator();
    }

    @Override
    public int size() {
      return 0;
    }
  }
}
