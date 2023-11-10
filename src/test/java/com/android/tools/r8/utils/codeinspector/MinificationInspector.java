// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.ClassReferenceUtils;

public class MinificationInspector {

  private final DexItemFactory dexItemFactory;
  private final NamingLens namingLens;

  public MinificationInspector(DexItemFactory dexItemFactory, NamingLens namingLens) {
    this.dexItemFactory = dexItemFactory;
    this.namingLens = namingLens;
  }

  public ClassReference getTarget(ClassReference classReference) {
    DexType sourceType = ClassReferenceUtils.toDexType(classReference, dexItemFactory);
    DexString targetDescriptor = namingLens.lookupClassDescriptor(sourceType);
    return Reference.classFromDescriptor(targetDescriptor.toString());
  }
}
