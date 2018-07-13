// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.dexinspector;

import com.android.tools.r8.graph.DexType;

public class TypeSubject extends Subject {

  private final DexInspector dexInspector;
  private final DexType dexType;

  TypeSubject(DexInspector dexInspector, DexType dexType) {
    this.dexInspector = dexInspector;
    this.dexType = dexType;
  }

  @Override
  public boolean isPresent() {
    return true;
  }

  @Override
  public boolean isRenamed() {
    return false;
  }

  public boolean is(String type) {
    return dexType.equals(dexInspector.toDexType(type));
  }

  public String toString() {
    return dexType.toSourceString();
  }
}
