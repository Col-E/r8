// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.FileUtils;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ObfuscationDictionaryDuplicateTest extends TestBase {

  public static class Main {
    public static void a() {
      System.out.println("a");
    }

    public static void b() {
      System.out.println("b");
    }

    public static void c() {
      System.out.println("c");
    }

    public static void main(String[] args) {
      a();
      b();
      c();
    }
  }

  public static class FirstClass {}

  public static class SecondClass {}

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test(expected = CompilationFailedException.class)
  public void testHavingDuplicateMethodNames() throws IOException, CompilationFailedException {
    Path dictionary = temp.getRoot().toPath().resolve("dictionary.txt");
    FileUtils.writeTextFile(dictionary, "x", "y", "x");
    testForR8(parameters.getBackend())
        .addInnerClasses(ObfuscationDictionaryDuplicateTest.class)
        .addKeepRules("-obfuscationdictionary " + dictionary.toString())
        .addKeepAllClassesRuleWithAllowObfuscation()
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .compile();
  }

  @Test(expected = CompilationFailedException.class)
  public void testHavingDuplicateClassNames() throws IOException, CompilationFailedException {
    Path dictionary = temp.getRoot().toPath().resolve("dictionary.txt");
    FileUtils.writeTextFile(dictionary, "x", "y", "x");
    testForR8(parameters.getBackend())
        .addInnerClasses(ObfuscationDictionaryDuplicateTest.class)
        .addKeepRules("-classobfuscationdictionary " + dictionary.toString())
        .addKeepAllClassesRuleWithAllowObfuscation()
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .compile();
  }
}
