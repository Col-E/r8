// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.codeinspector;

import java.util.List;
import kotlinx.metadata.jvm.JvmMethodSignature;

public class AbsentKmFunctionSubject extends KmFunctionSubject {

  @Override
  public boolean isPresent() {
    return false;
  }

  @Override
  public boolean isRenamed() {
    return false;
  }

  @Override
  public boolean isSynthetic() {
    return false;
  }

  @Override
  public boolean isExtension() {
    return false;
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
}
