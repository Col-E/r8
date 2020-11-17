// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.MergeGroup;
import com.android.tools.r8.horizontalclassmerging.MultiClassPolicy;
import java.util.Collection;
import java.util.Collections;

public class DontMergeIntoLessVisible extends MultiClassPolicy {

  @Override
  public Collection<MergeGroup> apply(MergeGroup group) {
    DexProgramClass clazz = group.removeFirst(DexClass::isPublic);
    if (clazz != null) {
      group.addFirst(clazz);
    }
    return Collections.singletonList(group);
  }
}
