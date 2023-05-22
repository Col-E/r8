// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.StringUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DontUseMixedCaseClassNamesExistingClassTest extends TestBase {

  private final TestParameters parameters;
  private final boolean dontUseMixedCase;
  private static final String EXPECTED = "A.foo";
  private static final String FINAL_CLASS_NAME = "DontUseMixedCaseClassNamesExistingClassTest$main";

  @Parameters(name = "{0}, dontusemixedcaseclassnames: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  public DontUseMixedCaseClassNamesExistingClassTest(
      TestParameters parameters, boolean dontUseMixedCase) {
    this.parameters = parameters;
    this.dontUseMixedCase = dontUseMixedCase;
  }

  @Test
  public void testR8() throws ExecutionException, CompilationFailedException, IOException {
    Path dictionary = temp.getRoot().toPath().resolve("dictionary.txt");
    FileUtils.writeTextFile(dictionary, FINAL_CLASS_NAME);
    testForR8(parameters.getBackend())
        .addProgramClasses(A.class, Main.class)
        .setMinApi(parameters)
        .addKeepClassRulesWithAllowObfuscation(A.class)
        .addKeepMainRule(Main.class)
        .addKeepPackageNamesRule(Main.class.getPackage())
        .addKeepRules("-classobfuscationdictionary " + dictionary.toString())
        .applyIf(dontUseMixedCase, b -> b.addKeepRules("-dontusemixedcaseclassnames"))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED)
        .inspect(
            inspector -> {
              String finalName = Main.class.getPackage().getName() + "." + FINAL_CLASS_NAME;
              assertEquals(
                  StringUtils.toLowerCase(finalName),
                  StringUtils.toLowerCase(Main.class.getTypeName()));
              if (dontUseMixedCase) {
                assertNotEquals(finalName, inspector.clazz(A.class).getFinalName());
              } else {
                assertEquals(finalName, inspector.clazz(A.class).getFinalName());
              }
            });
  }

  public static class A { // Will be renamed to main if not -dontusemixedcaseclassnames

    public void foo() {
      System.out.println("A.foo");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      new A().foo();
    }
  }
}
