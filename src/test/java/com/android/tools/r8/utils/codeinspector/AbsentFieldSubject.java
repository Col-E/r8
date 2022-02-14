// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AccessFlags;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.naming.MemberNaming.Signature;
import com.android.tools.r8.references.FieldReference;
import java.util.List;

public class AbsentFieldSubject extends FieldSubject {

  @Override
  public boolean isPresent() {
    return false;
  }

  @Override
  public boolean isRenamed() {
    throw new Unreachable("Cannot determine if an absent field has been renamed");
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

  @Override
  public List<FoundAnnotationSubject> annotations() {
    throw new Unreachable("Cannot determine if an absent field has annotations");
  }

  @Override
  public AnnotationSubject annotation(String name) {
    return new AbsentAnnotationSubject();
  }

  @Override
  public String getJvmFieldSignatureAsString() {
    return null;
  }

  @Override
  public FieldReference getOriginalReference() {
    return null;
  }

  @Override
  public FieldReference getFinalReference() {
    return null;
  }

  @Override
  public AccessFlags<?> getAccessFlags() {
    throw new Unreachable("Absent field has no access flags");
  }
}
