// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.experimental.startup;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;

public class StartupReferences {

  final DexType instrumentationServerImplType;
  final DexMethod addNonSyntheticMethod;
  final DexMethod addSyntheticMethod;

  StartupReferences(DexItemFactory dexItemFactory) {
    instrumentationServerImplType =
        dexItemFactory.createType("Lcom/android/tools/r8/startup/InstrumentationServerImpl;");
    addNonSyntheticMethod =
        dexItemFactory.createMethod(
            instrumentationServerImplType,
            dexItemFactory.createProto(dexItemFactory.voidType, dexItemFactory.stringType),
            "addNonSyntheticMethod");
    addSyntheticMethod =
        dexItemFactory.createMethod(
            instrumentationServerImplType,
            dexItemFactory.createProto(dexItemFactory.voidType, dexItemFactory.stringType),
            "addSyntheticMethod");
  }
}
