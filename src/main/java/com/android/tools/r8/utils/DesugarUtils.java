// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;

public class DesugarUtils {
  public static DexString appendFullyQualifiedHolderToMethodName(
      DexMethod method, DexItemFactory factory) {
    return factory.createString(
        method.name.toString() + "$" + method.holder.getTypeName().replace('.', '-'));
  }
}
