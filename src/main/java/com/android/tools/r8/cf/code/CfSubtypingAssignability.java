// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.code;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexType;

public class CfSubtypingAssignability extends CfAssignability {

  public CfSubtypingAssignability(AppView<? extends AppInfoWithClassHierarchy> appView) {
    super(appView);
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  boolean internalIsClassTypeAssignableToClassType(DexType source, DexType target) {
    if (source == target || target == dexItemFactory.objectType) {
      return true;
    }
    if (source.toTypeElement(appView).lessThanOrEqual(target.toTypeElement(appView), appView)) {
      return true;
    }
    DexClass targetClass = appView.definitionFor(target);
    if (targetClass != null && targetClass.isInterface()) {
      return true;
    }
    return false;
  }
}
