// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package assumenosideeffects6;

class A {
  @CheckDiscarded
  public static Object methodStaticNull() {
    System.out.println("methodStaticNull");
    return new Object();
  }

  // This method must be on a class which is not explicitly kept (as Assumenosideeffects is).
  // For an explicitly kept class we cannot expect single target for any methods, so the rule
  // will never apply (see b/70550443#comment2).
  @CheckDiscarded
  public Object methodNull() {
    System.out.println("methodNull");
    return new Object();
  }
}

public class Assumenosideeffects {
  public static void main(String[] args) {
    System.out.println(A.methodStaticNull() != null ? "NOT NULL" : "NULL");
    System.out.println((new A().methodNull()) != null ? "NOT NULL" : "NULL");
  }
}
