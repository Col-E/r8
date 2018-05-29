// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package catchhandleroverlap;

public class CatchHandlerOverlap {
  private static void f() throws Exception {
    throw new Exception("f");
  }

  private static void g() throws Exception {
    throw new Exception("g");
  }

  private static void h(int i1, int i2, int i3, int i4, int i5, int i6, int i7, int i8, int i9,
      int i10, int i11, int i12, int i13, int i14, int i15, int i16, int i17) {
    System.out.println(i1 + i2 + i3 + i4 + i5 + i6 + i7 + i8 + i9 + i10 + i11 +
        i12 + i13 + i14 + i15 + i16 + i17);
    try {
      f();
    } catch (Exception e0) {
      try {
        g();
      } catch (Exception e1) {
        System.out.println(e0.getMessage() + " " + e1.getMessage());
      }
    }
  }

  public static void main(String[] args) {
    h(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17);
  }
}
