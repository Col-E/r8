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
    // Test if we have a SimpleChar,
    // see https://source.android.com/devices/tech/dalvik/dex-format#string-syntax.
    //
    // NOTE: we assume the input strings are well-formed UTF-16 strings.
    // This matters when checking the range 0x10000..0x10ffff, which is represented by a pair of
    // UTF-16 surrogates. In that case, instead of checking the actual code point, we let pass all
    // characters which are high or low surrogates.
    return ('A' <= ch && ch <= 'Z')
        || ('a' <= ch && ch <= 'z')
        || ('0' <= ch && ch <= '9')
        || ch == '$'
        || ch == '-'
        || ch == '_'
        || (0x00a1 <= ch && ch <= 0x1fff)
        || (0x2010 <= ch && ch <= 0x2027)
        // The next range consists of these ranges:
        // 0x2030..0xd7ff, then
        // 0xd800..0xdfff (low or high surrogates), then
        // 0xe000..0xffef.
        || (0x2030 <= ch && ch <= 0xffef);
  }
}
