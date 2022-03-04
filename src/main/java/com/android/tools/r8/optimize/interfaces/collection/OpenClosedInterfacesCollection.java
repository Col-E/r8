// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.interfaces.collection;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.PrunedItems;

/**
 * Knowledge about open/closed interfaces.
 *
 * <p>An interface type is "open" if it may store an instance that is not a subtype of the given
 * interface.
 *
 * <p>An interface type is "closed" if it is guaranteed to store instances that are subtypes of the
 * given interface.
 */
public abstract class OpenClosedInterfacesCollection {

  public static DefaultOpenClosedInterfacesCollection getDefault() {
    return DefaultOpenClosedInterfacesCollection.getInstance();
  }

  public abstract boolean isDefinitelyClosed(DexClass clazz);

  public final boolean isMaybeOpen(DexClass clazz) {
    return !isDefinitelyClosed(clazz);
  }

  public abstract OpenClosedInterfacesCollection rewrittenWithLens(GraphLens graphLens);

  public abstract OpenClosedInterfacesCollection withoutPrunedItems(PrunedItems prunedItems);
}
