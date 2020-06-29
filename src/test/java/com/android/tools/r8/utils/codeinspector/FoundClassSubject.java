// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import static com.android.tools.r8.KotlinTestBase.METADATA_TYPE;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.kotlin.KotlinClassMetadataReader;
import com.android.tools.r8.naming.ClassNamingForNameMapper;
import com.android.tools.r8.naming.MemberNaming;
import com.android.tools.r8.naming.MemberNaming.FieldSignature;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.naming.MemberNaming.Signature;
import com.android.tools.r8.naming.signature.GenericSignatureParser;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringUtils;
import java.util.List;
import java.util.function.Consumer;
import kotlinx.metadata.jvm.KotlinClassMetadata;

public class FoundClassSubject extends ClassSubject {

  private final DexClass dexClass;
  final ClassNamingForNameMapper naming;

  FoundClassSubject(
      CodeInspector codeInspector,
      DexClass dexClass,
      ClassNamingForNameMapper naming,
      ClassReference reference) {
    super(codeInspector, reference);
    this.dexClass = dexClass;
    this.naming = naming;
  }

  @Override
  public boolean isPresent() {
    return true;
  }

  @Override
  public boolean isSynthetic() {
    return dexClass.accessFlags.isSynthetic();
  }

  @Override
  public void forAllMethods(Consumer<FoundMethodSubject> inspection) {
    CodeInspector.forAll(
        dexClass.directMethods(),
        (encoded, clazz) -> new FoundMethodSubject(codeInspector, encoded, clazz),
        this,
        inspection);
    forAllVirtualMethods(inspection);
  }

  @Override
  public void forAllVirtualMethods(Consumer<FoundMethodSubject> inspection) {
    CodeInspector.forAll(
        dexClass.virtualMethods(),
        (encoded, clazz) -> new FoundMethodSubject(codeInspector, encoded, clazz),
        this,
        inspection);
  }

  @Override
  public MethodSubject method(String returnType, String name, List<String> parameters) {
    DexType[] parameterTypes = new DexType[parameters.size()];
    for (int i = 0; i < parameters.size(); i++) {
      parameterTypes[i] =
          codeInspector.toDexType(codeInspector.getObfuscatedTypeName(parameters.get(i)));
    }
    DexProto proto =
        codeInspector.dexItemFactory.createProto(
            codeInspector.toDexType(codeInspector.getObfuscatedTypeName(returnType)), parameterTypes);
    if (naming != null) {
      Signature signature =
          new MethodSignature(name, returnType, parameters.toArray(StringUtils.EMPTY_ARRAY));
      MemberNaming methodNaming = naming.lookupByOriginalSignature(signature);
      if (methodNaming != null) {
        name = methodNaming.getRenamedName();
      }
    }
    DexMethod dexMethod =
        codeInspector.dexItemFactory.createMethod(
            dexClass.type, proto, codeInspector.dexItemFactory.createString(name));
    DexEncodedMethod encoded = findMethod(dexClass.directMethods(), dexMethod);
    if (encoded == null) {
      encoded = findMethod(dexClass.virtualMethods(), dexMethod);
    }
    return encoded == null
        ? new AbsentMethodSubject()
        : new FoundMethodSubject(codeInspector, encoded, this);
  }

  private DexEncodedMethod findMethod(Iterable<DexEncodedMethod> methods, DexMethod dexMethod) {
    for (DexEncodedMethod method : methods) {
      if (method.method.equals(dexMethod)) {
        return method;
      }
    }
    return null;
  }

  @Override
  public MethodSubject uniqueMethodWithName(String name) {
    MethodSubject methodSubject = null;
    for (FoundMethodSubject candidate : allMethods()) {
      if (candidate.getOriginalName(false).equals(name)) {
        assert methodSubject == null;
        methodSubject = candidate;
      }
    }
    return methodSubject != null ? methodSubject : new AbsentMethodSubject();
  }

  @Override
  public void forAllFields(Consumer<FoundFieldSubject> inspection) {
    forAllInstanceFields(inspection);
    forAllStaticFields(inspection);
  }

