// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner.trivial;

import com.android.tools.r8.AssumeMayHaveSideEffects;
import com.android.tools.r8.NeverInline;

public class TrivialTestClass {
  private static int ID = 0;

  private static String next() {
    return Integer.toString(ID++);
  }

  public static void main(String[] args) {
    TrivialTestClass test = new TrivialTestClass();
    test.testInner();
    test.testConstructorMapping1();
    test.testConstructorMapping2();
    test.testConstructorMapping3();
    test.testEmptyClass();
    test.testEmptyClassWithInitializer();
    test.testClassWithFinalizer();
    test.testCallOnIface1();
    test.testCallOnIface2();
    test.testCycles();
  }

  @NeverInline
  private void testInner() {
    Inner inner = new Inner("inner{", 123, next() + "}");
    System.out.println(inner.myToString() + " " + inner.getPrefix() + " = " + inner.prefix);
  }

  @NeverInline
  private void testConstructorMapping1() {
    ReferencedFields o = new ReferencedFields(next());
    System.out.println(o.getA());
  }

  @NeverInline
  private void testConstructorMapping2() {
    ReferencedFields o = new ReferencedFields(next());
    System.out.println(o.getB());
  }

  @NeverInline
  private void testConstructorMapping3() {
    ReferencedFields o = new ReferencedFields(next(), next());
    System.out.println(o.getA() + o.getB() + o.getConcat());
  }

  @AssumeMayHaveSideEffects
  @NeverInline
  private void testEmptyClass() {
    new EmptyClass();
  }

  @NeverInline
  private void testEmptyClassWithInitializer() {
    new EmptyClassWithInitializer();
  }

  @NeverInline
  private void testClassWithFinalizer() {
    new ClassWithFinal();
  }

  private void callOnIface1(Iface1 iface) {
    iface.foo();
  }

  @NeverInline
  private void testCallOnIface1() {
    callOnIface1(new Iface1Impl(next()));
  }

  private void callOnIface2(Iface2 iface) {
    iface.foo();
  }

  @NeverInline
  private void testCallOnIface2() {
    callOnIface2(new Iface2Impl(next()));
    System.out.println(Iface2Impl.CONSTANT); // Keep constant referenced
  }

  @NeverInline
  private void testCycles() {
    new CycleReferenceAB("first").foo(3);
    new CycleReferenceBA("second").foo(4);
    new CycleReferenceAB("third").foo(5);
    new CycleReferenceBA("fourth").foo(6);
  }

  public class Inner {
    private String prefix;
    private String suffix;
    private double id;

    public Inner(String prefix, double id, String suffix) {
      this.prefix = prefix;
      this.suffix = suffix;
      this.id = id;
    }

    public String myToString() {
      return prefix + id + suffix;
    }

    public String getPrefix() {
      return prefix;
    }

    public String getSuffix() {
      return suffix;
    }

    public double getId() {
      return id;
    }

    public void setPrefix(String prefix) {
      this.prefix = prefix;
    }

    public void setSuffix(String suffix) {
      this.suffix = suffix;
    }

    public void setId(double id) {
      this.id = id;
    }
  }
}
