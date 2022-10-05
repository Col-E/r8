// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.AnyOf.anyOf;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.StringContains.containsString;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvalidObfuscationEntryTest extends TestBase {

  public static class Main {

    public void a() {
      System.out.println("Hello from a");
    }

    public void b() {
      System.out.println("Hello from b");
    }

    public static void main(String[] args) {
      new Main().a();
      new Main().b();
    }
  }

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public InvalidObfuscationEntryTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testJumpingOverInvalidNames()
      throws IOException, CompilationFailedException, ExecutionException {
    Path dictionary = temp.getRoot().toPath().resolve("dictionary.txt");
    FileUtils.writeTextFile(dictionary, "x", "!x", "0x", "y");
    testForR8(parameters.getBackend())
        .addInnerClasses(InvalidObfuscationEntryTest.class)
        .addKeepRules("-obfuscationdictionary " + dictionary.toString())
        .addKeepAllClassesRuleWithAllowObfuscation()
        .addKeepMainRule(Main.class)
        .allowDiagnosticInfoMessages()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .assertInfoMessageThatMatches(containsString("Invalid character"))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello from a", "Hello from b")
        .inspect(
            codeInspector -> {
              ClassSubject clazz = codeInspector.clazz(Main.class);
              assertThat(clazz, isPresent());
              MethodSubject aSubject = clazz.uniqueMethodWithOriginalName("a");
              assertThat(aSubject.getFinalName(), anyOf(is("x"), is("y")));
              MethodSubject bSubject = clazz.uniqueMethodWithOriginalName("b");
              assertThat(bSubject.getFinalName(), anyOf(is("x"), is("y")));
            });
  }
}
