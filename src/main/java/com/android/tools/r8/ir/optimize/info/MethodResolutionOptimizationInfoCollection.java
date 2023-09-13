// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.info;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexReference;
import java.util.Collections;
import java.util.Map;

/**
 * Collection of optimization info for virtual methods with dynamic dispatch.
 *
 * <p>When a call to a virtual method does not have a single target, we cannot use the optimization
 * info we compute for each method. Given the resolved method, this collection returns a piece of
 * optimization info that is true for all possible dispatch targets (i.e., the join of the
 * optimization of all possible dispatch targets).
 */
public class MethodResolutionOptimizationInfoCollection {

  private static final MethodResolutionOptimizationInfoCollection EMPTY =
      new MethodResolutionOptimizationInfoCollection(Collections.emptyMap());

  private final Map<DexReference, MethodOptimizationInfo> backing;

  MethodResolutionOptimizationInfoCollection(Map<DexReference, MethodOptimizationInfo> backing) {
    this.backing = backing;
  }

  public static MethodResolutionOptimizationInfoCollection empty() {
    return EMPTY;
  }

  public MethodOptimizationInfo get(DexEncodedMethod method, DexClass holder) {
    MethodOptimizationInfo defaultValue = DefaultMethodOptimizationInfo.getInstance();
    if (!holder.isProgramClass()) {
      return defaultValue;
    }
    return backing.getOrDefault(method.getReference(), defaultValue);
  }
}
