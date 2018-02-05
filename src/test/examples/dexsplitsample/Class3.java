// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package dexsplitsample;

public class Class3 extends Class1 {
  public static void main(String[] args) {
    Class3 clazz = new Class3();
    if (clazz.getClass1String() != "Class1String") {
      throw new RuntimeException("Can't call method from super");
    }
    if (!new InnerClass().success()) {
      throw new RuntimeException("Can't call method on inner class");
    }
    System.out.println("Class3");
  }

  private static class InnerClass {
    public boolean success() {
      return true;
    }
  }
}
