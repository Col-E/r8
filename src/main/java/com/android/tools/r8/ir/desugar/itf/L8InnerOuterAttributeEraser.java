// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.itf;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.InnerClassAttribute;
import java.util.ArrayList;

/**
 * If D8/R8 rewrites a type but not its enclosing/inner types, then the package does not match
 * between the types and the inner/outer class attributes cannot be maintained nor be valid anymore.
 * This overrides keep rules regarding inner/outer attributes, and should be applied only to the L8
 * compilation itself.
 */
public class L8InnerOuterAttributeEraser {

  private final AppView<?> appView;

  public L8InnerOuterAttributeEraser(AppView<?> appView) {
    this.appView = appView;
  }

  public void run() {
    assert appView.options().isDesugaredLibraryCompilation();
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      eraseInvalidAttributes(clazz);
    }
  }

  private void eraseInvalidAttributes(DexProgramClass clazz) {
    boolean rewritten = hasRewrittenType(clazz.type);

    if (clazz.getEnclosingMethodAttribute() != null) {
      DexType outerType = clazz.getEnclosingMethodAttribute().getEnclosingClass();
      if (outerType != null && hasRewrittenType(outerType) != rewritten) {
        clazz.clearEnclosingMethodAttribute();
      }
      // Erasing enclosing method attributes (method, not class) does not seem to make any
      // difference at this point. It can be added here if relevant.
    }

    if (!clazz.getInnerClasses().isEmpty()) {
      ArrayList<InnerClassAttribute> innerClasses = new ArrayList<>();
      for (InnerClassAttribute innerClass : clazz.getInnerClasses()) {
        if (hasRewrittenType(innerClass.getInner()) == rewritten) {
          innerClasses.add(innerClass);
        }
      }
      if (innerClasses.size() != clazz.getInnerClasses().size()) {
        clazz.setInnerClasses(innerClasses);
      }
    }
  }

  private boolean hasRewrittenType(DexType type) {
    return appView
        .options()
        .machineDesugaredLibrarySpecification
        .getRewriteType()
        .containsKey(type);
  }
}
