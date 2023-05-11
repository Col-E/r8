// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.examples.enclosingmethod;

public class OuterClass {
  // Named member class.
  public class AClass {

  }

  static {
    // Named local class. Will have an enclosing-method annotation with a zero method by being
    // defined in the static initializer.
    class LocalClass extends AbstractClass {

      @Override
      public int anInt() {
        return 7;
      }
    }

    // Anonymous inner class. Will have the same zero-method enclosing-method annotation.
    print(new AbstractClass() {
      @Override
      public int anInt() {
        return 42;
      }
    });
    print(new LocalClass());
  }

  public void aMethod() {
    // Local class with a non-zero-method enclosing-method annotation.
    class AnotherClass extends AbstractClass {

      @Override
      public int anInt() {
        return 48;
      }
    }

    // Anonymous inner class with a non-zero-method enclosing-method annotation.
    print(new AbstractClass() {
      @Override
      public int anInt() {
        return 42;
      }
    });
    print(new AnotherClass());
  }

  private static void print(AbstractClass anInstance) {
    System.out.println(anInstance.anInt());
    System.out.println(anInstance.getClass().getEnclosingClass());
    System.out.println(anInstance.getClass().getEnclosingMethod());
    System.out.println(anInstance.getClass().isAnonymousClass());
    // DEX enclosing-class annotations don't distinguish member classes from local classes.
    // This results in Class.isLocalClass always being false and Class.isMemberClass always
    // being true even when the converse is the case when running on the JVM.
    // More context b/69453990
    System.out.println(
        anInstance.getClass().isLocalClass() || anInstance.getClass().isMemberClass());
  }
}
