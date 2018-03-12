// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

public class IdentifierUtils {
  public static boolean isDexIdentifierStart(char ch) {
    // Dex does not have special restrictions on the first char of an identifier.
    return isDexIdentifierPart(ch);
  }

  public static boolean isDexIdentifierPart(char ch) {
    return isSimpleNameChar(ch);
  }

  private static boolean isSimpleNameChar(char ch) {
    if (ch >= 'A' && ch <= 'Z') {
      return true;
    }
    if (ch >= 'a' && ch <= 'z') {
      return true;
    }
    if (ch >= '0' && ch <= '9') {
      return true;
    }
    if (ch == '$' || ch == '-' || ch == '_') {
      return true;
    }
    if (ch >= 0x00a1 && ch <= 0x1fff) {
      return true;
    }
    if (ch >= 0x2010 && ch <= 0x2027) {
      return true;
    }
    if (ch >= 0x2030 && ch <= 0xd7ff) {
      return true;
    }
    if (ch >= 0xe000 && ch <= 0xffef) {
      return true;
    }
    if (ch >= 0x10000 && ch <= 0x10ffff) {
      return true;
    }
    return false;
  }
}
