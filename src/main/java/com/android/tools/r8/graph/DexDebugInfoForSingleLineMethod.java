// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.graph.DexDebugInfo.EventBasedDebugInfo;

public class DexDebugInfoForSingleLineMethod extends EventBasedDebugInfo {

  private static final DexDebugInfoForSingleLineMethod INSTANCE =
      new DexDebugInfoForSingleLineMethod(0, DexString.EMPTY_ARRAY, DexDebugEvent.EMPTY_ARRAY);

  private DexDebugInfoForSingleLineMethod(
      int startLine, DexString[] parameters, DexDebugEvent[] events) {
    super(startLine, parameters, events);
  }

  public static DexDebugInfoForSingleLineMethod getInstance() {
    return INSTANCE;
  }
}
