// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package desugaringwithmissingclasstest1;

public class Main {
  public static void main(String[] args) throws Exception {
    ImplementMethodsWithDefault instance = new ImplementMethodsWithDefault();
    try {
      String foo = instance.foo();
      if (foo.equals("B")) {
        System.out.println("OK");
      } else {
        System.out.println("NOT OK: " + foo);
      }
    } catch (Throwable t) {
      System.out.println("NOT OK " + t.getClass() + " " + t.getMessage());
      t.printStackTrace();
    }
  }
}
