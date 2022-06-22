// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.code.frame;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.ReferenceTypeElement;

public abstract class InitializedNonNullReferenceFrameType extends BaseFrameType
    implements InitializedReferenceFrameType {

  @Override
  public final boolean isInitialized() {
    return true;
  }

  @Override
  public final boolean isInitializedReferenceType() {
    return true;
  }

  @Override
  public final InitializedNonNullReferenceFrameType asInitializedReferenceType() {
    return this;
  }

  @Override
  public final boolean isInitializedNonNullReferenceType() {
    return true;
  }

  @Override
  public final InitializedNonNullReferenceFrameType asInitializedNonNullReferenceType() {
    return this;
  }

  @Override
  public final boolean isObject() {
    return true;
  }

  @Override
  public final boolean isPrecise() {
    return true;
  }

  @Override
  public final PreciseFrameType asPrecise() {
    return this;
  }

  @Override
  public final SingleFrameType asSingle() {
    return this;
  }

  public abstract ReferenceTypeElement getInitializedTypeWithInterfaces(
      AppView<? extends AppInfoWithClassHierarchy> appView);

  @Override
  public final DexType getObjectType(DexItemFactory dexItemFactory, DexType context) {
    return getInitializedType(dexItemFactory);
  }
}
