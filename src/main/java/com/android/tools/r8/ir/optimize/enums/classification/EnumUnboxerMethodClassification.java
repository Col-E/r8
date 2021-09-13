// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.enums.classification;

import com.android.tools.r8.graph.RewrittenPrototypeDescription.ArgumentInfoCollection;

public abstract class EnumUnboxerMethodClassification {

  public static UnknownEnumUnboxerMethodClassification unknown() {
    return UnknownEnumUnboxerMethodClassification.getInstance();
  }

  public abstract EnumUnboxerMethodClassification fixupAfterParametersChanged(
      ArgumentInfoCollection removedParameters);

  public boolean isCheckNotNullClassification() {
    return false;
  }

  public CheckNotNullEnumUnboxerMethodClassification asCheckNotNullClassification() {
    return null;
  }

  public boolean isUnknownClassification() {
    return false;
  }
}
