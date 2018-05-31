// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner.builders;

public class ControlFlow {
  int a;
  int b;
  int c = 1234;
  int d;
  String s = ">";

  ControlFlow(int b, int c, int d) {
    this.s += this.a++ + ">";
    this.s += this.b + ">";
    this.b = b;
    this.s += this.b + ">";
    this.s += this.c + ">";
    this.c += c;
    this.s += this.c + ">";
    this.s += (this.d = d) + ">";
  }

  public void foo(int count) {
    for (int i = 0; i < count; i++) {
      switch (i % 4) {
        case 0:
          this.s += ++this.a + ">";
          break;
        case 1:
          this.c += this.b;
          this.s += this.c + ">";
          break;
        case 2:
          this.d += this.d++ + this.c++ + this.b++ + this.a++;
          this.s += this.d + ">";
          break;
      }
    }
  }

  public void bar(int a, int b, int c, int d) {
    this.a += a;
    this.b += b;
    this.c += c;
    this.d += d;
  }

  @Override
  public String toString() {
    return s;
  }
}
