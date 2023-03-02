// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.origin;

public class GlobalSyntheticOrigin extends Origin {

  private static final Origin INSTANCE = new GlobalSyntheticOrigin(Origin.root());

  public static Origin instance() {
    return INSTANCE;
  }

  protected GlobalSyntheticOrigin(Origin parent) {
    super(parent);
  }

  @Override
  public String part() {
    return "<synthetic>";
  }
}
