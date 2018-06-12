// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

public final class KotlinClassFacade extends KotlinInfo {
  @Override
  public Kind getKind() {
    return Kind.Facade;
  }

  @Override
  public boolean isClassFacade() {
    return true;
  }

  @Override
  public KotlinClassFacade asClassFacade() {
    return this;
  }

  KotlinClassFacade() {
    super();
  }
}
