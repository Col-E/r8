// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.errors.Unreachable;
import kotlinx.metadata.jvm.JvmFieldSignature;
import kotlinx.metadata.jvm.JvmMethodSignature;

public class AbsentKmPropertySubject extends KmPropertySubject {

  @Override
  public boolean isPresent() {
    return false;
  }

  @Override
  public boolean isRenamed() {
    throw new Unreachable("Cannot determine if an absent KmProperty is renamed");
  }

  @Override
  public boolean isSynthetic() {
    throw new Unreachable("Cannot determine if an absent KmProperty is synthetic");
  }

  @Override
  public boolean isExtension() {
    throw new Unreachable("Cannot determine if an absent KmProperty is extension");
  }

  @Override
  public String name() {
    return null;
  }

  @Override
  public JvmFieldSignature fieldSignature() {
    return null;
  }

  @Override
  public JvmMethodSignature getterSignature() {
    return null;
  }

  @Override
  public JvmMethodSignature setterSignature() {
    return null;
  }

  @Override
  public KmTypeSubject returnType() {
    return null;
  }
}
