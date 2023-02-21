// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import org.junit.Test;
import org.junit.runners.Parameterized;

public class MergeWithRepackageNameCollissionTest extends HorizontalClassMergingTestBase {

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public MergeWithRepackageNameCollissionTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassFileData(getProgramFileData())
        .addProgramClasses(I.class, Runner.class)
        .addKeepMainRule(Main.class)
        .addKeepClassAndMembersRules(I.class, Runner.class)
        .setMinApi(parameters)
        .enableNoHorizontalClassMergingAnnotations()
        .enableInliningAnnotations()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("C::baz", "Hello World");
  }

  private Collection<byte[]> getProgramFileData() throws Exception {
    return ImmutableList.of(
        transformer(A.class).setClassDescriptor("La/a;").transform(),
        transformer(B.class).setClassDescriptor("Lb/a;").setSuper("La/a;").transform(),
        transformer(D.class).setClassDescriptor("Lc/a;").transform(),
        transformer(Main.class)
            .replaceClassDescriptorInMethodInstructions(descriptor(A.class), "La/a;")
            .replaceClassDescriptorInMethodInstructions(descriptor(B.class), "Lb/a;")
            .replaceClassDescriptorInMethodInstructions(descriptor(D.class), "Lc/a;")
            .transform());
  }

  public static /* will be: a.a */ class A {

    @NeverInline
    public static void foo() {
      System.out.println("A::foo");
    }
  }

  // a.a will be merged into b.a and thereby produce a mapping from a.a -> b.a in our lens.
  public static /* will be: b.a */ class B extends A {}

  // c.a will be repackaged to a.a
  @NoHorizontalClassMerging
  public static /* will be: c.a */ class D {

    @NeverInline
    public static void baz() {
      System.out.println("C::baz");
    }
  }

  public interface I {

    void run();
  }

  public static class Runner {

    @NeverInline
    static void callI(I i) {
      i.run();
    }
  }

  public static class Main {

    public static void main(String[] args) {
      D.baz();
      callFoo(null);
      Runner.callI(
          () -> {
            System.out.println("Hello World");
          });
    }

    public static void callFoo(Object obj) {
      if (obj != null) {
        B.foo();
      }
    }
  }
}
