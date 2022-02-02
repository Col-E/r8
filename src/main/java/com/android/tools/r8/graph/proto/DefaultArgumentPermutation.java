// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.proto;

public class DefaultArgumentPermutation extends ArgumentPermutation {

  private static final DefaultArgumentPermutation INSTANCE = new DefaultArgumentPermutation();

  private DefaultArgumentPermutation() {}

  public static DefaultArgumentPermutation get() {
    return INSTANCE;
  }

  @Override
  public int getNewArgumentIndex(int argumentIndex) {
    return argumentIndex;
  }

  @Override
  public boolean isDefault() {
    return true;
  }
}
