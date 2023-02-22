// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.FileUtils;
import com.google.common.collect.Lists;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ObfuscationPrimitiveNamesTest extends TestBase {

  private static List<String> FORBIDDEN_NAMES =
      Lists.newArrayList("int", "long", "boolean", "float");

  public static class Main {

    public static void main(String[] args) {
      System.out.print("Hello World!");
    }
  }

  public static class A {}

  public static class B {}

  public static class C {}

  public static class D {}

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testNotHavingPrimitiveNames() throws Exception {
    Path dictionary = temp.getRoot().toPath().resolve("dictionary.txt");
    FileUtils.writeTextFile(dictionary, FORBIDDEN_NAMES);
    testForR8(parameters.getBackend())
        .addInnerClasses(ObfuscationPrimitiveNamesTest.class)
        .addKeepRules("-classobfuscationdictionary " + dictionary.toString())
        .addKeepAllClassesRuleWithAllowObfuscation()
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput("Hello World!")
        .inspect(
            inspector -> {
              assertEquals(5, inspector.allClasses().size());
              assertTrue(
                  inspector.allClasses().stream()
                      .noneMatch(c -> FORBIDDEN_NAMES.contains(c.getFinalName())));
            });
  }
}
