// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import java.util.Map;

public class EmulatedInterfaceDescriptor {
  private final DexType rewrittenType;
  private final Map<DexMethod, EmulatedDispatchMethodDescriptor> emulatedMethods;

  public EmulatedInterfaceDescriptor(
      DexType rewrittenType, Map<DexMethod, EmulatedDispatchMethodDescriptor> emulatedMethods) {
    this.rewrittenType = rewrittenType;
    this.emulatedMethods = emulatedMethods;
  }

  public DexType getRewrittenType() {
    return rewrittenType;
  }

  public Map<DexMethod, EmulatedDispatchMethodDescriptor> getEmulatedMethods() {
    return emulatedMethods;
  }
}
