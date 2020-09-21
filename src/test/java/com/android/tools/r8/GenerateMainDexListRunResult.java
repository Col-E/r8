// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.ListUtils;
import java.util.List;

public class GenerateMainDexListRunResult
    extends SingleTestRunResult<GenerateMainDexListRunResult> {

  List<String> mainDexList;

  public GenerateMainDexListRunResult(List<String> mainDexList) {
    super(null, null, null);
    this.mainDexList = mainDexList;
  }

  public List<ClassReference> getMainDexList() {
    return ListUtils.map(
        mainDexList,
        entry -> {
          assert entry.endsWith(".class");
          String binaryName = entry.substring(0, entry.length() - ".class".length());
          return Reference.classFromBinaryName(binaryName);
        });
  }

  @Override
  protected GenerateMainDexListRunResult self() {
    return this;
  }
}
