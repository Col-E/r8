// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.androidapi;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.util.function.BiConsumer;

class AndroidApiLevelDatabaseHelper {

  static void visitAdditionalKnownApiReferences(
      DexItemFactory factory, BiConsumer<DexReference, AndroidApiLevel> apiLevelConsumer) {
    // StringBuilder.substring(int) and StringBuilder.substring(int, int) is not part of
    // api-versions.xml so we add them here. See b/216587554 for related error.
    apiLevelConsumer.accept(
        factory.createMethod(
            factory.stringBuilderType,
            factory.createProto(factory.stringType, factory.intType),
            "substring"),
        AndroidApiLevel.B);
    apiLevelConsumer.accept(
        factory.createMethod(
            factory.stringBuilderType,
            factory.createProto(factory.stringType, factory.intType, factory.intType),
            "substring"),
        AndroidApiLevel.B);
  }
}
