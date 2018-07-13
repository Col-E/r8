// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.dexinspector;

import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.InnerClassAttribute;
import com.android.tools.r8.naming.ClassNamingForNameMapper;
import com.android.tools.r8.naming.MemberNaming;
import com.android.tools.r8.naming.MemberNaming.FieldSignature;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.naming.MemberNaming.Signature;
import com.android.tools.r8.naming.signature.GenericSignatureParser;
import com.android.tools.r8.utils.DescriptorUtils;
import java.util.List;
import java.util.function.Consumer;

public class FoundClassSubject extends ClassSubject {

  private DexInspector dexInspector;
  private final DexClass dexClass;
  final ClassNamingForNameMapper naming;

  FoundClassSubject(DexInspector dexInspector, DexClass dexClass, ClassNamingForNameMapper naming) {
    this.dexInspector = dexInspector;
    this.dexClass = dexClass;
    this.naming = naming;
  }

  @Override
  public boolean isPresent() {
    return true;
  }

  @Override
  public void forAllMethods(Consumer<FoundMethodSubject> inspection) {
    DexInspector.forAll(
        dexClass.directMethods(),
        (encoded, clazz) -> new FoundMethodSubject(dexInspector, encoded, clazz),
        this,
        inspection);
    DexInspector.forAll(
        dexClass.virtualMethods(),
        (encoded, clazz) -> new FoundMethodSubject(dexInspector, encoded, clazz),
        this,
        inspection);
  }

  @Override
  public MethodSubject method(String returnType, String name, List<String> parameters) {
    DexType[] parameterTypes = new DexType[parameters.size()];
    for (int i = 0; i < parameters.size(); i++) {
      parameterTypes[i] =
          dexInspector.toDexType(dexInspector.getObfuscatedTypeName(parameters.get(i)));
    }
    DexProto proto =
        dexInspector.dexItemFactory.createProto(
            dexInspector.toDexType(dexInspector.getObfuscatedTypeName(returnType)), parameterTypes);
    if (naming != null) {
      String[] parameterStrings = new String[parameterTypes.length];
      Signature signature =
          new MethodSignature(name, returnType, parameters.toArray(parameterStrings));
      MemberNaming methodNaming = naming.lookupByOriginalSignature(signature);
      if (methodNaming != null) {
        name = methodNaming.getRenamedName();
      }
    }
    DexMethod dexMethod =
        dexInspector.dexItemFactory.createMethod(
            dexClass.type, proto, dexInspector.dexItemFactory.createString(name));
    DexEncodedMethod encoded = findMethod(dexClass.directMethods(), dexMethod);
    if (encoded == null) {
      encoded = findMethod(dexClass.virtualMethods(), dexMethod);
    }
    return encoded == null
        ? new AbsentMethodSubject()
        : new FoundMethodSubject(dexInspector, encoded, this);
  }

  private DexEncodedMethod findMethod(DexEncodedMethod[] methods, DexMethod dexMethod) {
    for (DexEncodedMethod method : methods) {
      if (method.method.equals(dexMethod)) {
        return method;
      }
    }
    return null;
  }

  @Override
  public void forAllFields(Consumer<FoundFieldSubject> inspection) {
    DexInspector.forAll(
        dexClass.staticFields(),
        (dexField, clazz) -> new FoundFieldSubject(dexInspector, dexField, clazz),
        this,
        inspection);
    DexInspector.forAll(
        dexClass.instanceFields(),
        (dexField, clazz) -> new FoundFieldSubject(dexInspector, dexField, clazz),
        this,
        inspection);
  }

