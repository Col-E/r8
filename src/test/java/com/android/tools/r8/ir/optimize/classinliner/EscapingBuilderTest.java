// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner;

import static com.android.tools.r8.ir.optimize.classinliner.EscapingBuilderTest.TestClass.escape;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;

/** Regression test for b/120182628. */
public class EscapingBuilderTest extends TestBase {

  @Test
  public void test() throws Exception {
    testForR8(Backend.DEX)
        .addInnerClasses(EscapingBuilderTest.class)
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .compile()
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) {
    assertThat(inspector.clazz(Builder0.class), not(isPresent()));
    assertThat(inspector.clazz(Builder1.class), isPresent());
    assertThat(inspector.clazz(Builder2.class), isPresent());
    assertThat(inspector.clazz(Builder3.class), isPresent());
    assertThat(inspector.clazz(Builder4.class), isPresent());
    assertThat(inspector.clazz(Builder5.class), isPresent());
  }

  static class TestClass {

    public static void main(String[] args) {
      testBuilder0();
      testBuilder1();
      testBuilder2();
      testBuilder3();
      testBuilder4();
      testBuilder5();
    }

    private static void testBuilder0() {
      new Builder0().build();
    }

    private static void testBuilder1() {
      new Builder1().build();
    }

    private static void testBuilder2() {
      new Builder2().init().build();
    }

    private static void testBuilder3() {
      Builder3 builder3 = new Builder3();
      builder3.init(builder3).build();
    }

    private static void testBuilder4() {
      Builder4 builder4 = new Builder4();
      builder4.init(builder4).build();
    }

    private static void testBuilder5() {
      Builder5.init(new Builder5()).build();
    }

    @NeverInline
    public static void escape(Object o) {}
  }

  // Simple builder that should be class inlined.
  static class Builder0 {

    public Object build() {
      return new Object();
    }
  }

  // Builder that escapes via field `f` that is assigned in the constructor.
  static class Builder1 {

    public Builder1 f = this;

    public Object build() {
      escape(f);
      return new Object();
    }
  }

  // Builder that escapes via field `f` that is assigned in a virtual method.
  static class Builder2 {

    public Builder2 f;

    @NeverInline
    public Builder2 init() {
      f = this;
      return this;
    }

    public Object build() {
      escape(f);
      return new Object();
    }
  }

  // Builder that escapes via field `f` that is assigned in a virtual method.
  static class Builder3 {

    public Builder3 f;

    @NeverInline
    public Builder3 init(Builder3 builder) {
      f = builder;
      return this;
    }

    public Object build() {
      escape(f);
      return new Object();
    }
  }

  // Builder that escapes via field `f` that is assigned in a virtual method.
  static class Builder4 {

    public Builder4 f;

    @NeverInline
    public Builder4 init(Builder4 builder) {
      builder.f = builder;
      return this;
    }

    public Object build() {
      escape(f);
      return new Object();
    }
  }

  // Builder that escapes via field `f` that is assigned in a static method.
  static class Builder5 {

    public Builder5 f;

    @NeverInline
    public static Builder5 init(Builder5 builder) {
      builder.f = builder;
      return builder;
    }

    public Object build() {
      escape(f);
      return new Object();
    }
  }
}
