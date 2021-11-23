// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library;

import static com.android.tools.r8.graph.DexLibraryClass.asLibraryClassOrNull;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexLibraryClass;
import com.android.tools.r8.graph.FieldAccessFlags;

/**
 * This class synthesizes library fields that we rely on for modeling.
 *
 * <p>For example, we synthesize the field `java.lang.String java.lang.Enum.name` if it is not
 * present. We use this to model that the constructor `void java.lang.Enum.<init>(java.lang.String,
 * int)` initializes `java.lang.String java.lang.Enum.name` to the first argument of the
 * constructor.
 */
public class LibraryFieldSynthesis {

  public static void synthesizeEnumFields(AppView<?> appView) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    DexLibraryClass enumClass =
        asLibraryClassOrNull(appView.definitionFor(dexItemFactory.enumType));
    if (enumClass != null) {
      dexItemFactory.enumMembers.forEachField(
          field -> {
            DexEncodedField definition = enumClass.lookupField(field);
            if (definition == null) {
              enumClass.appendInstanceField(
                  DexEncodedField.syntheticBuilder()
                      .setField(field)
                      .setAccessFlags(
                          FieldAccessFlags.fromCfAccessFlags(
                              Constants.ACC_PRIVATE | Constants.ACC_FINAL))
                      .setApiLevel(appView.computedMinApiLevel())
                      // Will be traced by the enqueuer.
                      .disableAndroidApiLevelCheck()
                      .build());
            }
          });
    }
  }
}
