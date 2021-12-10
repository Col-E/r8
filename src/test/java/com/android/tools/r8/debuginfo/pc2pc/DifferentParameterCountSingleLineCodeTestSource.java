// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debuginfo.pc2pc;

class DifferentParameterCountSingleLineCodeTestSource {

  public static RuntimeException args0() {
    throw System.nanoTime() < 0 ? null : new IllegalStateException("DONE!");
  }

  public static RuntimeException args1(String arg1) {
    return !arg1.equals("asdf") ? args0() : null;
  }

  public static RuntimeException args2(String arg1, Object arg2) {
    return !arg1.equals(arg2) ? args1(arg1) : null;
  }

  public static void main(String[] args) {
    throw args2(System.nanoTime() < 0 ? args[0] : "foo", args.length > 0 ? args[0] : "bar");
  }
}
