// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import java.util.List;

public class GenerateMainDexListRunResult extends TestRunResult<GenerateMainDexListRunResult> {

  List<String> mainDexList;

  public GenerateMainDexListRunResult(List<String> mainDexList) {
    super(null, null);
    this.mainDexList = mainDexList;
  }

  @Override
  protected GenerateMainDexListRunResult self() {
    return this;
  }
}
