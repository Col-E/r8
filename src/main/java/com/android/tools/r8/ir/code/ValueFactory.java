// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.ir.analysis.type.TypeElement;

public interface ValueFactory {

  default Value createValue(TypeElement type) {
    return createValue(type, null);
  }

  Value createValue(TypeElement type, DebugLocalInfo localInfo);
}
