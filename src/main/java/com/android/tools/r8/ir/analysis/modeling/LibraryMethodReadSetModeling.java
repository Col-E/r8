// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.modeling;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.analysis.fieldvalueanalysis.AbstractFieldSet;
import com.android.tools.r8.ir.analysis.fieldvalueanalysis.EmptyFieldSet;
import com.android.tools.r8.ir.analysis.fieldvalueanalysis.UnknownFieldSet;
import com.android.tools.r8.ir.code.InvokeMethod;

/** Models if a given library method may cause a program field to be read. */
public class LibraryMethodReadSetModeling {

  @SuppressWarnings("ReferenceEquality")
  public static AbstractFieldSet getModeledReadSetOrUnknown(
      AppView<?> appView, InvokeMethod invoke) {
    DexMethod invokedMethod = invoke.getInvokedMethod();

    // Check if it is a library method that does not have side effects. In that case it is safe to
    // assume that the method does not read any fields, since even if it did, it would not be able
    // to do anything with the values it read (since we will remove such invocations without side
    // effects).
    if (appView
        .getLibraryMethodSideEffectModelCollection()
        .isCallToSideEffectFreeFinalMethod(invoke)) {
      return EmptyFieldSet.getInstance();
    }

    // Model that checkNotNullParameter() does not read any instance fields of the app. This is
    // currently needed for constructors that call checkNotNullParameter() not to be marked as
    // reading any field.
    if (invokedMethod == appView.dexItemFactory().kotlin.intrinsics.checkNotNullParameter
        || invokedMethod == appView.dexItemFactory().kotlin.intrinsics.checkParameterIsNotNull) {
      return EmptyFieldSet.getInstance();
    }

    // Already handled above.
    assert !appView.dexItemFactory().classMethods.isReflectiveNameLookup(invokedMethod);

    return UnknownFieldSet.getInstance();
  }
}
