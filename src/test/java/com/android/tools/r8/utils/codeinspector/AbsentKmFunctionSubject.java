// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.errors.Unreachable;
import java.util.List;
import kotlinx.metadata.KmTypeParameter;
import kotlinx.metadata.jvm.JvmMethodSignature;

public class AbsentKmFunctionSubject extends KmFunctionSubject {

  @Override
  public boolean isPresent() {
    return false;
  }

  @Override
  public boolean isRenamed() {
    throw new Unreachable("Cannot determine if an absent KmFunction is renamed");
  }

  @Override
  public boolean isSynthetic() {
    throw new Unreachable("Cannot determine if an absent KmFunction is synthetic");
  }

  @Override
  public boolean isExtension() {
    throw new Unreachable("Cannot determine if an absent KmFunction is extension");
  }

  @Override
  public JvmMethodSignature signature() {
    return null;
  }

  @Override
  public KmTypeSubject receiverParameterType() {
    return null;
  }

  @Override
  public List<KmValueParameterSubject> valueParameters() {
    return null;
  }

  @Override
  public KmTypeSubject returnType() {
    return null;
  }

  @Override
  public List<KmTypeParameter> getKmTypeParameters() {
    return null;
  }

  @Override
  public CodeInspector getCodeInspector() {
    return null;
  }
}
