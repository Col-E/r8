// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.SingleClassPolicy;
import it.unimi.dsi.fastutil.objects.Reference2BooleanMap;
import it.unimi.dsi.fastutil.objects.Reference2BooleanOpenHashMap;

public class NoEnums extends SingleClassPolicy {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final Reference2BooleanMap<DexClass> cache = new Reference2BooleanOpenHashMap<>();

  public NoEnums(AppView<? extends AppInfoWithClassHierarchy> appView) {
    this.appView = appView;
  }

  @Override
  public void clear() {
    cache.clear();
  }

  @Override
  public String getName() {
    return "NoEnums";
  }

  @Override
  public boolean canMerge(DexProgramClass program) {
    if (program.isEnum()) {
      return false;
    }
    if (isEnumSubtype(program)) {
      return false;
    }
    return true;
  }

  @SuppressWarnings("ReferenceEquality")
  private boolean isEnumSubtype(DexClass clazz) {
    if (cache.containsKey(clazz)) {
      return cache.getBoolean(clazz);
    }
    boolean result;
    if (clazz.type == appView.dexItemFactory().objectType) {
      result = false;
    } else if (clazz.type == appView.dexItemFactory().enumType) {
      result = true;
    } else {
      DexClass superClass = clazz.hasSuperType() ? appView.definitionFor(clazz.superType) : null;
      result = superClass != null && isEnumSubtype(superClass);
    }
    cache.put(clazz, result);
    return result;
  }
}
