// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ProgramMethod;
import java.util.LinkedHashMap;
import java.util.Map;

public class LinkedProgramMethodSet extends ProgramMethodSet {

  LinkedProgramMethodSet() {
    super();
  }

  LinkedProgramMethodSet(int capacity) {
    super(capacity);
  }

  @Override
  Map<DexMethod, ProgramMethod> createBacking() {
    return new LinkedHashMap<>();
  }

  @Override
  Map<DexMethod, ProgramMethod> createBacking(int capacity) {
    return new LinkedHashMap<>(capacity);
  }
}
