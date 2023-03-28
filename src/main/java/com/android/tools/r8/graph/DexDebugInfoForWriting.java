// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.dex.DebugBytecodeWriter;
import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.dex.MixedSectionCollection;
import com.android.tools.r8.graph.lens.GraphLens;

/** Interface to guarantee that the info only contains writable events. */
public interface DexDebugInfoForWriting {

  void collectMixedSectionItems(MixedSectionCollection collection);

  void collectIndexedItems(AppView<?> appView, IndexedItemCollection indexedItems);

  int estimatedWriteSize();

  void write(DebugBytecodeWriter writer, ObjectToOffsetMapping mapping, GraphLens graphLens);
}
