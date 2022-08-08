// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.attributes;

import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.utils.BooleanUtils;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InnerClassesSimpleTest extends TestBase {

  private final TestParameters parameters;
  private final boolean minify;

  @Parameters(name = "{0}, minify: {1}")
  public static List<Object[]> data() {
    return buildParameters(getTestParameters().withCfRuntimes().build(), BooleanUtils.values());
  }

  public InnerClassesSimpleTest(TestParameters parameters, boolean minify) {
    this.parameters = parameters;
    this.minify = minify;
  }

  @Test
  public void testR8() throws Exception {
    Path path =
        testForR8(parameters.getBackend())
            .addInnerClasses(getClass())
            .addKeepMainRule(Main.class)
            .addKeepPackageNamesRule(getClass().getPackage())
            .noTreeShaking()
            .applyIf(!minify, TestShrinkerBuilder::addDontObfuscate)
            .compile()
            .writeToZip();
    testForR8(parameters.getBackend())
        .addProgramFiles(path)
        .addKeepAllClassesRule()
        .allowDiagnosticInfoMessages(minify)
        .compile()
        // TODO(b/182524171): Prune inner class attributes if they are not kept.
        .assertAllInfoMessagesMatch(containsString("Malformed inner-class attribute"))
        .assertInfosCount(minify ? 4 : 0)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello World");
  }

  public interface MyRunner {
    void run();
  }

  public static class Main {

    public static void runIt(MyRunner runner) {
      runner.run();
    }

    public static void main(String[] args) {
      runIt(
          () -> {
            System.out.println("Hello World");
          });
    }
  }
}
