// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

public class CompatDexHelper {
  public static void ignoreDexInArchive(BaseCommand.Builder builder) {
    builder.setIgnoreDexInArchive(true);
  }
}
