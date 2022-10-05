// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.proguard.rules;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;

/** Regression test for b/124584385. */
public class ProguardMatchAllRuleWithPrefixTest extends TestBase {

  @Test
  public void test() throws Exception {
    CodeInspector inspector =
        testForR8(Backend.DEX)
            .addProgramClasses(TestClass.class)
            .addKeepRules(
                "-keep,allowobfuscation class com.android.tools.r8.*** {",
                "  com.android.tools.r8.*** methodA();",
                "  com.android.tools.r8.***Class methodB();",
                "  com.android.tools.r8.***[] methodC();",
                "  com.android.tools.r8.***Class[] methodD();",
                "}")
            .compile()
            .inspector();

    ClassSubject classSubject = inspector.clazz(TestClass.class);
    assertThat(classSubject, isPresent());
    assertThat(classSubject.uniqueMethodWithOriginalName("methodA"), isPresent());
    assertThat(classSubject.uniqueMethodWithOriginalName("methodB"), isPresent());
    assertThat(classSubject.uniqueMethodWithOriginalName("methodC"), isPresent());
    assertThat(classSubject.uniqueMethodWithOriginalName("methodD"), isPresent());
  }

  static class TestClass {

    TestClass methodA() {
      return new TestClass();
    }

    TestClass methodB() {
      return new TestClass();
    }

    TestClass[] methodC() {
      return new TestClass[0];
    }

    TestClass[] methodD() {
      return new TestClass[0];
    }
  }
}
