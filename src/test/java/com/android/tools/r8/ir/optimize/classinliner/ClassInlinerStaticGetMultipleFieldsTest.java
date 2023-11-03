// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.classinliner;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ClassInlinerStaticGetMultipleFieldsTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              assertThat(inspector.clazz(Data.class), isAbsent());
              assertThat(inspector.clazz(DataImpl.class), isAbsent());
            })
        .run(parameters.getRuntime(), Main.class)
        // TODO(b/309076043): Should succeed with "1", "2", "3", "4".
        .assertSuccessWithOutputLines("1", "1", "3", "3");
  }

  static class Main {

    public static void main(String[] args) {
      DataImpl.FOO.print();
      DataImpl.BAR.print();
    }
  }

  @NoVerticalClassMerging
  abstract static class Data {

    abstract void print();
  }

  static class DataImpl extends Data {

    static final Data FOO = new DataImpl(1, 2);
    static final Data BAR = new DataImpl(3, 4);

    int one;
    int two;

    DataImpl(int one, int two) {
      this.one = one;
      this.two = two;
    }

    @NeverInline
    void print() {
      System.out.println(one);
      System.out.println(two);
    }
  }
}
