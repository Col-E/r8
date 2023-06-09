// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoMethodStaticizing;
import com.android.tools.r8.TestParameters;
import org.junit.Test;

public class InheritInterfaceWithDefaultTest extends HorizontalClassMergingTestBase {

  public InheritInterfaceWithDefaultTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .allowStdoutMessages()
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoMethodStaticizingAnnotations()
        .setMinApi(parameters)
        .addHorizontallyMergedClassesInspector(
            inspector -> inspector.assertMergedInto(B.class, A.class))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(
            "print interface", "print interface", "print interface", "print interface")
        .inspect(
            codeInspector -> {
              assertThat(codeInspector.clazz(A.class), isPresent());
              assertThat(codeInspector.clazz(B.class), isAbsent());
            });
  }

  public interface Interface {
    @NeverInline
    @NoMethodStaticizing
    default void print() {
      System.out.println("print interface");
    }
  }

  @NeverClassInline
  public static class A implements Interface {}

  @NeverClassInline
  public static class B implements Interface {}

  public static class Main {

    @NeverInline
    public static void print(Interface i) {
      i.print();
    }

    public static void main(String[] args) {
      new A().print();
      new B().print();
      print(new A());
      print(new B());
    }
  }
}
