// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.utils;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import java.util.concurrent.ConcurrentHashMap;

public abstract class ConcurrentNonProgramMethodsCollection extends NonProgramMethodsCollection {

  ConcurrentNonProgramMethodsCollection(AppView<? extends AppInfoWithClassHierarchy> appView) {
    super(appView, new ConcurrentHashMap<>());
  }

  public static ConcurrentNonProgramMethodsCollection createVirtualMethodsCollection(
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    return new ConcurrentNonProgramMethodsCollection(appView) {
      @Override
      public boolean test(DexClassAndMethod method) {
        return method.getAccessFlags().belongsToVirtualPool();
      }
    };
  }
}
