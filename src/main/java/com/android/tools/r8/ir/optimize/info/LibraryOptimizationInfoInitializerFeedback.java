// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.info;

import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.ir.analysis.value.AbstractValue;

public class LibraryOptimizationInfoInitializerFeedback extends OptimizationFeedbackSimple {

  private static LibraryOptimizationInfoInitializerFeedback INSTANCE =
      new LibraryOptimizationInfoInitializerFeedback();

  LibraryOptimizationInfoInitializerFeedback() {}

  public static LibraryOptimizationInfoInitializerFeedback getInstance() {
    return INSTANCE;
  }

  public void setAbstractFieldValue(AbstractValue abstractValue, DexEncodedField field) {
    field.getMutableOptimizationInfo().setAbstractValue(abstractValue, field);
  }
}
