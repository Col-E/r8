// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

public class LambdaRenamingTest {

  public static final Class[] CLASSES = {
    LambdaRenamingTest.class,
    Interface.class,
    DummyInterface1.class,
    DummyInterface2.class,
    DummyInterface3.class,
    DummyInterface4.class,
    ObjectInterface.class,
    IntegerInterface.class,
    ReservedNameObjectInterface1.class,
    ReservedNameIntegerInterface1.class,
    ReservedNameObjectInterface2.class,
    ReservedNameIntegerInterface2.class,
    InexactImplementation.class,
    DummyImplementation.class,
    ReservedImplementation1.class,
    ReservedImplementation2.class,
  };

  interface Interface {
    String method();
  }

  // Interface methods are renamed in decreasing order of number of interfaces they appear in.
  // Define many interfaces with "Object foo();" so that it will receive the first name "a".
  // Define "Object inexactMethod();" in one of them so that it will be named "b".
  // Then define "Integer inexactMethod();" in an unrelated interface so it will be named "a"
  // (when renaming aggressively).
  interface DummyInterface1 {
    Object foo();

    Object inexactMethod();
  }

  interface DummyInterface2 {
    Object foo();
  }

  interface DummyInterface3 {
    Object foo();
  }

  interface DummyInterface4 {
    Object foo();
  }

  interface ObjectInterface {
    Object inexactMethod();
  }

  interface IntegerInterface {
    Integer inexactMethod();
  }

  interface ReservedNameObjectInterface1 {
    // The following method is explicitly kept in the test's ProGuard config.
    Object reservedMethod1();
  }

  interface ReservedNameIntegerInterface1 {
    Integer reservedMethod1();
  }

  interface ReservedNameObjectInterface2 {
    Object reservedMethod2();
  }

  interface ReservedNameIntegerInterface2 {
    // The following method is explicitly kept in the test's ProGuard config.
    Integer reservedMethod2();
  }

  static class InexactImplementation implements ObjectInterface, DummyInterface1, IntegerInterface {
    @Override
    public Object foo() {
      return null;
    }

    @Override
    public Integer inexactMethod() {
      return 10;
    }
  }

  static class DummyImplementation implements DummyInterface1 {
    @Override
    public Object foo() {
      return null;
    }

    @Override
    public Integer inexactMethod() {
      return 11;
    }
  }

  static class ReservedImplementation1
      implements ReservedNameIntegerInterface1, ReservedNameObjectInterface1 {
    @Override
    public Integer reservedMethod1() {
      return 101;
    }
  }

  static class ReservedImplementation2
      implements ReservedNameIntegerInterface2, ReservedNameObjectInterface2 {
    @Override
    public Integer reservedMethod2() {
      return 102;
    }
  }

  public static void main(String[] args) {
    dummyMethod(new InexactImplementation(), () -> null, () -> null, () -> null);
    dummyMethod(new DummyImplementation(), () -> null, () -> null, () -> null);
    invokeInteger(new InexactImplementation());
    invokeInteger((IntegerInterface) getInexactLambda());
    invokeObject(new InexactImplementation());
    invokeObject((ObjectInterface) getInexactLambda());
    invokeIntegerReserved1(new ReservedImplementation1());
    invokeIntegerReserved1((ReservedNameIntegerInterface1) getReservedLambda1());
    invokeObjectReserved1(new ReservedImplementation1());
    invokeObjectReserved1((ReservedNameObjectInterface1) getReservedLambda1());
    invokeIntegerReserved2(new ReservedImplementation2());
    invokeIntegerReserved2((ReservedNameIntegerInterface2) getReservedLambda2());
    invokeObjectReserved2(new ReservedImplementation2());
    invokeObjectReserved2((ReservedNameObjectInterface2) getReservedLambda2());
  }

  private static Object getInexactLambda() {
    return (IntegerInterface & ObjectInterface) () -> 30;
  }

  private static Object getReservedLambda1() {
    return (ReservedNameIntegerInterface1 & ReservedNameObjectInterface1) () -> 301;
  }

  private static Object getReservedLambda2() {
    return (ReservedNameIntegerInterface2 & ReservedNameObjectInterface2) () -> 302;
  }

  private static void dummyMethod(
      DummyInterface1 instance1,
      DummyInterface2 instance2,
      DummyInterface3 instance3,
      DummyInterface4 instance4) {
    System.out.println(instance1.foo());
    System.out.println(instance2.foo());
    System.out.println(instance3.foo());
    System.out.println(instance4.foo());
    System.out.println(instance1.inexactMethod());
  }

  private static void invokeInteger(IntegerInterface instance) {
    System.out.println(instance.inexactMethod());
  }

  private static void invokeObject(ObjectInterface instance) {
    System.out.println(instance.inexactMethod());
  }

  private static void invokeIntegerReserved1(ReservedNameIntegerInterface1 instance) {
    System.out.println(instance.reservedMethod1());
  }

  private static void invokeObjectReserved1(ReservedNameObjectInterface1 instance) {
    System.out.println(instance.reservedMethod1());
  }

  private static void invokeIntegerReserved2(ReservedNameIntegerInterface2 instance) {
    System.out.println(instance.reservedMethod2());
  }

  private static void invokeObjectReserved2(ReservedNameObjectInterface2 instance) {
    System.out.println(instance.reservedMethod2());
  }
}
