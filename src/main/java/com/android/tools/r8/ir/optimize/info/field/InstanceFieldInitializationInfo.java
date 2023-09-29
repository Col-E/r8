// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.info.field;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.proto.ArgumentInfoCollection;
import com.android.tools.r8.ir.analysis.value.SingleValue;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

/**
 * Information about the way a constructor initializes an instance field on the newly created
 * instance.
 *
 * <p>For example, this can be used to represent that a constructor always initializes a particular
 * instance field with a constant, or with an argument from the constructor's argument list.
 */
public interface InstanceFieldInitializationInfo {

  default boolean isArgumentInitializationInfo() {
    return false;
  }

  default InstanceFieldArgumentInitializationInfo asArgumentInitializationInfo() {
    return null;
  }

  default boolean isTypeInitializationInfo() {
    return false;
  }

  default InstanceFieldTypeInitializationInfo asTypeInitializationInfo() {
    return null;
  }

  default boolean isSingleValue() {
    return false;
  }

  default SingleValue asSingleValue() {
    return null;
  }

  default boolean isUnknown() {
    return false;
  }

  InstanceFieldInitializationInfo fixupAfterParametersChanged(
      ArgumentInfoCollection argumentInfoCollection);

  InstanceFieldInitializationInfo rewrittenWithLens(
      AppView<AppInfoWithLiveness> appView, DexType newType, GraphLens lens, GraphLens codeLens);
}
