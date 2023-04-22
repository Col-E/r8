// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import javax.annotation.Nonnull;

public interface ProgramOrClasspathClass
    extends ClassDefinition, ProgramOrClasspathDefinition, ClassResolutionResult {

  @Nonnull
  ProgramOrClasspathClass copyClass();

  DexClass asDexClass();
}
