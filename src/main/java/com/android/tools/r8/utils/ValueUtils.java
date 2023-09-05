// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.Value;

public class ValueUtils {

  @SuppressWarnings("ReferenceEquality")
  public static boolean isStringBuilder(Value value, DexItemFactory dexItemFactory) {
    TypeElement type = value.getType();
    return type.isClassType()
        && type.asClassType().getClassType() == dexItemFactory.stringBuilderType;
  }

  @SuppressWarnings("ReferenceEquality")
  public static boolean isNonNullStringBuilder(Value value, DexItemFactory dexItemFactory) {
    while (true) {
      if (value.isPhi()) {
        return false;
      }

      Instruction definition = value.getDefinition();
      if (definition.isNewInstance()) {
        NewInstance newInstance = definition.asNewInstance();
        return newInstance.clazz == dexItemFactory.stringBuilderType;
      }

      if (definition.isInvokeVirtual()) {
        InvokeVirtual invoke = definition.asInvokeVirtual();
        if (dexItemFactory.stringBuilderMethods.isAppendMethod(invoke.getInvokedMethod())) {
          value = invoke.getReceiver();
          continue;
        }
      }

      // Unhandled definition.
      return false;
    }
  }
}
