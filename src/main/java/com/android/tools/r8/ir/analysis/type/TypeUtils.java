// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.type;

import com.android.tools.r8.graph.AppView;

public class TypeUtils {

  public static boolean isNullPointerException(TypeElement type, AppView<?> appView) {
    return type.isClassType()
        && type.asClassType().getClassType() == appView.dexItemFactory().npeType;
  }
}
