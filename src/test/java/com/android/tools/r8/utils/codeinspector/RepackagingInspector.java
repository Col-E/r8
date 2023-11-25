// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.repackaging.RepackagingLens;
import com.android.tools.r8.utils.ClassReferenceUtils;

public class RepackagingInspector {

  private final DexItemFactory dexItemFactory;
  private final RepackagingLens repackagingLens;

  public RepackagingInspector(DexItemFactory dexItemFactory, RepackagingLens repackagingLens) {
    this.dexItemFactory = dexItemFactory;
    this.repackagingLens = repackagingLens;
  }

  public ClassReference getTarget(ClassReference classReference) {
    DexType sourceType = ClassReferenceUtils.toDexType(classReference, dexItemFactory);
    DexType targetType = repackagingLens.getNextClassType(sourceType);
    return targetType.asClassReference();
  }
}
