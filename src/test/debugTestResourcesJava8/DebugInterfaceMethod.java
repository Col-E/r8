// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

public class DebugInterfaceMethod {

  static class DefaultImpl implements InterfaceWithDefaultAndStaticMethods {
  }

  static class OverrideImpl implements InterfaceWithDefaultAndStaticMethods {

    @Override
    public void doSomething(String msg) {
      String newMsg = "OVERRIDE" + msg;
      System.out.println(newMsg);
    }
  }

  private static void testDefaultMethod(InterfaceWithDefaultAndStaticMethods i) {
    i.doSomething("Test");
  }

  private static void testStaticMethod() {
    InterfaceWithDefaultAndStaticMethods.printString("I'm a static method in interface");
  }

  public static void main(String[] args) {
    testDefaultMethod(new DefaultImpl());
    testDefaultMethod(new OverrideImpl());
    testStaticMethod();
  }

}
