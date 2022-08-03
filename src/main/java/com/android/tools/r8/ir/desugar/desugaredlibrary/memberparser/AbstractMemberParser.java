// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.memberparser;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.collect.ImmutableMap;
import java.util.Map;

public abstract class AbstractMemberParser {

  static final String SEPARATORS = "\\s+|,\\s+|#|\\(|\\)";

  static final Map<String, Integer> MODIFIERS =
      ImmutableMap.<String, Integer>builder()
          .put("public", Constants.ACC_PUBLIC)
          .put("private", Constants.ACC_PRIVATE)
          .put("protected", Constants.ACC_PROTECTED)
          .put("final", Constants.ACC_FINAL)
          .put("abstract", Constants.ACC_ABSTRACT)
          .put("static", Constants.ACC_STATIC)
          .build();

  final DexItemFactory factory;

  protected AbstractMemberParser(DexItemFactory factory) {
    this.factory = factory;
  }

  DexType stringTypeToDexType(String stringType) {
    return factory.createType(DescriptorUtils.javaTypeToDescriptor(stringType));
  }

  int parseModifiers(String[] split) {
    int index = 0;
    while (MODIFIERS.containsKey(split[index])) {
      modifier(MODIFIERS.get(split[index]));
      index++;
    }
    return index;
  }

  protected abstract void modifier(int access);
}
