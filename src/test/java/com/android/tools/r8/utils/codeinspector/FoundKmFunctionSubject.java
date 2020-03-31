// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.utils.codeinspector.FoundKmDeclarationContainerSubject.KmFunctionProcessor;
import java.util.List;
import java.util.stream.Collectors;
import kotlinx.metadata.KmFunction;
import kotlinx.metadata.KmType;
import kotlinx.metadata.KmTypeParameter;
import kotlinx.metadata.jvm.JvmMethodSignature;

public class FoundKmFunctionSubject extends KmFunctionSubject {

  private final CodeInspector codeInspector;
  private final KmFunction kmFunction;
  private final JvmMethodSignature signature;

  FoundKmFunctionSubject(CodeInspector codeInspector, KmFunction kmFunction) {
    this.codeInspector = codeInspector;
    this.kmFunction = kmFunction;
    KmFunctionProcessor kmFunctionProcessor = new KmFunctionProcessor(kmFunction);
    this.signature = kmFunctionProcessor.signature;
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

  @Override
  public boolean isExtension() {
    return isExtension(kmFunction);
  }

  @Override
  public JvmMethodSignature signature() {
    return signature;
  }

  @Override
  public KmTypeSubject receiverParameterType() {
    KmType kmType = kmFunction.getReceiverParameterType();
    assert !isExtension() || kmType != null;
    return kmType == null ? null : new KmTypeSubject(codeInspector, kmType);
  }

  @Override
  public List<KmValueParameterSubject> valueParameters() {
    return kmFunction.getValueParameters().stream()
        .map(kmValueParameter -> new KmValueParameterSubject(codeInspector, kmValueParameter))
        .collect(Collectors.toList());
  }

  @Override
  public KmTypeSubject returnType() {
    return new KmTypeSubject(codeInspector, kmFunction.getReturnType());
  }

  @Override
  public List<KmTypeParameter> getKmTypeParameters() {
    return kmFunction.getTypeParameters();
  }

  @Override
  public CodeInspector getCodeInspector() {
    return codeInspector;
  }
}
