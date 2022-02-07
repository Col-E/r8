// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package jdk8272564;

public class Main {
  // From javac in JDK-18 all of the following three invokes of toString are compiled to
  // invokeinterface. Prior to JDK 18 the last two where compiled to invokevirtual.
  // See https://bugs.openjdk.java.net/browse/JDK-8272564.
  static void f(I i, J j, K k) {
    i.toString();
    j.toString();
    k.toString();
  }

  public static void main(String[] args) {
    f(new A(), new B(), new C());
  }
}
