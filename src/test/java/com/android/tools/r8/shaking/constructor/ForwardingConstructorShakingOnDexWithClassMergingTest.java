// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.constructor;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.Iterables;
import java.util.Objects;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ForwardingConstructorShakingOnDexWithClassMergingTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addOptionsModification(
            options ->
                options.testing.horizontalClassMergingTarget =
                    (appView, candidates, target) ->
                        Iterables.find(
                            candidates,
                            candidate -> candidate.getTypeName().equals(B.class.getTypeName())))
        .addHorizontallyMergedClassesInspector(
            inspector -> inspector.assertMergedInto(A.class, B.class).assertNoOtherClassesMerged())
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject aSubClassSubject = inspector.clazz(ASub.class);
    assertThat(aSubClassSubject, isPresent());
    assertEquals(
        parameters.canHaveNonReboundConstructorInvoke() ? 0 : 1,
        aSubClassSubject.allMethods().size());
  }

  public static class Main {

    public static void main(String[] args) {
      System.out.print(new A(obfuscate("Hello")).getMessageFromA());
      System.out.print(new ASub(obfuscate(", ")).getMessageFromA());
      System.out.println(new B(obfuscate("world!")).getMessageFromB());
    }

    @NeverInline
    private static String obfuscate(String str) {
      return System.currentTimeMillis() > 0 ? str : "dead";
    }

    @NeverInline
    public static void requireNonNull(Object o) {
      Objects.requireNonNull(o);
    }
  }

  @NoVerticalClassMerging
  public static class A {

    private final String msg;

    // Will be merged with B.<init>(String). Since B is chosen as the merge target, a new
    // B.<init>(String, int) method will be generated, and mappings will be created from
    // A.<init>(String) -> B.init$A(String) and B.<init>(String) -> B.init$B(String).
    public A(String msg) {
      // So that A.<init>(String) and B.<init>(String) will not be considered identical during class
      // merging. The indirection is to ensure that the API level of class A is 1.
      Main.requireNonNull(msg);
      this.msg = msg;
    }

    // Will be moved to B.<init>(String, String) by horizontal class merging, and then be optimized
    // to B.<init>(String) by unused argument removal.
    public A(String unused, String msg) {
      this.msg = msg;
    }

    @NeverInline
    public String getMessageFromA() {
      return msg;
    }
  }

  @NeverClassInline
  public static class ASub extends A {

    // After horizontal class merging and unused argument removal, this is rewritten to target
    // B.<init>(String). This constructor can therefore be eliminated by the redundant bridge
    // removal phase.
    public ASub(String msg) {
      super("<unused>", msg);
    }
  }

  public static class B {

    private final String msg;

    public B(String msg) {
      this.msg = msg;
    }

    @NeverInline
    public String getMessageFromB() {
      return msg;
    }
  }
}
