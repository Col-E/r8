// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexEncodedAnnotation;
import com.android.tools.r8.graph.DexTypeAnnotation;

public class AbsentAnnotationSubject extends AnnotationSubject {

  @Override
  public boolean isPresent() {
    return false;
  }

  @Override
  public boolean isRenamed() {
    throw new Unreachable("Cannot determine if an absent annotation has been renamed");
  }

  @Override
  public boolean isSynthetic() {
    throw new Unreachable("Cannot determine if an absent annotation is synthetic");
  }

  @Override
  public DexEncodedAnnotation getAnnotation() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int isVisible() {
    throw new Unreachable("Subject is absent");
  }

  @Override
  public boolean isTypeAnnotation() {
    throw new Unreachable("Subject is absent");
  }

  @Override
  public DexTypeAnnotation asDexTypeAnnotation() {
    throw new Unreachable("Subject is absent");
  }
}
