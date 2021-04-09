// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.naming.mappinginformation.MappingInformation;
import com.android.tools.r8.naming.mappinginformation.ScopedMappingInformation.ScopeReference;
import com.android.tools.r8.position.Position;
import java.util.function.Consumer;

public interface ProguardMap {

  abstract class Builder {
    abstract ClassNaming.Builder classNamingBuilder(
        String renamedName, String originalName, Position position);

    abstract ProguardMap build();

    abstract void addMappingInformation(
        ScopeReference scope,
        MappingInformation MappingInformation,
        Consumer<MappingInformation> onProhibitedAddition);
  }

  boolean hasMapping(DexType type);
  ClassNaming getClassNaming(DexType type);
}
