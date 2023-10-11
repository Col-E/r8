// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.library;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.code.AbstractValueSupplier;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.optimize.library.info.ComputedBooleanMethodOptimizationInfoCollection;
import com.android.tools.r8.ir.optimize.library.info.ComputedByteMethodOptimizationInfoCollection;
import com.android.tools.r8.ir.optimize.library.info.ComputedCharacterMethodOptimizationInfoCollection;
import com.android.tools.r8.ir.optimize.library.info.ComputedDoubleMethodOptimizationInfoCollection;
import com.android.tools.r8.ir.optimize.library.info.ComputedFloatMethodOptimizationInfoCollection;
import com.android.tools.r8.ir.optimize.library.info.ComputedIntegerMethodOptimizationInfoCollection;
import com.android.tools.r8.ir.optimize.library.info.ComputedLongMethodOptimizationInfoCollection;
import com.android.tools.r8.ir.optimize.library.info.ComputedMethodOptimizationInfoCollection;
import com.android.tools.r8.ir.optimize.library.info.ComputedShortMethodOptimizationInfoCollection;

public class LibraryOptimizationInfoCollection {

  public static AbstractValue getAbstractReturnValueOrDefault(
      AppView<?> appView,
      InvokeMethod invoke,
      DexClassAndMethod singleTarget,
      ProgramMethod context,
      AbstractValueSupplier abstractValueSupplier,
      AbstractValue defaultValue) {
    ComputedMethodOptimizationInfoCollection computedMethodOptimizationInfoCollection =
        getComputedMethodOptimizationInfoCollection(appView, singleTarget.getHolderType());
    if (computedMethodOptimizationInfoCollection != null) {
      AbstractValue abstractValue =
          computedMethodOptimizationInfoCollection.getAbstractReturnValueOrDefault(
              invoke, singleTarget, context, abstractValueSupplier);
      if (!abstractValue.isUnknown()) {
        return abstractValue;
      }
    }
    return defaultValue;
  }

  private static ComputedMethodOptimizationInfoCollection
      getComputedMethodOptimizationInfoCollection(AppView<?> appView, DexType type) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    switch (type.getDescriptor().size()) {
      case 16:
        // java.lang.Byte
        // java.lang.Long
        if (type.isIdenticalTo(dexItemFactory.boxedByteType)) {
          return new ComputedByteMethodOptimizationInfoCollection(appView);
        } else if (type.isIdenticalTo(dexItemFactory.boxedLongType)) {
          return new ComputedLongMethodOptimizationInfoCollection(appView);
        }
        break;
      case 17:
        // java.lang.Float
        // java.lang.Short
        if (type.isIdenticalTo(dexItemFactory.boxedFloatType)) {
          return new ComputedFloatMethodOptimizationInfoCollection(appView);
        } else if (type.isIdenticalTo(dexItemFactory.boxedShortType)) {
          return new ComputedShortMethodOptimizationInfoCollection(appView);
        }
        break;
      case 18:
        // java.lang.Double
        if (type.isIdenticalTo(dexItemFactory.boxedDoubleType)) {
          return new ComputedDoubleMethodOptimizationInfoCollection(appView);
        }
        break;
      case 19:
        // java.lang.Boolean
        // java.lang.Integer
        if (type.isIdenticalTo(dexItemFactory.boxedBooleanType)) {
          return new ComputedBooleanMethodOptimizationInfoCollection(appView);
        } else if (type.isIdenticalTo(dexItemFactory.boxedIntType)) {
          return new ComputedIntegerMethodOptimizationInfoCollection(appView);
        }
        break;
      case 21:
        // java.lang.Character
        if (type.isIdenticalTo(dexItemFactory.boxedCharType)) {
          return new ComputedCharacterMethodOptimizationInfoCollection(appView);
        }
        break;
      default:
        // Intentionally empty.
        break;
    }
    return null;
  }
}
