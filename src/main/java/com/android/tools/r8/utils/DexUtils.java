// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

public class DexUtils {
  public static String getDefaultDexFileName(int fileIndex) {
    return fileIndex == 0
        ? "classes" + FileUtils.DEX_EXTENSION
        : ("classes" + (fileIndex + 1) + FileUtils.DEX_EXTENSION);
  }
}
