// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import kotlinx.metadata.KmClassifier;

public class KmClassifierSubject extends Subject {

  private final KmClassifier classifier;

  public KmClassifierSubject(KmClassifier classifier) {
    this.classifier = classifier;
  }

  public boolean isTypeParameter() {
    return classifier instanceof KmClassifier.TypeParameter;
  }

  public KmClassifier.TypeParameter asTypeParameter() {
    return (KmClassifier.TypeParameter) classifier;
  }

  public boolean isTypeAlias() {
    return classifier instanceof KmClassifier.TypeAlias;
  }

  public KmClassifier.TypeAlias asTypeAlias() {
    return (KmClassifier.TypeAlias) classifier;
  }

  @Override
  public boolean isPresent() {
    return true;
  }

  @Override
  public boolean isRenamed() {
    return false;
  }

  @Override
  public boolean isSynthetic() {
    return false;
  }
}
