// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverMerge;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.naming.testclasses.Greeting;
import org.junit.Test;

/** Regression test for b/128600647. */
public class InterfaceFieldMinificationTest extends TestBase {

  @Test
  public void test() throws Exception {
    testForR8(Backend.DEX)
        .addProgramClasses(
            TestClass.class, Greeter.class, Greeting.class, Greeting.getGreetingBase(), Tag.class)
        .addKeepMainRule(TestClass.class)
        .addKeepRules("-keep,allowobfuscation class " + Tag.class.getTypeName() + " { <fields>; }")
        .enableClassInliningAnnotations()
        .enableInliningAnnotations()
        .enableMergeAnnotations()
        .run(TestClass.class)
        .assertFailureWithErrorThatMatches(containsString("IncompatibleClassChangeError"));
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

  @NeverMerge
  public interface Tag {

    String TAG = "Greeter";
  }
}
