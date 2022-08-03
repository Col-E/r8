// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.memberparser;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import java.util.function.Function;

public class MachineFieldParser extends HumanFieldParser {
  private final Function<String, DexType> typeParser;

  public MachineFieldParser(DexItemFactory factory, Function<String, DexType> typeParser) {
    super(factory);
    this.typeParser = typeParser;
  }

  @Override
  DexType stringTypeToDexType(String stringType) {
    return typeParser.apply(stringType);
  }
}
