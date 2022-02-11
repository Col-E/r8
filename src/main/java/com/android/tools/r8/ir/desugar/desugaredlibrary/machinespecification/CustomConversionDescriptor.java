// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification;

import com.android.tools.r8.graph.DexMethod;

public class CustomConversionDescriptor {
  private final DexMethod to;
  private final DexMethod from;

  public CustomConversionDescriptor(DexMethod to, DexMethod from) {
    this.to = to;
    this.from = from;
    assert to.getReturnType() == from.getArgumentType(0, true);
    assert from.getReturnType() == to.getArgumentType(0, true);
  }

  public DexMethod getTo() {
    return to;
  }

  public DexMethod getFrom() {
    return from;
  }
}
