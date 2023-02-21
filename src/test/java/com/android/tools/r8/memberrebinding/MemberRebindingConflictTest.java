// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.memberrebinding;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.memberrebinding.testclasses.MemberRebindingConflictTestClasses;
import com.android.tools.r8.memberrebinding.testclasses.MemberRebindingConflictTestClasses.C;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MemberRebindingConflictTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public MemberRebindingConflictTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(A.class, TestClass.class)
        .addProgramClassFileData(
            transformer(B.class)
                .removeMethods(
                    (access, name, descriptor, signature, exceptions) -> {
                      if (name.equals("foo")) {
                        assert MethodAccessFlags.fromCfAccessFlags(access, false).isSynthetic();
                        return true;
                      }
                      return false;
                    })
                .transform())
        .addInnerClasses(MemberRebindingConflictTestClasses.class)
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("foo", "bar", "foo", "baz");
  }

  static class TestClass {

    public static void main(String[] args) {
      C c = new C();
      c.bar();
      c.baz();
    }
  }

  @NoVerticalClassMerging
  static class A {

    @NeverInline
    public void foo() {
      System.out.println("foo");
    }
  }

  @NoVerticalClassMerging
  public static class B extends A {

    // public synthetic void foo() { super.foo(); }

    @NeverInline
    public void bar() {
      foo();
      System.out.println("bar");
    }
  }
}
