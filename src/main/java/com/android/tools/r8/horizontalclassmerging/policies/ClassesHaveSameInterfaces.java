// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.horizontalclassmerging.MultiClassSameReferencePolicy;

public class ClassesHaveSameInterfaces extends MultiClassSameReferencePolicy<DexTypeList> {

  @Override
  public DexTypeList getMergeKey(DexProgramClass clazz) {
    return clazz.interfaces.getSorted();
  }
}
