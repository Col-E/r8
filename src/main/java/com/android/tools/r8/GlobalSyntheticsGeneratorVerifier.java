// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import java.util.function.Consumer;

public class GlobalSyntheticsGeneratorVerifier {

  public static void forEachExpectedClass(
      DexItemFactory dexItemFactory, int minApi, Consumer<DexType> consumer) {
    consumer.accept(dexItemFactory.methodHandlesType);
    consumer.accept(dexItemFactory.methodHandlesLookupType);
    consumer.accept(dexItemFactory.recordType);
    consumer.accept(dexItemFactory.varHandleType);
  }

  public static boolean verifyExpectedClassesArePresent(AppView<?> appView) {
    forEachExpectedClass(
        appView.dexItemFactory(),
        appView.options().getMinApiLevel().getLevel(),
        type -> {
          assert appView.hasDefinitionFor(type);
        });
    return true;
  }
}
