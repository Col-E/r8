// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.startup.instrumentation;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;

class StartupInstrumentationReferences {

  final DexType instrumentationServerType;
  final DexType instrumentationServerImplType;
  final DexMethod addMethod;

  StartupInstrumentationReferences(DexItemFactory dexItemFactory) {
    instrumentationServerType =
        dexItemFactory.createType("Lcom/android/tools/r8/startup/InstrumentationServer;");
    instrumentationServerImplType =
        dexItemFactory.createType("Lcom/android/tools/r8/startup/InstrumentationServerImpl;");
    addMethod =
        dexItemFactory.createMethod(
            instrumentationServerImplType,
            dexItemFactory.createProto(dexItemFactory.voidType, dexItemFactory.stringType),
            "addMethod");
  }
}
