// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package applymapping044;

import naming044.A;
import naming044.B;
import naming044.sub.SubB;

public class Main {
  public static void main(String[] args) {
    B.m();
    SubB.n();
    A a = new A();
    B b = new B();
    b.f(a);
    AsubB aSubB = new AsubB(3);
    aSubB.f(a);
    SubB subB = new SubB(3);
    subB.f(a);
  }
}
