// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.HorizontalClassMerger.Mode;

public class AtMostOneClassInitializer extends AtMostOneClassThatMatchesPolicy {

  public AtMostOneClassInitializer(Mode mode) {
    // TODO(b/182124475): Allow merging groups with multiple <clinit> methods in the final round of
    //  merging.
    assert mode.isFinal();
  }

  @Override
  boolean atMostOneOf(DexProgramClass clazz) {
    return clazz.hasClassInitializer();
  }

  @Override
  public String getName() {
    return "AtMostOneClassInitializer";
  }
}
