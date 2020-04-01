// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress.b151964517;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ConstStringWithMonitorTest extends TestBase {
  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  public ConstStringWithMonitorTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void regress() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(ConstStringWithMonitorTest.class)
        .noMinification()
        .setMinApi(parameters.getApiLevel())
        .allowAccessModification()
        .addKeepMainRule(TestClass.class)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("foobar");
  }

  public static class TestClass {
    public static void main(String[] args) {
      try {
        System.out.println(FooBar.tryIt());
      } catch (IllegalStateException e) {
        e.printStackTrace();
      }
    }
  }

  public static class Value {
    public final String value;

    public Value(String value) {
      this.value = value;
    }
  }

  public static class FooBar {
    private static Object mLock = new Object();

    public static String tryIt() {
      Value value = synchronizedMethod("foobar");
      return value.value;
    }

    private static Value synchronizedMethod(String s) {
      synchronized (mLock) {
        if (System.currentTimeMillis() < 2) {
          s.length();
          throw new IllegalStateException("wrong" + s);
        }
        return new Value(s);
      }
    }
  }
}
