// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.analysis.EnqueuerAnalysis;
import com.android.tools.r8.shaking.EnqueuerWorklist;
import com.google.common.collect.Sets;
import java.util.Set;

public class KotlinMetadataEnqueuerExtension extends EnqueuerAnalysis {

  private final AppView<?> appView;
  private final Set<DexEncodedMethod> keepByteCodeFunctions = Sets.newIdentityHashSet();

  public KotlinMetadataEnqueuerExtension(AppView<?> appView) {
    this.appView = appView;
  }

  @Override
  public void processNewlyLiveClass(
      DexProgramClass clazz, EnqueuerWorklist worklist, DexDefinitionSupplier definitionSupplier) {
    Kotlin kotlin = appView.dexItemFactory().kotlin;
    clazz.setKotlinInfo(
        KotlinClassMetadataReader.getKotlinInfo(
            kotlin,
            clazz,
            definitionSupplier,
            appView.options().reporter,
            keepByteCodeFunctions::add));
  }

  @Override
  public void done() {
    appView.setKotlinInlineFunctions(keepByteCodeFunctions);
  }
}
