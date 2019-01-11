// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.naming.MemberNaming.Signature;

public class AbsentFieldSubject extends FieldSubject {

  @Override
  public boolean isPublic() {
    throw new Unreachable("Cannot determine if an absent field is public");
  }

  @Override
  public boolean isProtected() {
    throw new Unreachable("Cannot determine if an absent field is protected");
  }

  @Override
  public boolean isPrivate() {
    throw new Unreachable("Cannot determine if an absent field is private");
  }

  @Override
  public boolean isPackagePrivate() {
    throw new Unreachable("Cannot determine if an absent field is package-private");
  }

  @Override
  public boolean isStatic() {
    throw new Unreachable("Cannot determine if an absent field is static");
  }

  @Override
  public boolean isFinal() {
    throw new Unreachable("Cannot determine if an absent field is final");
  }

  @Override
  public boolean isPresent() {
    return false;
  }

  @Override
  public boolean isRenamed() {
    throw new Unreachable("Cannot determine if an absent field has been renamed");
  }

  @Override
  public boolean isSynthetic() {
    throw new Unreachable("Cannot determine if an absent field is synthetic");
  }

  @Override
  public Signature getOriginalSignature() {
    return null;
  }

  @Override
  public Signature getFinalSignature() {
    return null;
  }

  @Override
  public boolean hasExplicitStaticValue() {
    throw new Unreachable("Cannot determine if an absent field has en explicit static value");
  }

  @Override
  public DexValue getStaticValue() {
    return null;
  }

  @Override
  public DexEncodedField getField() {
    return null;
  }

  @Override
  public String getOriginalSignatureAttribute() {
    return null;
  }

  @Override
  public String getFinalSignatureAttribute() {
    return null;
  }
}
