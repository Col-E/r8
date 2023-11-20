// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.regalloc;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import java.util.Iterator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class B140588497 extends TestBase {

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  private final TestParameters parameters;

  public B140588497(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testD8() throws Exception {
    testForD8()
        .addInnerClasses(B140588497.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("012345")
        .inspect(
            inspector -> {
              ClassSubject c = inspector.clazz(TestClass.class);
              assertThat(c, isPresent());

              MethodSubject m = c.uniqueMethodWithOriginalName("invokeRangeTest");
              assertThat(m, isPresent());
              Iterator<InstructionSubject> it =
                  m.iterateInstructions(InstructionSubject::isConstNumber);
              LongList numbers = new LongArrayList();
              while (it.hasNext()) {
                numbers.add(it.next().getConstNumber());
              }
              assertEquals(new LongArrayList(new long[] {4, 5, 0, 1, 2, 3}), numbers);
            });
  }

  static class TestClass {
    public static void invokeRangeTest() {
      consumeManyLongs(0, 1, 2, 3, 4, 5);
    }

    public static void consumeManyLongs(long a, long b, long c, long d, long e, long f) {
      System.out.println("" + a + b + c + d + e + f);
    }

    public static void main(String[] args) {
      invokeRangeTest();
    }
  }

}
