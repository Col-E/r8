// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.naming.MemberNaming.Signature;

public abstract class MemberSubject extends Subject {

  public abstract boolean isPublic();

  public abstract boolean isProtected();

  public abstract boolean isPrivate();

  public abstract boolean isPackagePrivate();

  public abstract boolean isStatic();

  public abstract boolean isFinal();

  public abstract Signature getOriginalSignature();

  public abstract Signature getFinalSignature();

  public String getOriginalName() {
    return getOriginalName(true);
  }

  public String getOriginalName(boolean qualified) {
    Signature originalSignature = getOriginalSignature();
    if (originalSignature != null) {
      String name = originalSignature.name;
      if (!qualified) {
        int index = name.lastIndexOf(".");
        if (index >= 0) {
          return name.substring(index + 1);
        }
      }
      return name;
    }
    return null;
  }

  public String getFinalName() {
    Signature finalSignature = getFinalSignature();
    return finalSignature == null ? null : finalSignature.name;
  }

  public FieldSubject asFieldSubject() {
    return null;
  }

  public boolean isFieldSubject() {
    return false;
  }

  public MethodSubject asMethodSubject() {
    return null;
  }

  public boolean isMethodSubject() {
    return false;
  }
}
