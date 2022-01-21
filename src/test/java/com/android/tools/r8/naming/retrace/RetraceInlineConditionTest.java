// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.retrace;

import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.hamcrest.CoreMatchers;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RetraceInlineConditionTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testR8() throws Throwable {
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addInnerClasses(getClass())
            .setMinApi(parameters.getApiLevel())
            .addKeepMainRule(Main.class)
            .compile()
            .inspectProguardMap(
                map -> {
                  // TODO(b/215339687): We should not have a rewriteFrame in the mapping file since
                  //  an explicit null check should be inserted.
                  assertThat(map, CoreMatchers.containsString("com.android.tools.r8.rewriteFrame"));
                });
  }

  static class Foo {

    void inlinable(boolean loop) {
      while (loop) {}
      String string = toString();
      System.out.println(string);
    }
  }

  static class Main {
    public static void main(String[] args) {
      Foo foo = (args.length == 0 ? null : new Foo());
      foo.inlinable(args.length == 0);
    }
  }
}
