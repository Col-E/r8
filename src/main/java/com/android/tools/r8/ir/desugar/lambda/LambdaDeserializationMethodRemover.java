// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.lambda;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import java.util.Collection;

public class LambdaDeserializationMethodRemover {

  /** Remove lambda deserialization methods. */
  public static void run(AppView<AppInfo> appView) {
    if (appView.options().desugarState.isOn()) {
      run(appView, appView.appInfo().classes());
    }
  }

  /** Remove lambda deserialization methods. */
  public static void run(AppView<?> appView, Collection<DexProgramClass> classes) {
    assert appView.options().desugarState.isOn() || classes.isEmpty();
    DexMethod reference = appView.dexItemFactory().deserializeLambdaMethod;
    for (DexProgramClass clazz : classes) {
      DexEncodedMethod method = clazz.lookupMethod(reference);
      if (method != null && method.getAccessFlags().isPrivate()) {
        clazz.removeMethod(reference);
      }
    }
  }
}
