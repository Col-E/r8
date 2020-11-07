// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.origin.Origin;

public interface ProgramDefinition {

  DexProgramClass getContextClass();

  AccessFlags<?> getAccessFlags();

  DexType getContextType();

  DexDefinition getDefinition();

  DexReference getReference();

  Origin getOrigin();

  default boolean isProgramClass() {
    return false;
  }

  default DexProgramClass asProgramClass() {
    return null;
  }

  default boolean isProgramField() {
    return false;
  }

  default ProgramField asProgramField() {
    return null;
  }

  default boolean isProgramMethod() {
    return false;
  }

  default ProgramMethod asProgramMethod() {
    return null;
  }
}
