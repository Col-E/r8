// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.itf;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.EnclosingMethodAttribute;
import com.android.tools.r8.graph.InnerClassAttribute;
import com.android.tools.r8.ir.desugar.itf.InterfaceProcessor.InterfaceProcessorNestedGraphLens;

class InterfaceMethodRewriterFixup {
  private final AppView<?> appView;
  private final InterfaceProcessorNestedGraphLens graphLens;

  InterfaceMethodRewriterFixup(AppView<?> appView, InterfaceProcessorNestedGraphLens graphLens) {
    this.appView = appView;
    this.graphLens = graphLens;
  }

  void run() {
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      if (clazz.getEnclosingMethodAttribute() != null
          && clazz.getEnclosingMethodAttribute().getEnclosingMethod() != null) {
        // Only relevant to rewrite for local or anonymous classes, as the interface
        // desugaring only moves methods to the companion class.
        InnerClassAttribute innerClassAttributeForThisClass =
            clazz.getInnerClassAttributeForThisClass();
        if (innerClassAttributeForThisClass != null
            && innerClassAttributeForThisClass.getOuter() == null) {
          clazz.setEnclosingMethodAttribute(
              fixupEnclosingMethodAttribute(clazz.getEnclosingMethodAttribute()));
        }
      }
    }
  }

  @SuppressWarnings("ReferenceEquality")
  private EnclosingMethodAttribute fixupEnclosingMethodAttribute(
      EnclosingMethodAttribute enclosingMethodAttribute) {
    DexMethod enclosingMethod = enclosingMethodAttribute.getEnclosingMethod();
    DexMethod mappedEnclosingMethod = fixupDexMethodForEnclosingMethod(enclosingMethod);
    return mappedEnclosingMethod != enclosingMethod
        ? new EnclosingMethodAttribute(mappedEnclosingMethod)
        : enclosingMethodAttribute;
  }

  private DexMethod fixupDexMethodForEnclosingMethod(DexMethod method) {
    if (method == null) {
      return null;
    }
    // Map default methods to their companion methods.
    DexMethod mappedMethod = graphLens.getExtraNewMethodSignatures().getRepresentativeValue(method);
    if (mappedMethod != null) {
      return mappedMethod;
    }
    // For other methods moved to the companion class use the lens mapping.
    return graphLens.getRenamedMethodSignature(method);
  }
}
