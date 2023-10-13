// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import static com.android.tools.r8.KotlinTestBase.METADATA_TYPE;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.graph.ClassAccessFlags;
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
import com.android.tools.r8.graph.NestMemberClassAttribute;
import com.android.tools.r8.graph.PermittedSubclassAttribute;
import com.android.tools.r8.kotlin.KotlinClassMetadataReader;
import com.android.tools.r8.kotlin.KotlinMetadataException;
import com.android.tools.r8.naming.ClassNamingForNameMapper;
import com.android.tools.r8.naming.MapVersion;
import com.android.tools.r8.naming.MemberNaming;
import com.android.tools.r8.naming.MemberNaming.FieldSignature;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.naming.MemberNaming.Signature;
import com.android.tools.r8.naming.mappinginformation.MappingInformation;
import com.android.tools.r8.naming.mappinginformation.ResidualSignatureMappingInformation;
import com.android.tools.r8.naming.signature.GenericSignatureParser;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.retrace.RetraceClassElement;
import com.android.tools.r8.retrace.RetraceClassResult;
import com.android.tools.r8.retrace.RetraceTypeResult;
import com.android.tools.r8.retrace.RetracedFieldReference;
import com.android.tools.r8.retrace.Retracer;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ZipUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector.MappingWrapper;
import com.google.common.collect.Sets;
import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import kotlinx.metadata.jvm.KotlinClassMetadata;
import org.junit.rules.TemporaryFolder;

public class FoundClassSubject extends ClassSubject {

  private final DexClass dexClass;
  private final MappingWrapper mapping;

  FoundClassSubject(
      CodeInspector codeInspector,
      DexClass dexClass,
      MappingWrapper mapping,
      ClassReference reference) {
    super(codeInspector, reference);
    this.dexClass = dexClass;
    this.mapping = mapping;
  }

  @Override
  public boolean isPresent() {
    return true;
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
    if (getNaming() != null) {
      Signature signature =
          new MethodSignature(name, returnType, parameters.toArray(StringUtils.EMPTY_ARRAY));
      MemberNaming methodNaming = getNaming().lookupByOriginalSignature(signature);
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
      if (method.getReference().equals(dexMethod)) {
        return method;
      }
    }
    return null;
  }

  @Override
  public MethodSubject uniqueMethodThatMatches(Predicate<FoundMethodSubject> predicate) {
    MethodSubject methodSubject = null;
    for (FoundMethodSubject candidate : allMethods(predicate)) {
      assert methodSubject == null;
      methodSubject = candidate;
    }
    return methodSubject != null ? methodSubject : new AbsentMethodSubject();
  }

