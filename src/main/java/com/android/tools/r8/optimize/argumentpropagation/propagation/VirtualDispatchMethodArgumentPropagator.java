// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.propagation;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodStateCollection;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.function.Consumer;

public class VirtualDispatchMethodArgumentPropagator extends MethodArgumentPropagator {

  public VirtualDispatchMethodArgumentPropagator(
      AppView<AppInfoWithLiveness> appView,
      ImmediateProgramSubtypingInfo immediateSubtypingInfo,
      MethodStateCollection methodStates) {
    super(appView, immediateSubtypingInfo, methodStates);
  }

  @Override
  public void forEachSubClass(DexProgramClass clazz, Consumer<DexProgramClass> consumer) {
    immediateSubtypingInfo.getSubclasses(clazz).forEach(consumer);
  }

  @Override
  public boolean isRoot(DexProgramClass clazz) {
    DexProgramClass superclass = asProgramClassOrNull(appView.definitionFor(clazz.getSuperType()));
    if (superclass != null) {
      return false;
    }
    for (DexType implementedType : clazz.getInterfaces()) {
      DexProgramClass implementedClass =
          asProgramClassOrNull(appView.definitionFor(implementedType));
      if (implementedClass != null) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void visit(DexProgramClass clazz) {
    throw new Unimplemented();
  }

  @Override
  public void prune(DexProgramClass clazz) {
    throw new Unimplemented();
  }
}
