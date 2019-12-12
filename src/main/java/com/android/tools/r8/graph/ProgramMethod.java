// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.errors.Unreachable;

/** Type representing a method definition in the programs compilation unit and its holder. */
public class ProgramMethod {
  public final DexProgramClass holder;
  public final DexEncodedMethod method;

  public ProgramMethod(DexProgramClass holder,  DexEncodedMethod method) {
    assert holder.type == method.method.holder;
    this.holder = holder;
    this.method = method;
  }

  @Override
  public boolean equals(Object obj) {
    throw new Unreachable("Unsupported attempt at comparing ProgramMethod");
  }

  @Override
  public int hashCode() {
    throw new Unreachable("Unsupported attempt at computing the hashcode of ProgramMethod");
  }
}
