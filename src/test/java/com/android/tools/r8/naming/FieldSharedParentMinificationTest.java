// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class FieldSharedParentMinificationTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .addKeepClassAndMembersRulesWithAllowObfuscation(
            I.class, J.class, A.class, B.class, C.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("true", "42", "Hello World!")
        .inspect(
            inspector -> {
              FieldSubject foo = inspector.clazz(I.class).uniqueFieldWithOriginalName("foo");
              FieldSubject bar = inspector.clazz(J.class).uniqueFieldWithOriginalName("bar");
              FieldSubject baz = inspector.clazz(A.class).uniqueFieldWithOriginalName("baz");
              assertThat(foo, isPresentAndRenamed());
              assertThat(bar, isPresentAndRenamed());
              assertThat(baz, isPresentAndRenamed());
              Set<String> seenNames =
                  ImmutableSet.of(foo.getFinalName(), bar.getFinalName(), baz.getFinalName());
              assertEquals(ImmutableSet.of("a", "b", "c"), seenNames);
            });
  }

  public interface I {

    int foo = 42;
  }

  public interface J {
    String bar = "Hello World!";
  }

  public static class A {

    boolean baz = System.currentTimeMillis() > 0;
  }

  public static class B extends A implements I {}

  public static class C extends A implements J {}

  public static class Main {

    public static void main(String[] args) {
      System.out.println(new A().baz);
      System.out.println(new B().foo);
      System.out.println(new C().bar);
    }
  }
}
