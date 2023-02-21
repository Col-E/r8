// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.naming.testclasses.Greeting;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Regression test for b/128600647. */
@RunWith(Parameterized.class)
public class InterfaceFieldMinificationTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    String expectedOutput = StringUtils.lines("Greeter: Hello world!");
    testForR8(Backend.DEX)
        .addProgramClasses(
            TestClass.class, Greeter.class, Greeting.class, Greeting.getGreetingBase(), Tag.class)
        .addKeepMainRule(TestClass.class)
        .addKeepRules("-keep,allowobfuscation class " + Tag.class.getTypeName() + " { <fields>; }")
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(expectedOutput);
  }

  static class TestClass {

    public static void main(String[] args) {
      new Greeter("Hello world!").greet();
    }
  }

  @NeverClassInline
  static class Greeter extends Greeting implements Tag {

    Greeter(String greeting) {
      this.greeting = greeting;
    }

    @NeverInline
    void greet() {
      System.out.println(TAG + ": " + greeting);
    }
  }

  @NoVerticalClassMerging
  public interface Tag {

    String TAG = "Greeter";
  }
}
