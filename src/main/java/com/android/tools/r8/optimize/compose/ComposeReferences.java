// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.compose;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.lens.GraphLens;

public class ComposeReferences {

  public final DexString changedFieldName;
  public final DexString defaultFieldName;

  public final DexType composableType;
  public final DexType composerType;

  public final DexMethod updatedChangedFlagsMethod;

  public ComposeReferences(DexItemFactory factory) {
    changedFieldName = factory.createString("$$changed");
    defaultFieldName = factory.createString("$$default");

    composableType = factory.createType("Landroidx/compose/runtime/Composable;");
    composerType = factory.createType("Landroidx/compose/runtime/Composer;");

    updatedChangedFlagsMethod =
        factory.createMethod(
            factory.createType("Landroidx/compose/runtime/RecomposeScopeImplKt;"),
            factory.createProto(factory.intType, factory.intType),
            "updateChangedFlags");
  }

  public ComposeReferences(
      DexString changedFieldName,
      DexString defaultFieldName,
      DexType composableType,
      DexType composerType,
      DexMethod updatedChangedFlagsMethod) {
    this.changedFieldName = changedFieldName;
    this.defaultFieldName = defaultFieldName;
    this.composableType = composableType;
    this.composerType = composerType;
    this.updatedChangedFlagsMethod = updatedChangedFlagsMethod;
  }

  public ComposeReferences rewrittenWithLens(GraphLens graphLens, GraphLens codeLens) {
    return new ComposeReferences(
        changedFieldName,
        defaultFieldName,
        graphLens.lookupClassType(composableType, codeLens),
        graphLens.lookupClassType(composerType, codeLens),
        graphLens.getRenamedMethodSignature(updatedChangedFlagsMethod, codeLens));
  }
}