  @Override
  public FieldSubject field(String type, String name) {
    String obfuscatedType = dexInspector.getObfuscatedTypeName(type);
    MemberNaming fieldNaming = null;
    if (naming != null) {
      fieldNaming = naming.lookupByOriginalSignature(new FieldSignature(name, type));
    }
    String obfuscatedName = fieldNaming == null ? name : fieldNaming.getRenamedName();

    DexField field =
        dexInspector.dexItemFactory.createField(
            dexClass.type,
            dexInspector.toDexType(obfuscatedType),
            dexInspector.dexItemFactory.createString(obfuscatedName));
    DexEncodedField encoded = findField(dexClass.staticFields(), field);
    if (encoded == null) {
      encoded = findField(dexClass.instanceFields(), field);
    }
    return encoded == null
        ? new AbsentFieldSubject()
        : new FoundFieldSubject(dexInspector, encoded, this);
  }

  @Override
  public boolean isAbstract() {
    return dexClass.accessFlags.isAbstract();
  }

  @Override
  public boolean isAnnotation() {
    return dexClass.accessFlags.isAnnotation();
  }

  private DexEncodedField findField(DexEncodedField[] fields, DexField dexField) {
    for (DexEncodedField field : fields) {
      if (field.field.equals(dexField)) {
        return field;
      }
    }
    return null;
  }

  @Override
  public DexClass getDexClass() {
    return dexClass;
  }

  @Override
  public AnnotationSubject annotation(String name) {
    // Ensure we don't check for annotations represented as attributes.
    assert !name.endsWith("EnclosingClass")
        && !name.endsWith("EnclosingMethod")
        && !name.endsWith("InnerClass");
    DexAnnotation annotation = dexInspector.findAnnotation(name, dexClass.annotations);
    return annotation == null
        ? new AbsentAnnotationSubject()
        : new FoundAnnotationSubject(annotation);
  }

  @Override
  public String getOriginalName() {
    if (naming != null) {
      return naming.originalName;
    } else {
      return getFinalName();
    }
  }

  @Override
  public String getOriginalDescriptor() {
    if (naming != null) {
      return DescriptorUtils.javaTypeToDescriptor(naming.originalName);
    } else {
      return getFinalDescriptor();
    }
  }

  @Override
  public String getFinalName() {
    return DescriptorUtils.descriptorToJavaType(getFinalDescriptor());
  }

  @Override
  public String getFinalDescriptor() {
    return dexClass.type.descriptor.toString();
  }

  @Override
  public boolean isRenamed() {
    return naming != null && !getFinalDescriptor().equals(getOriginalDescriptor());
  }

  private InnerClassAttribute getInnerClassAttribute() {
    for (InnerClassAttribute innerClassAttribute : dexClass.getInnerClasses()) {
      if (dexClass.type == innerClassAttribute.getInner()) {
        return innerClassAttribute;
      }
    }
    return null;
  }

  @Override
  public boolean isLocalClass() {
    InnerClassAttribute innerClass = getInnerClassAttribute();
    return innerClass != null && innerClass.isNamed() && dexClass.getEnclosingMethod() != null;
  }

  @Override
  public boolean isMemberClass() {
    InnerClassAttribute innerClass = getInnerClassAttribute();
    return innerClass != null
        && innerClass.getOuter() != null
        && innerClass.isNamed()
        && dexClass.getEnclosingMethod() == null;
  }

  @Override
  public boolean isAnonymousClass() {
    InnerClassAttribute innerClass = getInnerClassAttribute();
    return innerClass != null && innerClass.isAnonymous() && dexClass.getEnclosingMethod() != null;
  }

  @Override
  public boolean isSynthesizedJavaLambdaClass() {
    return dexClass.type.getName().contains("$Lambda$");
  }

  @Override
  public String getOriginalSignatureAttribute() {
    return dexInspector.getOriginalSignatureAttribute(
        dexClass.annotations, GenericSignatureParser::parseClassSignature);
  }

  @Override
  public String getFinalSignatureAttribute() {
    return dexInspector.getFinalSignatureAttribute(dexClass.annotations);
  }

  @Override
  public String toString() {
    return dexClass.toSourceString();
  }
}
