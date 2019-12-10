// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.kotlin.Kotlin;
import kotlinx.metadata.KmClass;

public class AbsentKmClassSubject extends KmClassSubject {

  @Override
  public DexClass getDexClass() {
    return null;
  }

  @Override
  public KmClass getKmClass(Kotlin kotlin) {
    return null;
  }

  @Override
  public boolean isPresent() {
    return false;
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
