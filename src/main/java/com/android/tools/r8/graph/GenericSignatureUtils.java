// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.graph.GenericSignature.DexDefinitionSignature;

public class GenericSignatureUtils {

  public static boolean verifyNoDuplicateGenericDefinitions(
      DexDefinitionSignature<?> signature, DexAnnotationSet annotations) {
    assert signature != null;
    if (signature.hasNoSignature() || annotations == null) {
      return true;
    }
    // The check is on the string descriptor to allow for not passing in a factory.
    for (DexAnnotation annotation : annotations.annotations) {
      assert !annotation
          .getAnnotationType()
          .descriptor
          .toString()
          .equals(DexItemFactory.dalvikAnnotationSignatureString);
    }
    return true;
  }
}