  @Override
  public void forAllInstanceFields(Consumer<FoundFieldSubject> inspection) {
    CodeInspector.forAll(
        dexClass.instanceFields(),
        (dexField, clazz) -> new FoundFieldSubject(codeInspector, dexField, clazz),
        this,
        inspection);
  }

  @Override
  public void forAllStaticFields(Consumer<FoundFieldSubject> inspection) {
    CodeInspector.forAll(
        dexClass.staticFields(),
        (dexField, clazz) -> new FoundFieldSubject(codeInspector, dexField, clazz),
        this,
        inspection);
  }

  @Override
  public FieldSubject field(String type, String name) {
    String obfuscatedType = codeInspector.getObfuscatedTypeName(type);
    MemberNaming fieldNaming = null;
    if (naming != null) {
      fieldNaming = naming.lookupByOriginalSignature(new FieldSignature(name, type));
    }
    String obfuscatedName = fieldNaming == null ? name : fieldNaming.getRenamedName();

    DexField field =
        codeInspector.dexItemFactory.createField(
            dexClass.type,
            codeInspector.toDexType(obfuscatedType),
            codeInspector.dexItemFactory.createString(obfuscatedName));
    DexEncodedField encoded = findField(dexClass.staticFields(), field);
    if (encoded == null) {
      encoded = findField(dexClass.instanceFields(), field);
    }
    return encoded == null
        ? new AbsentFieldSubject()
        : new FoundFieldSubject(codeInspector, encoded, this);
  }

  @Override
  public FieldSubject uniqueFieldWithName(String name) {
    FieldSubject fieldSubject = null;
    for (FoundFieldSubject candidate : allFields()) {
      if (candidate.getOriginalName().equals(name)) {
        assert fieldSubject == null;
        fieldSubject = candidate;
      }
    }
    return fieldSubject != null ? fieldSubject : new AbsentFieldSubject();
  }

  @Override
  public FieldSubject uniqueFieldWithFinalName(String name) {
    FieldSubject fieldSubject = null;
    for (FoundFieldSubject candidate : allFields()) {
      if (candidate.getFinalName().equals(name)) {
        assert fieldSubject == null;
        fieldSubject = candidate;
      }
    }
    return fieldSubject != null ? fieldSubject : new AbsentFieldSubject();
  }

  @Override
  public FoundClassSubject asFoundClassSubject() {
    return this;
  }

  @Override
  public boolean isAbstract() {
    return dexClass.accessFlags.isAbstract();
  }

  @Override
  public boolean isPublic() {
    return dexClass.accessFlags.isPublic();
  }

  @Override
  public boolean isAnnotation() {
    return dexClass.accessFlags.isAnnotation();
  }

  private DexEncodedField findField(List<DexEncodedField> fields, DexField dexField) {
    for (DexEncodedField field : fields) {
      if (field.field.equals(dexField)) {
        return field;
      }
    }
    return null;
  }

  @Override
  public DexProgramClass getDexProgramClass() {
    assert dexClass.isProgramClass();
    return dexClass.asProgramClass();
  }

  public ClassSubject getSuperClass() {
    return codeInspector.clazz(dexClass.superType.toSourceString());
  }

