// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.codeinspector;

import static kotlinx.metadata.Flag.ValueParameter.DECLARES_DEFAULT_VALUE;

import com.android.tools.r8.errors.Unreachable;
import kotlinx.metadata.KmValueParameter;

public class KmValueParameterSubject extends Subject {
  private final CodeInspector codeInspector;
  private final KmValueParameter kmValueParameter;

  KmValueParameterSubject(CodeInspector codeInspector, KmValueParameter kmValueParameter) {
    this.codeInspector = codeInspector;
    this.kmValueParameter = kmValueParameter;
  }

  public KmTypeSubject type() {
    return new KmTypeSubject(codeInspector, kmValueParameter.getType());
  }

  public KmTypeSubject varargElementType() {
    if (!isVararg()) {
      return null;
    }
    return new KmTypeSubject(codeInspector, kmValueParameter.getVarargElementType());
  }

  public boolean isVararg() {
    return kmValueParameter.getVarargElementType() != null;
  }

  public boolean declaresDefaultValue() {
    return DECLARES_DEFAULT_VALUE.invoke(kmValueParameter.getFlags());
  }

  @Override
  public boolean isPresent() {
    return true;
  }

  @Override
  public boolean isRenamed() {
    throw new Unreachable("Cannot determine if a parameter is renamed");
  }

  @Override
  public boolean isSynthetic() {
    throw new Unreachable("Cannot determine if a parameter is synthetic");
  }
}
