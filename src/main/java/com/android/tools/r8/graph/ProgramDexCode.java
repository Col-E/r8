// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

public class ProgramDexCode {

  private final DexCode code;
  private final ProgramMethod method;

  public ProgramDexCode(DexCode code, ProgramMethod method) {
    this.code = code;
    this.method = method;
  }

  public DexCode getCode() {
    return code;
  }

  public ProgramMethod getMethod() {
    return method;
  }
}
