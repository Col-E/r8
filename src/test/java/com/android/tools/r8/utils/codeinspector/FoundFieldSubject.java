// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.graph.AccessFlags;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.naming.MemberNaming;
import com.android.tools.r8.naming.MemberNaming.FieldSignature;
import com.android.tools.r8.naming.signature.GenericSignatureParser;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.Reference;
import java.util.List;

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
  public FoundFieldSubject asFoundFieldSubject() {
    return this;
  }

  @Override
  public boolean isPresent() {
    return true;
  }

  @Override
  public boolean isRenamed() {
    return clazz.getNaming() != null
        && !getFinalSignature().name.equals(getOriginalSignature().name);
  }

  public TypeSubject type() {
    return new TypeSubject(codeInspector, dexField.getReference().type);
  }

  @Override
  public FieldSignature getOriginalSignature() {
    FieldSignature signature = getFinalSignature();
    if (clazz.getNaming() == null) {
      return signature;
    }

    // First check if we have residual signature information.

    MemberNaming memberNaming = clazz.getNaming().lookup(signature);
    if (memberNaming != null) {
      return memberNaming.getOriginalSignature().asFieldSignature();
    }

    // Map the type to the original name. This is needed as the in the Proguard map the
    // names on the left side are the original names. E.g.
    //
    //   X -> a
    //     X field -> a
    //
    // whereas the final signature is for X.a is "a a"
    String obfuscatedType = signature.type;
    String originalType =
        codeInspector.mapType(codeInspector.obfuscatedToOriginalMapping, obfuscatedType);
    String fieldType = originalType != null ? originalType : obfuscatedType;
    FieldSignature lookupSignature = new FieldSignature(signature.name, fieldType);

    memberNaming = clazz.getNaming().lookup(lookupSignature);
    return memberNaming != null
        ? memberNaming.getOriginalSignature().asFieldSignature()
        : lookupSignature;
  }

  public DexField getOriginalDexField(DexItemFactory dexItemFactory) {
    FieldSignature fieldSignature = getOriginalSignature();
    return fieldSignature.toDexField(dexItemFactory, clazz.getOriginalDexType(dexItemFactory));
  }

  @Override
  public FieldSignature getFinalSignature() {
    return FieldSignature.fromDexField(dexField.getReference());
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
        getFinalSignatureAttribute(), GenericSignatureParser::parseFieldSignature);
  }

  @Override
  public String getFinalSignatureAttribute() {
    return dexField.getGenericSignature().toString();
  }

  @Override
  public List<FoundAnnotationSubject> annotations() {
    return FoundAnnotationSubject.listFromDex(dexField.annotations(), codeInspector);
  }

  @Override
  public AnnotationSubject annotation(String name) {
    DexAnnotation annotation = codeInspector.findAnnotation(dexField.annotations(), name);
    return annotation == null
        ? new AbsentAnnotationSubject()
        : new FoundAnnotationSubject(annotation, codeInspector);
  }

  @Override
  public String toString() {
    return dexField.toSourceString();
  }

  @Override
  public String getJvmFieldSignatureAsString() {
    return dexField.getReference().name + ":" + dexField.getReference().type.toDescriptorString();
  }

  @Override
  public FieldReference getOriginalReference() {
    DexField originalDexField = getOriginalDexField(codeInspector.getFactory());
    return Reference.field(
        Reference.classFromDescriptor(originalDexField.holder.toDescriptorString()),
        getOriginalName(),
        Reference.typeFromDescriptor(originalDexField.type.toDescriptorString()));
  }

  @Override
  public FieldReference getFinalReference() {
    return Reference.field(
        Reference.classFromDescriptor(getField().getHolderType().toDescriptorString()),
        getFinalName(),
        Reference.typeFromDescriptor(getField().getType().toDescriptorString()));
  }

  @Override
  public AccessFlags<?> getAccessFlags() {
    return getField().getAccessFlags();
  }
}
