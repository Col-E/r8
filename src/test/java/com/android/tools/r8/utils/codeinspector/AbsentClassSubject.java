// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.ClassAccessFlags;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.naming.ClassNamingForNameMapper;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.retrace.RetraceClassElement;
import com.android.tools.r8.retrace.RetraceClassResult;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import kotlinx.metadata.jvm.KotlinClassMetadata;
import org.junit.rules.TemporaryFolder;

public class AbsentClassSubject extends ClassSubject {

  public AbsentClassSubject(CodeInspector codeInspector, ClassReference reference) {
    super(codeInspector, reference);
  }

  @Override
  public boolean isPresent() {
    return false;
  }

  @Override
  public void forAllMethods(Consumer<FoundMethodSubject> inspection) {}

  @Override
  public void forAllVirtualMethods(Consumer<FoundMethodSubject> inspection) {}

  @Override
  public MethodSubject method(String returnType, String name, List<String> parameters) {
    return new AbsentMethodSubject();
  }

  @Override
  public MethodSubject uniqueMethodThatMatches(Predicate<FoundMethodSubject> predicate) {
    return new AbsentMethodSubject();
  }

  @Override
  public MethodSubject uniqueMethodWithOriginalName(String name) {
    return new AbsentMethodSubject();
  }

  @Override
  public MethodSubject uniqueMethodWithFinalName(String name) {
    return new AbsentMethodSubject();
  }

  @Override
  public void forAllFields(Consumer<FoundFieldSubject> inspection) {}

  @Override
  public void forAllInstanceFields(Consumer<FoundFieldSubject> inspection) {}

  @Override
  public void forAllStaticFields(Consumer<FoundFieldSubject> inspection) {}

  @Override
  public FieldSubject field(String type, String name) {
    return new AbsentFieldSubject();
  }

  @Override
  public FieldSubject uniqueFieldWithOriginalName(String name) {
    return new AbsentFieldSubject();
  }

  @Override
  public FieldSubject uniqueFieldWithFinalName(String name) {
    return new AbsentFieldSubject();
  }

  @Override
  public boolean isAbstract() {
    throw new Unreachable("Cannot determine if an absent class is abstract");
  }

  @Override
  public boolean isAnnotation() {
    throw new Unreachable("Cannot determine if an absent class is an annotation");
  }

  @Override
  public boolean isExtending(ClassSubject subject) {
    throw new Unreachable("Cannot determine if an absent class is extending a given class");
  }

  @Override
  public boolean isImplementing(ClassSubject subject) {
    throw new Unreachable("Cannot determine if an absent class is implementing a given interface");
  }

  @Override
  public boolean isImplementing(Class<?> clazz) {
    throw new Unreachable("Cannot determine if an absent class is implementing a given interface");
  }

  @Override
  public boolean isImplementing(String javaTypeName) {
    throw new Unreachable("Cannot determine if an absent class is implementing a given interface");
  }

  @Override
  public DexProgramClass getDexProgramClass() {
    return null;
  }

  @Override
  public List<FoundAnnotationSubject> annotations() {
    throw new Unreachable("Cannot determine if an absent class has annotations");
  }

  @Override
  public AnnotationSubject annotation(String name) {
    return new AbsentAnnotationSubject();
  }

  @Override
  public ClassAccessFlags getAccessFlags() {
    throw new Unreachable("Absent class has no access flags");
  }

  @Override
  public TypeSubject getSuperType() {
    throw new Unreachable("Absent class has no super type");
  }

  @Override
  public boolean isInterface() {
    throw new Unreachable("Cannot determine if an absent class is an interface");
  }

  @Override
  public String getOriginalName() {
    return reference.getTypeName();
  }

  @Override
  public String getOriginalDescriptor() {
    return null;
  }

  @Override
  public String getOriginalBinaryName() {
    return null;
  }

  @Override
  public ClassReference getOriginalReference() {
    return null;
  }

  @Override
  public ClassReference getFinalReference() {
    return null;
  }

  @Override
  public String getFinalName() {
    return null;
  }

  @Override
  public String getFinalDescriptor() {
    return null;
  }

  @Override
  public String getFinalBinaryName() {
    return null;
  }

  @Override
  public boolean isRenamed() {
    throw new Unreachable("Cannot determine if an absent class has been renamed");
  }

  @Override
  public boolean isMemberClass() {
    throw new Unreachable("Cannot determine if an absent class is a member class");
  }

  @Override
  public boolean isLocalClass() {
    throw new Unreachable("Cannot determine if an absent class is a local class");
  }

  @Override
  public boolean isAnonymousClass() {
    throw new Unreachable("Cannot determine if an absent class is an anonymous class");
  }

  @Override
  public boolean isSynthesizedJavaLambdaClass() {
    throw new Unreachable("Cannot determine if an absent class is a synthesized lambda class");
  }

  @Override
  public DexMethod getFinalEnclosingMethod() {
    throw new Unreachable("Cannot determine EnclosingMethod attribute of an absent class");
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
  public TypeSubject getFinalNestHostAttribute() {
    throw new Unreachable("Cannot determine NestHost attribute of an absent class");
  }

  @Override
  public List<TypeSubject> getFinalNestMembersAttribute() {
    throw new Unreachable("Cannot determine NestMembers attribute of an absent class");
  }

  @Override
  public List<TypeSubject> getFinalPermittedSubclassAttributes() {
    throw new Unreachable("Cannot determine PermittedSubclasses attribute of an absent class");
  }

  @Override
  public List<RecordComponentSubject> getFinalRecordComponents() {
    throw new Unreachable("Cannot determine RecordComponents attribute of an absent class");
  }

  @Override
  public KmClassSubject getKmClass() {
    return null;
  }

  @Override
  public KmPackageSubject getKmPackage() {
    return null;
  }

  @Override
  public KotlinClassMetadata getKotlinClassMetadata() {
    return null;
  }

  @Override
  public RetraceClassResult retrace() {
    throw new Unreachable("Cannot retrace an absent class");
  }

  @Override
  public RetraceClassElement retraceUnique() {
    throw new Unreachable("Cannot retrace an absent class");
  }

  @Override
  public ClassNamingForNameMapper getNaming() {
    return null;
  }

  @Override
  public String javap(boolean verbose) {
    throw new Unreachable("Cannot disassembly an absent class");
  }

  @Override
  public String asmify(TemporaryFolder tempFolder, boolean debug) {
    throw new Unreachable("Cannot asmify an absent class");
  }
}
