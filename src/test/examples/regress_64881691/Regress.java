// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package regress_64881691;

class A {
}

class B {
}

public class Regress {

  private static Object o = null;
  private static final String NAME = (o != null ? A.class : B.class).getSimpleName();

  public static void main(String[] args) throws NoSuchMethodException {
    System.out.println(NAME);
  }
}
