// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

public final class CharSequenceMethods {

  public static int compare(CharSequence a, CharSequence b) {
    int aLen = a.length(); // implicit null check
    int bLen = b.length(); // implicit null check
    if (a == b) {
      return 0;
    }
    for (int i = 0, stop = Math.min(aLen, bLen); i < stop; i++) {
      char aChar = a.charAt(i);
      char bChar = b.charAt(i);
      if (aChar != bChar) {
        return (int) aChar - (int) bChar; // See CharacterMethods.compare
      }
    }
    // Prefixes match so we need to return a value based on which is longer.
    return aLen - bLen;
  }
}
