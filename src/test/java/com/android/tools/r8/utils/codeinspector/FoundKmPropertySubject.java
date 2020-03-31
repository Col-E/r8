// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.utils.codeinspector.FoundKmDeclarationContainerSubject.KmPropertyProcessor;
import kotlinx.metadata.KmProperty;
import kotlinx.metadata.jvm.JvmFieldSignature;
import kotlinx.metadata.jvm.JvmMethodSignature;

public class FoundKmPropertySubject extends KmPropertySubject {

  private final CodeInspector codeInspector;
  private final KmProperty kmProperty;
  private final JvmFieldSignature fieldSignature;
  private final JvmMethodSignature getterSignature;
  private final JvmMethodSignature setterSignature;

  FoundKmPropertySubject(CodeInspector codeInspector, KmProperty kmProperty) {
    this.codeInspector = codeInspector;
    this.kmProperty = kmProperty;
    KmPropertyProcessor kmPropertyProcessor = new KmPropertyProcessor(kmProperty);
    this.fieldSignature = kmPropertyProcessor.fieldSignature;
    this.getterSignature = kmPropertyProcessor.getterSignature;
    this.setterSignature = kmPropertyProcessor.setterSignature;
  }

  @Override
  public boolean isPresent() {
    return true;
  }

  @Override
  public boolean isRenamed() {
    // TODO(b/151194869): How to determine it is renamed?
    //   backing field renamed? If no backing field exists, then examine getter/setter?
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
    return isExtension(kmProperty);
  }

  @Override
  public String name() {
    return kmProperty.getName();
  }

  @Override
  public JvmFieldSignature fieldSignature() {
    return fieldSignature;
  }

  @Override
  public JvmMethodSignature getterSignature() {
    return getterSignature;
  }

  @Override
  public JvmMethodSignature setterSignature() {
    return setterSignature;
  }

  @Override
  public KmTypeSubject returnType() {
    return new KmTypeSubject(codeInspector, kmProperty.getReturnType());
  }
}
