// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.TestParameters;
import org.junit.Test;

public class CompanionClassMergingTest extends HorizontalClassMergingTestBase {
  public CompanionClassMergingTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addOptionsModification(options -> options.enableClassInlining = false)
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .addHorizontallyMergedClassesInspector(
            inspector -> inspector.assertMergedInto(B.Companion.class, A.Companion.class))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("foo a 0", "foo b 1")
        .inspect(
            codeInspector -> {
              assertThat(codeInspector.clazz(A.class), isPresent());
              assertThat(codeInspector.clazz(B.class), isPresent());

              assertThat(codeInspector.clazz(A.Companion.class), isPresent());
              assertThat(codeInspector.clazz(B.Companion.class), isAbsent());
            });
  }

  @NeverClassInline
  public static class A {
    public static final Companion companion = new Companion(null);
    public static String foo;

    static {
      foo = "foo a " + Main.next();
    }

    public static String access$getFoo$cp() {
      return foo;
    }

    public static class Companion {
      public Companion() {}

      public Companion(Object obj) {
        this();
      }

      public String getFoo() {
        return access$getFoo$cp();
      }
    }
  }

  @NeverClassInline
  public static class B {
    public static final Companion companion = new Companion(null);
    public static String foo;

    static {
      foo = "foo b " + Main.next();
    }

    public static String access$getFoo$cp() {
      return foo;
    }

    public static class Companion {
      public Companion() {}

      public Companion(Object obj) {
        this();
      }

      public String getFoo() {
        return access$getFoo$cp();
      }
    }
  }

  public static class Main {
    static int COUNT = 0;

    public static int next() {
      return COUNT++;
    }

    public static void main(String[] args) {
      System.out.println(A.companion.getFoo());
      System.out.println(B.companion.getFoo());
    }
  }
}
