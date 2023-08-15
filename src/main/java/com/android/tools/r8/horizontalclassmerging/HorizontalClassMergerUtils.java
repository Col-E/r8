// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.ProgramField;

public class HorizontalClassMergerUtils {

  public static boolean isClassIdField(AppView<?> appView, ProgramField field) {
    return isClassIdField(appView, field.getDefinition());
  }

  public static boolean isClassIdField(AppView<?> appView, DexEncodedField field) {
    if (field.getType().isIntType()) {
      DexField originalField = appView.graphLens().getOriginalFieldSignature(field.getReference());
      return originalField.getType().isIntType()
          && originalField.getName().startsWith(ClassMerger.CLASS_ID_FIELD_PREFIX);
    }
    return false;
  }
}
