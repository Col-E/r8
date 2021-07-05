// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

public class ImmutableArrayUtils {

  public static <T> T[] set(T[] array, int index, T element) {
    T[] clone = array.clone();
    clone[index] = element;
    return clone;
  }
}
