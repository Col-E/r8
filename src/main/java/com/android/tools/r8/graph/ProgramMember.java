// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.kotlin.KotlinMemberLevelInfo;

public interface ProgramMember<D extends DexEncodedMember<D, R>, R extends DexMember<D, R>>
    extends ProgramDefinition {

  @Override
  default DexProgramClass getContextClass() {
    return getHolder();
  }

  @Override
  D getDefinition();

  DexProgramClass getHolder();

  DexType getHolderType();

  KotlinMemberLevelInfo getKotlinInfo();

  default void clearGenericSignature() {
    getDefinition().clearGenericSignature();
  }

  default void clearKotlinInfo() {
    getDefinition().clearKotlinInfo();
  }
}
