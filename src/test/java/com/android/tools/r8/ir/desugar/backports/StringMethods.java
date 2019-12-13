// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

import java.util.Iterator;

public final class StringMethods {

  public static String joinArray(CharSequence delimiter, CharSequence... elements) {
    if (delimiter == null) throw new NullPointerException("delimiter");
    StringBuilder builder = new StringBuilder();
    if (elements.length > 0) {
      builder.append(elements[0]);
      for (int i = 1; i < elements.length; i++) {
        builder.append(delimiter);
        builder.append(elements[i]);
      }
    }
    return builder.toString();
  }

  public static String joinIterable(CharSequence delimiter,
      Iterable<? extends CharSequence> elements) {
    if (delimiter == null) throw new NullPointerException("delimiter");
    StringBuilder builder = new StringBuilder();
    Iterator<? extends CharSequence> iterator = elements.iterator();
    if (iterator.hasNext()) {
      builder.append(iterator.next());
      while (iterator.hasNext()) {
        builder.append(delimiter);
        builder.append(iterator.next());
      }
    }
    return builder.toString();
  }

  public static String repeat(String receiver, int count) {
    if (count < 0) {
      throw new IllegalArgumentException("count is negative: " + count);
    }
    int length = receiver.length();
    if (count == 0 || length == 0) {
      return "";
    }
    if (count == 1) {
      return receiver;
    }
    StringBuilder builder = new StringBuilder(length * count);
    for (int i = 0; i < count; i++) {
      builder.append(receiver);
    }
    return builder.toString();
  }
}
