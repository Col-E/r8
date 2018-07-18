// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.naming.MemberNaming;
import com.android.tools.r8.naming.MemberNaming.FieldSignature;
import com.android.tools.r8.naming.signature.GenericSignatureParser;

public class FoundFieldSubject extends FieldSubject {

  private final CodeInspector codeInspector;
  private final FoundClassSubject clazz;
  private final DexEncodedField dexField;

  public FoundFieldSubject(
      CodeInspector codeInspector, DexEncodedField dexField, FoundClassSubject clazz) {
    this.codeInspector = codeInspector;
    this.clazz = clazz;
    this.dexField = dexField;
  }

  @Override
  public boolean isPublic() {
    return dexField.accessFlags.isPublic();
  }

  @Override
  public boolean isStatic() {
    return dexField.accessFlags.isStatic();
  }

  @Override
  public boolean isFinal() {
    return dexField.accessFlags.isFinal();
  }

  @Override
  public boolean isPresent() {
    return true;
  }

  @Override
  public boolean isRenamed() {
    return clazz.naming != null && !getFinalSignature().name.equals(getOriginalSignature().name);
  }

  public TypeSubject type() {
    return new TypeSubject(codeInspector, dexField.field.type);
  }

  @Override
  public FieldSignature getOriginalSignature() {
    FieldSignature signature = getFinalSignature();
    if (clazz.naming == null) {
      return signature;
    }

    // Map the type to the original name. This is needed as the in the Proguard map the
    // names on the left side are the original names. E.g.
    //
    //   X -> a
    //     X field -> a
    //
    // whereas the final signature is for X.a is "a a"
    String obfuscatedType = signature.type;
    String originalType = codeInspector.originalToObfuscatedMapping.inverse().get(obfuscatedType);
    String fieldType = originalType != null ? originalType : obfuscatedType;

    FieldSignature lookupSignature = new FieldSignature(signature.name, fieldType);

    MemberNaming memberNaming = clazz.naming.lookup(lookupSignature);
    return memberNaming != null ? (FieldSignature) memberNaming.getOriginalSignature() : signature;
  }

  @Override
  public FieldSignature getFinalSignature() {
    return FieldSignature.fromDexField(dexField.field);
  }

  @Override
  public boolean hasExplicitStaticValue() {
    return isStatic() && dexField.hasExplicitStaticValue();
  }

  @Override
  public DexValue getStaticValue() {
    return dexField.getStaticValue();
  }

  @Override
  public DexEncodedField getField() {
    return dexField;
  }

  @Override
  public String getOriginalSignatureAttribute() {
    return codeInspector.getOriginalSignatureAttribute(
        dexField.annotations, GenericSignatureParser::parseFieldSignature);
  }

  @Override
  public String getFinalSignatureAttribute() {
    return codeInspector.getFinalSignatureAttribute(dexField.annotations);
  }

  @Override
  public String toString() {
    return dexField.toSourceString();
  }
}
