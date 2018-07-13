// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.dexinspector;

import com.android.tools.r8.naming.MemberNaming.Signature;

public abstract class MemberSubject extends Subject {

  public abstract boolean isPublic();

  public abstract boolean isStatic();

  public abstract boolean isFinal();

  public abstract Signature getOriginalSignature();

  public abstract Signature getFinalSignature();

  public String getOriginalName() {
    Signature originalSignature = getOriginalSignature();
    return originalSignature == null ? null : originalSignature.name;
  }

  public String getFinalName() {
    Signature finalSignature = getFinalSignature();
    return finalSignature == null ? null : finalSignature.name;
  }
}
