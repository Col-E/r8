// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import com.android.tools.r8.utils.TriConsumer;
import java.util.Set;

/**
 * Interface that accommodates many-to-one mappings.
 *
 * <p>This interface implicitly adds a "representative" for each many-to-one mapping by inheriting
 * from {@link BidirectionalManyToManyRepresentativeMap}.
 */
public interface BidirectionalManyToOneRepresentativeMap<K, V>
    extends BidirectionalManyToOneMap<K, V>, BidirectionalManyToManyRepresentativeMap<K, V> {

  void forEachManyToOneMapping(TriConsumer<? super Set<K>, V, K> consumer);
}
