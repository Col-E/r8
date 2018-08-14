// Copyright (c) 2018 the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package assumevalues7;

class A {
  public static Object getObjectStatic() {
    return new Object();
  }

  // This method must be on a class which is not explicitly kept (as Assumevalues is).
  // For an explicitly kept class we cannot expect single target for any methods, so the rule
  // will never apply (see b/70550443#comment2).
  public Object getObject() {
    return new Object();
  }
}

public class Assumevalues {
  public static void main(String[] args) {
    if (A.getObjectStatic() != null) {
      System.out.println("NOPE_STATIC_NOT_NULL");
    }
    if (new A().getObject() != null) {
      System.out.println("NOPE_NOT_NULL");
    }
    System.out.println("OK");
  }
}
