// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.references.FieldReference;

public class FieldReferenceUtils {

  public static String toSourceString(FieldReference fieldReference) {
    return fieldReference.getFieldType().getTypeName()
        + " "
        + fieldReference.getHolderClass().getTypeName()
        + "."
        + fieldReference.getFieldName();
  }
}
