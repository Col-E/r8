// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.androidapi;

import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.utils.AndroidApiLevel;

/** This interface is used to add additional known references to the api database. */
class AndroidApiForHashingReference {

  private final DexReference reference;

  private final AndroidApiLevel apiLevel;

  private AndroidApiForHashingReference(DexReference reference, AndroidApiLevel apiLevel) {
    this.reference = reference;
    this.apiLevel = apiLevel;
  }

  static AndroidApiForHashingReference create(DexReference reference, AndroidApiLevel apiLevel) {
    return new AndroidApiForHashingReference(reference, apiLevel);
  }

  DexReference getReference() {
    return reference;
  }

  AndroidApiLevel getApiLevel() {
    return apiLevel;
  }
}
