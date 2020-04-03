// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import kotlinx.metadata.KmClassifier;

// Provides access to information about a kotlin classifier
public class KotlinClassifierInfo {

  private static boolean isClass(KmClassifier classifier) {
    return classifier instanceof KmClassifier.Class;
  }

  private static KmClassifier.Class getClassClassifier(KmClassifier classifier) {
    return (KmClassifier.Class) classifier;
  }

  private static boolean isTypeAlias(KmClassifier classifier) {
    return classifier instanceof KmClassifier.TypeAlias;
  }

  private static KmClassifier.TypeAlias getTypeAlias(KmClassifier classifier) {
    return (KmClassifier.TypeAlias) classifier;
  }

  private static boolean isTypeParameter(KmClassifier classifier) {
    return classifier instanceof KmClassifier.TypeParameter;
  }

  private static KmClassifier.TypeParameter getTypeParameter(KmClassifier classifier) {
    return (KmClassifier.TypeParameter) classifier;
  }

  public static boolean equals(KmClassifier one, KmClassifier other) {
    if (isClass(one)) {
      return isClass(other)
          && getClassClassifier(one).getName().equals(getClassClassifier(other).getName());
    }
    if (isTypeAlias(one)) {
      return isTypeAlias(other)
          && getTypeAlias(one).getName().equals(getTypeAlias(other).getName());
    }
    assert isTypeParameter(one);
    return isTypeParameter(other)
        && getTypeParameter(one).getId() == getTypeParameter(other).getId();
  }
}
