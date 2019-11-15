// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress.b142682636;

class Regress142682636 {
  static void foo(long v, byte[] a, int s, int b) {
    for (int i = s; i < s + b; i++) {
      a[i] = (byte) v;
      v = v >> 8;
    }
  }

  static void bar(int s, long v, byte[] a, int b) {
    for (int i = s; i < s + b; i++) {
      a[i] = (byte) v;
      v = v >> 8;
    }
  }
}
