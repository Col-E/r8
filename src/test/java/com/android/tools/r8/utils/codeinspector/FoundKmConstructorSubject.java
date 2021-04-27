// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import java.util.List;
import java.util.stream.Collectors;
import kotlinx.metadata.KmConstructor;
import kotlinx.metadata.jvm.JvmExtensionsKt;
import kotlinx.metadata.jvm.JvmMethodSignature;

public class FoundKmConstructorSubject extends KmConstructorSubject {

  private final CodeInspector codeInspector;
  private final KmConstructor kmConstructor;

  FoundKmConstructorSubject(CodeInspector codeInspector, KmConstructor kmConstructor) {
    this.codeInspector = codeInspector;
    this.kmConstructor = kmConstructor;
  }

  @Override
  public JvmMethodSignature signature() {
    JvmExtensionsKt.getSignature(this.kmConstructor);
    return null;
  }

  @Override
  public List<KmValueParameterSubject> valueParameters() {
    return kmConstructor.getValueParameters().stream()
        .map(kmValueParameter -> new KmValueParameterSubject(codeInspector, kmValueParameter))
        .collect(Collectors.toList());
  }

  @Override
  public boolean isPresent() {
    return true;
  }

  @Override
  public boolean isRenamed() {
    // TODO(b/151194869): need to know the corresponding DexEncodedMethod.
    return false;
  }

  @Override
  public boolean isSynthetic() {
    // TODO(b/151194785): This should return `true` conditionally if we start synthesizing @Metadata
    //   from scratch.
    return false;
  }
}
