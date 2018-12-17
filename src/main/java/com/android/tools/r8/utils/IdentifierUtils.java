// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

public class IdentifierUtils {
  public static boolean isDexIdentifierStart(int cp) {
    // Dex does not have special restrictions on the first char of an identifier.
    return isDexIdentifierPart(cp);
  }

  public static boolean isDexIdentifierPart(int cp) {
    return isSimpleNameChar(cp);
  }

  public static boolean isQuestionMark(int cp) {
    return cp == '?';
  }

  private static boolean isSimpleNameChar(int cp) {
    // See https://source.android.com/devices/tech/dalvik/dex-format#string-syntax.
    return ('A' <= cp && cp <= 'Z')
        || ('a' <= cp && cp <= 'z')
        || ('0' <= cp && cp <= '9')
        || cp == '$'
        || cp == '-'
        || cp == '_'
        || (0x00a1 <= cp && cp <= 0x1fff)
        || (0x2010 <= cp && cp <= 0x2027)
        || (0x2030 <= cp && cp <= 0xd7ff)
        || (0xe000 <= cp && cp <= 0xffef)
        || (0x10000 <= cp && cp <= 0x10ffff);
  }
}
