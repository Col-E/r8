// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeMetadataProvider;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.optimize.DeadCodeRemover;
import com.android.tools.r8.utils.Timing;

public abstract class IRFinalizer<C extends Code> {

  protected final AppView<?> appView;
  protected final DeadCodeRemover deadCodeRemover;

  public IRFinalizer(AppView<?> appView, DeadCodeRemover deadCodeRemover) {
    this.appView = appView;
    this.deadCodeRemover = deadCodeRemover;
  }

  public abstract C finalizeCode(
      IRCode code, BytecodeMetadataProvider bytecodeMetadataProvider, Timing timing);
}