  @Override
  public MethodSubject uniqueMethodWithOriginalName(String name) {
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
  public MethodSubject uniqueMethodWithFinalName(String name) {
    MethodSubject methodSubject = null;
    for (FoundMethodSubject candidate : allMethods()) {
      if (candidate.getFinalName().equals(name)) {
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
    if (getNaming() != null) {
      fieldNaming = getNaming().lookupByOriginalSignature(new FieldSignature(name, type));
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
  public FieldSubject uniqueFieldWithOriginalName(String name) {
    return uniqueFieldWithOriginalName(name, null);
  }

  // TODO(b/169882658): This should be removed when we have identity mappings for ambiguous cases.
  public FieldSubject uniqueFieldWithOriginalName(String name, TypeReference originalType) {
    Retracer retracer = codeInspector.retrace();
    ClassReference finalReference = getFinalReference();
    Set<FoundFieldSubject> candidates = Sets.newIdentityHashSet();
    Set<FoundFieldSubject> sameTypeCandidates = Sets.newIdentityHashSet();
    for (FoundFieldSubject candidate : allFields()) {
      FieldReference fieldReference = candidate.getDexField().asFieldReference();
      // TODO(b/169882658): This if should be removed completely.
      if (candidate.getFinalName().equals(name)) {
        candidates.add(candidate);
        if (isNullOrEqual(originalType, fieldReference.getFieldType())) {
          sameTypeCandidates.add(candidate);
        }
      }
      retracer
          .retraceClass(finalReference)
          .lookupField(candidate.getFinalName())
          .forEach(
              element -> {
                RetracedFieldReference field = element.getField();
                if (!element.isUnknown() && field.getFieldName().equals(name)) {
                  candidates.add(candidate);
                  // TODO(b/169953605): There should not be a need for mapping the final type.
                  TypeReference fieldOriginalType = originalType;
                  if (fieldOriginalType == null) {
                    RetraceTypeResult retraceTypeResult =
                        retracer.retraceType(fieldReference.getFieldType());
                    assert !retraceTypeResult.isAmbiguous();
                    fieldOriginalType =
                        retraceTypeResult.stream().iterator().next().getType().getTypeReference();
                  }
                  if (isNullOrEqual(fieldOriginalType, field.asKnown().getFieldType())) {
                    sameTypeCandidates.add(candidate);
                  }
                }
              });
    }
    assert candidates.size() >= sameTypeCandidates.size();
    // If we have any merged types we cannot rely on sameTypeCandidates, so we look in all
    // candidates first.
    if (candidates.size() == 1) {
      return candidates.iterator().next();
    }
    return sameTypeCandidates.size() == 1
        ? sameTypeCandidates.iterator().next()
        : new AbsentFieldSubject();
  }

  private boolean isNullOrEqual(TypeReference original, TypeReference rewritten) {
    return original == null || original.equals(rewritten);
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
  public TypeSubject asTypeSubject() {
    return new TypeSubject(codeInspector, dexClass.getType());
  }

  @Override
  public boolean isAbstract() {
    return dexClass.accessFlags.isAbstract();
  }

  @Override
  public boolean isExtending(ClassSubject subject) {
    return getSuperClass().getDexProgramClass().getType() == subject.getDexProgramClass().getType();
  }

  @Override
  public boolean isInterface() {
    return dexClass.isInterface();
  }

  @Override
  public boolean isImplementing(ClassSubject subject) {
    assertTrue(subject.isPresent());
    for (DexType itf : getDexProgramClass().interfaces) {
      if (itf.toSourceString().equals(subject.getFinalName())) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isImplementing(Class<?> clazz) {
    return isImplementing(clazz.getTypeName());
  }

  @Override
  public boolean isImplementing(String javaTypeName) {
    for (DexType itf : getDexProgramClass().interfaces) {
      if (itf.toSourceString().equals(javaTypeName)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isAnnotation() {
    return dexClass.accessFlags.isAnnotation();
  }

  private DexEncodedField findField(List<DexEncodedField> fields, DexField dexField) {
    for (DexEncodedField field : fields) {
      if (field.getReference().equals(dexField)) {
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
  public List<FoundAnnotationSubject> annotations() {
    return FoundAnnotationSubject.listFromDex(dexClass.annotations(), codeInspector);
  }

  @Override
  public AnnotationSubject annotation(String name) {
    // Ensure we don't check for annotations represented as attributes.
    assert !name.endsWith("EnclosingClass")
        && !name.endsWith("EnclosingMethod")
        && !name.endsWith("InnerClass");
    DexAnnotation annotation = codeInspector.findAnnotation(dexClass.annotations(), name);
    return annotation == null
        ? new AbsentAnnotationSubject()
        : new FoundAnnotationSubject(annotation, codeInspector);
  }

  @Override
  public ClassAccessFlags getAccessFlags() {
    return getDexProgramClass().getAccessFlags();
  }

  @Override
  public TypeSubject getSuperType() {
    return new TypeSubject(codeInspector, dexClass.getSuperType());
  }

  @Override
  public String getOriginalName() {
    if (getNaming() != null) {
      return getNaming().originalName;
    } else {
      return getFinalName();
    }
  }

  @Override
  public String getOriginalDescriptor() {
    if (getNaming() != null) {
      return DescriptorUtils.javaTypeToDescriptor(getNaming().originalName);
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
  public ClassReference getOriginalReference() {
    return Reference.classFromDescriptor(getOriginalDescriptor());
  }

  @Override
  public ClassReference getFinalReference() {
    return Reference.classFromDescriptor(getFinalDescriptor());
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
    return getNaming() != null && !getFinalDescriptor().equals(getOriginalDescriptor());
  }

  @Override
  public boolean isCompilerSynthesized() {
    for (MappingInformation info : mapping.getAdditionalMappings()) {
      if (info.isCompilerSynthesizedMappingInformation()) {
        return true;
      }
    }
    return false;
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
    // TODO(141287349): Make this precise based on the map input.
    return SyntheticItemsTestUtils.isExternalLambda(getOriginalReference())
        || SyntheticItemsTestUtils.isExternalLambda(getFinalReference());
  }

  @Override
  public DexMethod getFinalEnclosingMethod() {
    return dexClass.getEnclosingMethodAttribute().getEnclosingMethod();
  }

  @Override
  public String getOriginalSignatureAttribute() {
    return codeInspector.getOriginalSignatureAttribute(
        dexClass.getClassSignature().toString(), GenericSignatureParser::parseClassSignature);
  }

  @Override
  public String getFinalSignatureAttribute() {
    return dexClass.getClassSignature().toString();
  }

  @Override
  public TypeSubject getFinalNestHostAttribute() {
    if (dexClass.getNestHost() == null) {
      return null;
    }
    return new TypeSubject(codeInspector, dexClass.getNestHost());
  }

  @Override
  public List<TypeSubject> getFinalNestMembersAttribute() {
    List<TypeSubject> result = new ArrayList<>();
    for (NestMemberClassAttribute member : dexClass.getNestMembersClassAttributes()) {
      result.add(new TypeSubject(codeInspector, member.getNestMember()));
    }
    return result;
  }

  @Override
  public List<TypeSubject> getFinalPermittedSubclassAttributes() {
    List<TypeSubject> result = new ArrayList<>();
    for (PermittedSubclassAttribute permittedSubclassAttribute :
        dexClass.getPermittedSubclassAttributes()) {
      result.add(new TypeSubject(codeInspector, permittedSubclassAttribute.getPermittedSubclass()));
    }
    return result;
  }

  @Override
  public List<RecordComponentSubject> getFinalRecordComponents() {
    List<RecordComponentSubject> result = new ArrayList<>(dexClass.getRecordComponents().size());
    for (int i = 0; i < dexClass.getRecordComponents().size(); i++) {
      result.add(new RecordComponentSubject(codeInspector, dexClass, i));
    }
    return result;
  }

  @Override
  public int hashCode() {
    int result = codeInspector.hashCode();
    result = 31 * result + dexClass.hashCode();
    result = 31 * result + (getNaming() != null ? getNaming().hashCode() : 0);
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
        && getNaming() == otherSubject.getNaming();
  }

  @Override
  public String toString() {
    return dexClass.toSourceString();
  }

  @Override
  public KmClassSubject getKmClass() {
    AnnotationSubject annotationSubject = annotation(METADATA_TYPE);
    if (!annotationSubject.isPresent()) {
      return new AbsentKmClassSubject();
    }
    KotlinClassMetadata metadata = null;
    try {
      metadata =
          KotlinClassMetadataReader.toKotlinClassMetadata(
              codeInspector.getFactory().kotlin, annotationSubject.getAnnotation());
    } catch (KotlinMetadataException e) {
      throw new RuntimeException(e);
    }
    assertTrue(metadata instanceof KotlinClassMetadata.Class);
    KotlinClassMetadata.Class kClass = (KotlinClassMetadata.Class) metadata;
    return new FoundKmClassSubject(codeInspector, getDexProgramClass(), kClass.getKmClass());
  }

  @Override
  public KmPackageSubject getKmPackage() {
    AnnotationSubject annotationSubject = annotation(METADATA_TYPE);
    if (!annotationSubject.isPresent()) {
      return new AbsentKmPackageSubject();
    }
    KotlinClassMetadata metadata = null;
    try {
      metadata =
          KotlinClassMetadataReader.toKotlinClassMetadata(
              codeInspector.getFactory().kotlin, annotationSubject.getAnnotation());
    } catch (KotlinMetadataException e) {
      throw new RuntimeException(e);
    }
    assertTrue(metadata instanceof KotlinClassMetadata.FileFacade
        || metadata instanceof KotlinClassMetadata.MultiFileClassPart);
    if (metadata instanceof KotlinClassMetadata.FileFacade) {
      KotlinClassMetadata.FileFacade kFile = (KotlinClassMetadata.FileFacade) metadata;
      return new FoundKmPackageSubject(codeInspector, getDexProgramClass(), kFile.getKmPackage());
    } else {
      KotlinClassMetadata.MultiFileClassPart kPart =
          (KotlinClassMetadata.MultiFileClassPart) metadata;
      return new FoundKmPackageSubject(codeInspector, getDexProgramClass(), kPart.getKmPackage());
    }
  }

  @Override
  public KotlinClassMetadata getKotlinClassMetadata() {
    AnnotationSubject annotationSubject = annotation(METADATA_TYPE);
    if (!annotationSubject.isPresent()) {
      return null;
    }
    try {
      return KotlinClassMetadataReader.toKotlinClassMetadata(
          codeInspector.getFactory().kotlin, annotationSubject.getAnnotation());
    } catch (KotlinMetadataException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public RetraceClassResult retrace() {
    assertTrue(mapping.getNaming() != null);
    return codeInspector
        .getRetracer()
        .retraceClass(Reference.classFromTypeName(mapping.getNaming().renamedName));
  }

  @Override
  public RetraceClassElement retraceUnique() {
    RetraceClassResult result = retrace();
    if (result.isAmbiguous()) {
      fail("Expected unique retrace of " + this + ", got ambiguous: " + result);
    }
    Optional<RetraceClassElement> first = result.stream().findFirst();
    if (!first.isPresent()) {
      fail("Expected unique retrace of " + this + ", got empty result");
    }
    return first.get();
  }

  @Override
  public ClassNamingForNameMapper getNaming() {
    return mapping.getNaming();
  }

  @Override
  public boolean hasResidualSignatureMapping() {
    MapVersion mapVersion = mapping.getMapVersion();
    return !mapVersion.isUnknown() && ResidualSignatureMappingInformation.isSupported(mapVersion);
  }

  @Override
  public String javap(boolean verbose) throws Exception {
    assert dexClass.origin != null;
    List<String> command = new ArrayList<>();
    command.add(
        CfRuntime.getCheckedInJdk9().getJavaHome().resolve("bin").resolve("javap").toString());
    if (verbose) {
      command.add("-v");
      command.add("-c");
      command.add("-p");
    }
    command.add("-cp");
    List<String> parts = dexClass.origin.parts();
    assert parts.size() == 2;
    command.add(parts.get(0));
    command.add(parts.get(1).replace(".class", ""));
    ProcessResult processResult = ToolHelper.runProcess(new ProcessBuilder(command));
    assert processResult.exitCode == 0;
    return processResult.stdout;
  }

  @Override
  public String asmify(TemporaryFolder tempFolder, boolean debug) throws Exception {
    assert dexClass.origin != null;
    List<String> parts = dexClass.origin.parts();
    assert parts.size() == 2;
    String directory = parts.get(0);
    String fileName = parts.get(1);
    if (directory.endsWith(".jar") || directory.endsWith(".zip")) {
      File tempOut = tempFolder.newFolder();
      ZipUtils.unzip(directory, tempOut);
      directory = tempOut.getAbsolutePath();
    }
    List<String> command = new ArrayList<>();
    command.add(
        CfRuntime.getCheckedInJdk9().getJavaHome().resolve("bin").resolve("java").toString());
    command.add("-cp");
    command.add(ToolHelper.ASM_JAR + ":" + ToolHelper.ASM_UTIL_JAR);
    command.add("org.objectweb.asm.util.ASMifier");
    if (!debug) {
      command.add("-debug");
    }
    command.add(Paths.get(directory, fileName).toString());
    ProcessResult processResult = ToolHelper.runProcess(new ProcessBuilder(command));
    assert processResult.exitCode == 0;
    System.out.println(processResult.stdout);
    return processResult.stdout;
  }

  public MemberNaming getMethodMappingInfo(DexEncodedMethod dexMethod) {
    return mapping
        .getNaming()
        .lookup(MethodSignature.fromDexMethod(dexMethod.getReference(), false));
  }
}
