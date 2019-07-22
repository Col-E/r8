// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

public class ThrowNPETest {

  private static void throwNPESameLine(int i) {
    throw new NullPointerException();
  }

  private static void throwNPESeparateLine(int i) {
    // It seems that javac doesn't generate two different line numbers for the throw and
    // the null pointer instantiation.
    throw
        new NullPointerException();
  }

  private static void throwNPEFromLocal() {
    NullPointerException e = new NullPointerException();
    throw e;
  }

  public static void main(String[] args) {
    try { throwNPESameLine(0); } catch (NullPointerException e) { }
    try { throwNPESeparateLine(1); } catch (NullPointerException e) { }
    try { throwNPEFromLocal(); } catch (NullPointerException e) { }
  }
}
