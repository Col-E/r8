// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

public interface DexDefinitionSupplier {

  DexClass definitionFor(DexType type);

  DexProgramClass definitionForProgramType(DexType type);

  default <D extends DexEncodedMember<D, R>, R extends DexMember<D, R>>
      DexClass definitionForHolder(DexEncodedMember<D, R> member) {
    return definitionForHolder(member.toReference());
  }

  default <D extends DexEncodedMember<D, R>, R extends DexMember<D, R>>
      DexClass definitionForHolder(DexMember<D, R> member) {
    return definitionFor(member.holder);
  }

  DexItemFactory dexItemFactory();
}
