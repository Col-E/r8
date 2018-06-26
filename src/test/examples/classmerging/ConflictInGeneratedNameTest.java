// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package classmerging;

public class ConflictInGeneratedNameTest {
  public static void main(String[] args) {
    B obj = new B();
    obj.run();
  }

  public static class A {
    private String name = "A";

    public A() {
      print("In A.<init>()");
      constructor$classmerging$ConflictInGeneratedNameTest$A();
    }

    private void constructor$classmerging$ConflictInGeneratedNameTest$A() {
      print("In A.constructor$classmerging$ConflictInGeneratedNameTest$A()");
    }

    public void printState() {
      print("In A.printState()");
      print("A=" + name + "=" + getName());
    }

    // This method is not overridden.
    public void foo() {
      print("In A.foo()");
    }

    // This method is overridden.
    public void bar() {
      print("In A.bar()");
    }

    // This method is overridden and there is a PUBLIC method in B with the same name
    // as the direct version of this method in B.
    public void baz() {
      print("In A.baz()");
    }

    // This method is overridden and there is a PRIVATE method in B with the same name
    // as the direct version of this method in B.
    public void boo() {
      print("In A.boo()");
    }

    // There is a private method in B with the same name as this one.
    private String getName() {
      return name;
    }
  }

  public static class B extends A {
    private String name = "B";
    private String name$classmerging$ConflictInGeneratedNameTest$A = "C";

    public B() {
      print("In B.<init>()");
      print("A=" + super.name + "=" + super.getName());
      print("B=" + name + "=" + getName());
      print("C=" + name$classmerging$ConflictInGeneratedNameTest$A);
    }

    public void run() {
      printState();
      foo();
      bar();
      baz();
      baz$classmerging$ConflictInGeneratedNameTest$A();
      boo();
      boo$classmerging$ConflictInGeneratedNameTest$A();
    }

    @Override
    public void bar() {
      print("In B.bar()");
      super.bar();
    }

    @Override
    public void baz() {
      print("In B.baz()");
      super.baz();
    }

    public void baz$classmerging$ConflictInGeneratedNameTest$A() {
      print("In B.baz$classmerging$ConflictInGeneratedNameTest$A()");
    }

    @Override
    public void boo() {
      print("In B.boo()");
      super.boo();
    }

    private void boo$classmerging$ConflictInGeneratedNameTest$A() {
      print("In B.boo$classmerging$ConflictInGeneratedNameTest$A()");
    }

    private String getName() {
      return name;
    }
  }

  public static void print(String message) {
    System.out.println(message);
  }
}
