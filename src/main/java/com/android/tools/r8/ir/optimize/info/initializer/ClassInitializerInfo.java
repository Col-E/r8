// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.info.initializer;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.ir.optimize.info.MethodOptimizationInfoCollector;

/**
 * Defines class trivial initialized, see details in comments {@link
 * MethodOptimizationInfoCollector#computeClassInitializerInfo}.
 */
public final class ClassInitializerInfo extends InitializerInfo {

  public final DexField field;

  public ClassInitializerInfo(DexField field) {
    this.field = field;
  }

  @Override
  public ClassInitializerInfo asClassInitializerInfo() {
    return this;
  }
}