  @Override
  public AnnotationSubject annotation(String name) {
    // Ensure we don't check for annotations represented as attributes.
    assert !name.endsWith("EnclosingClass")
        && !name.endsWith("EnclosingMethod")
        && !name.endsWith("InnerClass");
    DexAnnotation annotation = codeInspector.findAnnotation(name, dexClass.annotations());
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
  public String getOriginalBinaryName() {
    return DescriptorUtils.getBinaryNameFromDescriptor(getOriginalDescriptor());
  }

  public DexType getOriginalDexType(DexItemFactory dexItemFactory) {
    return dexItemFactory.createType(getOriginalDescriptor());
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
  public String getFinalBinaryName() {
    return DescriptorUtils.getBinaryNameFromDescriptor(getFinalDescriptor());
  }

  @Override
  public boolean isRenamed() {
    return naming != null && !getFinalDescriptor().equals(getOriginalDescriptor());
  }

  @Override
  public boolean isLocalClass() {
    return dexClass.isLocalClass();
  }

  @Override
  public boolean isMemberClass() {
    return dexClass.isMemberClass();
  }

  @Override
  public boolean isAnonymousClass() {
    return dexClass.isAnonymousClass();
  }

  @Override
  public boolean isSynthesizedJavaLambdaClass() {
    return dexClass.type.getName().contains("$Lambda$");
  }

  @Override
  public DexMethod getFinalEnclosingMethod() {
    return dexClass.getEnclosingMethodAttribute().getEnclosingMethod();
  }

  @Override
  public String getOriginalSignatureAttribute() {
    return codeInspector.getOriginalSignatureAttribute(
        dexClass.annotations(), GenericSignatureParser::parseClassSignature);
  }

  @Override
  public String getFinalSignatureAttribute() {
    return codeInspector.getFinalSignatureAttribute(dexClass.annotations());
  }

  @Override
  public int hashCode() {
    int result = codeInspector.hashCode();
    result = 31 * result + dexClass.hashCode();
    result = 31 * result + (naming != null ? naming.hashCode() : 0);
    return result;
  }

  @Override
  public boolean equals(Object other) {
    if (other == null || other.getClass() != this.getClass()) {
      return false;
    }
    FoundClassSubject otherSubject = (FoundClassSubject) other;
    return codeInspector == otherSubject.codeInspector
        && dexClass == otherSubject.dexClass
        && naming == otherSubject.naming;
  }

  @Override
  public String toString() {
    return dexClass.toSourceString();
  }

  public TypeSubject asTypeSubject() {
    return new TypeSubject(codeInspector, getDexProgramClass().type);
  }

  @Override
  public KmClassSubject getKmClass() {
    AnnotationSubject annotationSubject = annotation(METADATA_TYPE);
    if (!annotationSubject.isPresent()) {
      return new AbsentKmClassSubject();
    }
    KotlinClassMetadata metadata =
        KotlinClassMetadataReader.toKotlinClassMetadata(
            codeInspector.getFactory().kotlin, annotationSubject.getAnnotation());
    assertTrue(metadata instanceof KotlinClassMetadata.Class);
    KotlinClassMetadata.Class kClass = (KotlinClassMetadata.Class) metadata;
    return new FoundKmClassSubject(codeInspector, getDexProgramClass(), kClass.toKmClass());
  }

  @Override
  public KmPackageSubject getKmPackage() {
    AnnotationSubject annotationSubject = annotation(METADATA_TYPE);
    if (!annotationSubject.isPresent()) {
      return new AbsentKmPackageSubject();
    }
    KotlinClassMetadata metadata =
        KotlinClassMetadataReader.toKotlinClassMetadata(
            codeInspector.getFactory().kotlin, annotationSubject.getAnnotation());
    assertTrue(metadata instanceof KotlinClassMetadata.FileFacade
        || metadata instanceof KotlinClassMetadata.MultiFileClassPart);
    if (metadata instanceof KotlinClassMetadata.FileFacade) {
      KotlinClassMetadata.FileFacade kFile = (KotlinClassMetadata.FileFacade) metadata;
      return new FoundKmPackageSubject(codeInspector, getDexProgramClass(), kFile.toKmPackage());
    } else {
      KotlinClassMetadata.MultiFileClassPart kPart =
          (KotlinClassMetadata.MultiFileClassPart) metadata;
      return new FoundKmPackageSubject(codeInspector, getDexProgramClass(), kPart.toKmPackage());
    }
  }

  @Override
  public KotlinClassMetadata getKotlinClassMetadata() {
    AnnotationSubject annotationSubject = annotation(METADATA_TYPE);
    if (!annotationSubject.isPresent()) {
      return null;
    }
    return KotlinClassMetadataReader.toKotlinClassMetadata(
        codeInspector.getFactory().kotlin, annotationSubject.getAnnotation());
  }
}
