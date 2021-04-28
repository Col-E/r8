// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.tracereferences.internal;

import com.android.tools.r8.tracereferences.TraceReferencesConsumer.AccessFlags;

public class AccessFlagsImpl<T extends com.android.tools.r8.graph.AccessFlags<T>>
    implements AccessFlags {
  T accessFlags;

  AccessFlagsImpl(T accessFlags) {
    this.accessFlags = accessFlags;
  }

  @Override
  public boolean isStatic() {
    return accessFlags.isStatic();
  }

  @Override
  public boolean isPublic() {
    return accessFlags.isPublic();
  }

  @Override
  public boolean isProtected() {
    return accessFlags.isProtected();
  }

  @Override
  public boolean isPrivate() {
    return accessFlags.isPrivate();
  }
}
