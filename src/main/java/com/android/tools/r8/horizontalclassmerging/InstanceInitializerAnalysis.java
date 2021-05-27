// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.graph.ProgramMethod;

public class InstanceInitializerAnalysis {

  public static InstanceInitializerDescription analyze(
      ProgramMethod instanceInitializer, HorizontalClassMergerGraphLens.Builder lensBuilder) {
    // TODO(b/189296638): Return an InstanceInitializerDescription if the given instance initializer
    //  is parent constructor call followed by a sequence of instance field puts.
    return null;
  }
}
