// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import java.util.LinkedHashMap;

public class LinkedProgramMethodSet extends ProgramMethodSet {

  LinkedProgramMethodSet() {
    super(LinkedProgramMethodSet::createBacking, createBacking());
  }

  LinkedProgramMethodSet(int capacity) {
    super(LinkedProgramMethodSet::createBacking, createBacking(capacity));
  }

  private static <K, V> LinkedHashMap<K, V> createBacking() {
    return new LinkedHashMap<>();
  }

  private static <K, V> LinkedHashMap<K, V> createBacking(int capacity) {
    return new LinkedHashMap<>(capacity);
  }
}
