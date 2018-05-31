// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner.builders;

public class Tuple {
  public boolean z;
  public byte b;
  public short s;
  public char c;
  public int i;
  public long l;
  public float f;
  public double d;
  public Object o;

  Tuple() {
  }

  Tuple(boolean z, byte b, short s, char c, int i, long l, float f, double d, Object o) {
    this.z = z;
    this.b = b;
    this.s = s;
    this.c = c;
    this.i = i;
    this.l = l;
    this.f = f;
    this.d = d;
    this.o = o;
  }

  @Override
  public String toString() {
    return "Tuple1(" + z + ", " + b + ", " + s + ", " +
        ((int) c) + ", " + i + ", " + l + ", " + f + ", " +
        d + ", " + (o == null ? "<null>" : o) + ")";
  }
}
