// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.analysis.EnqueuerAnalysis;
import com.android.tools.r8.shaking.Enqueuer;

public class KotlinMetadataEnqueuerExtension extends EnqueuerAnalysis {

  private final AppView<?> appView;
  private final DexDefinitionSupplier definitionSupplier;

  public KotlinMetadataEnqueuerExtension(
      AppView<?> appView, DexDefinitionSupplier definitionSupplier) {
    this.appView = appView;
    this.definitionSupplier = definitionSupplier;
  }

  @Override
  public void done(Enqueuer enqueuer) {
    // We will process kotlin.Metadata even if the type is not present in the program, as long as
    // a direct keep is specified.
    boolean keepMetadata =
        enqueuer.isPinned(appView.dexItemFactory().kotlinMetadataType)
            || KotlinMetadataUtils.isKeepingKotlinMetadataInRules(appView.options());
    enqueuer.forAllLiveClasses(
        clazz -> {
          clazz.setKotlinInfo(
              KotlinClassMetadataReader.getKotlinInfo(
                  appView.dexItemFactory().kotlin,
                  clazz,
                  definitionSupplier,
                  appView.options().reporter,
                  !keepMetadata || !enqueuer.isPinned(clazz.type)));
        });
  }
}
