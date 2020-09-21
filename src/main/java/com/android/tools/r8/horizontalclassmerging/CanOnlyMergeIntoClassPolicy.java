// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.graph.DexProgramClass;

public abstract class CanOnlyMergeIntoClassPolicy extends SingleClassPolicy {
  public abstract boolean canOnlyMergeInto(DexProgramClass clazz);

  @Override
  public boolean canMerge(DexProgramClass program) {
    // TODO(b/165577835): Allow merging of classes that must be the target of their merge group.
    return !canOnlyMergeInto(program);
  }
}
