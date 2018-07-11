// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

public class ArrayDimensionGreaterThanSevenTest {

  public static float foo(int x) {
    try {
      float[] fs1 = new float[] {42f};
      float[][] fs2 = new float[][] {fs1};
      float[][][] fs3 = new float[][][] {fs2};
      float[][][][] fs4 = new float[][][][] {fs3};
      float[][][][][] fs5 = new float[][][][][] {fs4};
      float[][][][][][] fs6 = new float[][][][][][] {fs5};
      float[][][][][][][] fs7 = new float[][][][][][][] {fs6};
      float[][][][][][][][] fs8 = new float[][][][][][][][] {fs7};
      while (x-- > 0) {
        try {
          fs8 = x == 0 ? fs8 : null;
          fs7 = x == 1 ? fs8[1] : fs8[0];
          fs6 = x == 2 ? fs7[1] : fs7[0];
          fs5 = x == 3 ? fs6[1] : fs6[0];
          fs4 = x == 4 ? fs5[1] : fs5[0];
          fs3 = x == 5 ? fs4[1] : fs4[0];
          fs2 = x == 6 ? fs3[1] : fs3[0];
          fs1 = x == 7 ? fs2[1] : fs2[0];
        } catch (NullPointerException e) {
          System.out.println("null pointer");
        }
      }
    } catch (RuntimeException e) {
      return -1f;
    }
    return 42;
  }

  public static void main(String[] args) {
    System.out.println(foo(args.length + 1));
  }
}
