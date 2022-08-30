// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.naming.MemberNaming.Signature;
import com.android.tools.r8.references.MethodReference;
import java.util.List;

public class AbsentMethodSubject extends MethodSubject {

  @Override
  public IRCode buildIR() {
    throw new Unreachable("Cannot build IR for an absent method");
  }

  @Override
  public IRCode buildIR(AppView<?> appView) {
    throw new Unreachable("Cannot build IR for an absent method");
  }

  @Override
  public boolean isPresent() {
    return false;
  }

  @Override
  public boolean isRenamed() {
    throw new Unreachable("Cannot determine if an absent method has been renamed");
  }

  @Override
  public boolean isAbstract() {
    throw new Unreachable("Cannot determine if an absent method is abstract");
  }

  @Override
  public boolean isBridge() {
    throw new Unreachable("Cannot determine if an absent method is a bridge");
  }

  @Override
  public boolean isSynchronized() {
    throw new Unreachable("Cannot determine if an absent method is synchronized");
  }

  @Override
  public boolean isInstanceInitializer() {
    throw new Unreachable("Cannot determine if an absent method is an instance initializer");
  }

  @Override
  public boolean isClassInitializer() {
    throw new Unreachable("Cannot determine if an absent method is a class initializer");
  }

  @Override
  public boolean isVirtual() {
    throw new Unreachable("Cannot determine if an absent method is virtual");
  }

  @Override
  public boolean isNative() {
    throw new Unreachable("Cannot determine if an absent method is native");
  }

  @Override
  public MethodAccessFlags getAccessFlags() {
    throw new Unreachable("Cannot get the access flags for an absent method");
  }

  @Override
  public DexEncodedMethod getMethod() {
    return null;
  }

  @Override
  public MethodReference getFinalReference() {
    throw new Unreachable("Cannot get the final reference for an absent method");
  }

  @Override
  public TypeSubject getParameter(int index) {
    throw new Unreachable("Cannot get the parameter for an absent method");
  }

  @Override
  public List<TypeSubject> getParameters() {
    throw new Unreachable("Cannot get the parameters for an absent method");
  }

  @Override
  public List<List<FoundAnnotationSubject>> getParameterAnnotations() {
    throw new Unreachable("Cannot get the parameter annotations for an absent method");
  }

  @Override
  public List<FoundAnnotationSubject> getParameterAnnotations(int index) {
    throw new Unreachable("Cannot get the parameter annotations for an absent method");
  }

  @Override
  public ProgramMethod getProgramMethod() {
    return null;
  }

  @Override
  public MethodSignature getOriginalSignature() {
    return null;
  }

  @Override
  public Signature getFinalSignature() {
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

  @Override
  public LineNumberTable getLineNumberTable() {
    return null;
  }

  @Override
  public LocalVariableTable getLocalVariableTable() {
    return null;
  }

  @Override
  public boolean hasLocalVariableTable() {
    throw new Unreachable("Cannot determine if an absent method has a local variable table");
  }

  @Override
  public List<FoundAnnotationSubject> annotations() {
    throw new Unreachable("Cannot determine if an absent method has annotations");
  }

  @Override
  public AnnotationSubject annotation(String name) {
    return new AbsentAnnotationSubject();
  }

  @Override
  public String getJvmMethodSignatureAsString() {
    return null;
  }

  @Override
  public MethodSubject toMethodOnCompanionClass() {
    throw new Unreachable("Cannot determine companion class method");
  }
}
