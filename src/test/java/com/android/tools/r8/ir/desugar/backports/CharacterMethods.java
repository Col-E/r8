// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

public final class CharacterMethods {

  public static int compare(char a, char b) {
    return (int) a - (int) b;
  }

  public static String toStringCodepoint(int codepoint) {
    return new String(Character.toChars(codepoint));
  }
}
