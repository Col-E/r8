// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;


import com.android.tools.r8.naming.ClassNamingForNameMapper;
import com.android.tools.r8.naming.ClassNamingForNameMapper.MappedRangesOfName;
import com.android.tools.r8.naming.MemberNaming;
import com.android.tools.r8.naming.MemberNaming.FieldSignature;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.naming.mappinginformation.MappingInformation;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.retrace.RetraceClassElement;
import com.android.tools.r8.retrace.RetraceClassResult;
import com.android.tools.r8.retrace.RetraceFrameResult;
import com.android.tools.r8.retrace.RetraceStackTraceContext;
import com.android.tools.r8.retrace.RetraceUnknownJsonMappingInformationResult;
import com.android.tools.r8.retrace.RetracedSourceFile;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.Pair;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.BiFunction;
import java.util.stream.Stream;

public class RetraceClassResultImpl implements RetraceClassResult {

  private final ClassReference obfuscatedReference;
  private final ClassNamingForNameMapper mapper;
  private final RetracerImpl retracer;

  private RetraceClassResultImpl(
      ClassReference obfuscatedReference, ClassNamingForNameMapper mapper, RetracerImpl retracer) {
    this.obfuscatedReference = obfuscatedReference;
    this.mapper = mapper;
    this.retracer = retracer;
  }

  static RetraceClassResultImpl create(
      ClassReference obfuscatedReference, ClassNamingForNameMapper mapper, RetracerImpl retracer) {
    return new RetraceClassResultImpl(obfuscatedReference, mapper, retracer);
  }

  @Override
  public RetraceFieldResultImpl lookupField(String fieldName) {
    return lookupField(FieldDefinition.create(obfuscatedReference, fieldName));
  }

  @Override
  public RetraceFieldResultImpl lookupField(String fieldName, TypeReference fieldType) {
    return lookupFieldInternal(Reference.field(obfuscatedReference, fieldName, fieldType));
  }

  RetraceFieldResultImpl lookupFieldInternal(FieldReference fieldReference) {
    return lookupField(FieldDefinition.create(fieldReference));
  }

  private RetraceFieldResultImpl lookupField(FieldDefinition fieldDefinition) {
    return lookup(
        fieldDefinition,
        RetraceClassResultImpl::lookupMemberNamingsForFieldDefinition,
        RetraceFieldResultImpl::new);
  }

  private static List<MemberNaming> lookupMemberNamingsForFieldDefinition(
      ClassNamingForNameMapper mapper, FieldDefinition fieldDefinition) {
    List<MemberNaming> memberNamings =
        mapper.mappedFieldNamingsByName.get(fieldDefinition.getName());
    if (memberNamings == null || memberNamings.isEmpty()) {
      return null;
    }
    if (fieldDefinition.isFullFieldDefinition()) {
      FieldSignature fieldSignature =
          FieldSignature.fromFieldReference(
              fieldDefinition.asFullFieldDefinition().getFieldReference());
      memberNamings =
          ListUtils.filter(
              memberNamings,
              memberNaming -> memberNaming.getResidualSignature().equals(fieldSignature));
    }
    return memberNamings;
  }

  @Override
  public RetraceMethodResultImpl lookupMethod(String methodName) {
    return lookupMethod(MethodDefinition.create(obfuscatedReference, methodName));
  }

  @Override
  public RetraceMethodResultImpl lookupMethod(
      String methodName, List<TypeReference> formalTypes, TypeReference returnType) {
    return lookupMethodInternal(
        Reference.method(obfuscatedReference, methodName, formalTypes, returnType));
  }

  RetraceMethodResultImpl lookupMethodInternal(MethodReference reference) {
    return lookupMethod(MethodDefinition.create(reference));
  }

  private RetraceMethodResultImpl lookupMethod(MethodDefinition methodDefinition) {
    return lookup(
        methodDefinition,
        RetraceClassResultImpl::lookupMappedRangesForMethodDefinition,
        RetraceMethodResultImpl::new);
  }

  private static List<MemberNamingWithMappedRangesOfName> lookupMappedRangesForMethodDefinition(
      ClassNamingForNameMapper mapper, MethodDefinition methodDefinition) {
    MappedRangesOfName mappedRanges =
        mapper.mappedRangesByRenamedName.get(methodDefinition.getName());
    if (mappedRanges == null || mappedRanges.getMappedRanges().isEmpty()) {
      return null;
    }
    List<MappedRangesOfName> partitions = mappedRanges.partitionOnMethodSignature();
    if (methodDefinition.isFullMethodDefinition()) {
      MethodSignature methodSignature =
          MethodSignature.fromMethodReference(
              methodDefinition.asFullMethodDefinition().getMethodReference());
      partitions =
          ListUtils.filter(
              partitions,
              partition ->
                  ListUtils.last(partition.getMappedRanges())
                      .getResidualSignature()
                      .equals(methodSignature));
    }
    return ListUtils.map(
        partitions,
        mappedRangesOfName ->
            new MemberNamingWithMappedRangesOfName(
                mappedRangesOfName.getMemberNaming(mapper), mappedRangesOfName));
  }

  private static <T, D extends Definition> void lookupElement(
      RetraceClassElementImpl element,
      D definition,
      List<Pair<RetraceClassElementImpl, T>> mappings,
      BiFunction<ClassNamingForNameMapper, D, T> lookupFunction) {
    if (element.mapper != null) {
      T mappedElements = lookupFunction.apply(element.mapper, definition);
      if (mappedElements != null) {
        mappings.add(new Pair<>(element, mappedElements));
        return;
      }
    }
    mappings.add(new Pair<>(element, null));
  }

  private <T, R, D extends Definition> R lookup(
      D definition,
      BiFunction<ClassNamingForNameMapper, D, T> lookupFunction,
      ResultConstructor<T, R, D> constructor) {
    List<Pair<RetraceClassElementImpl, T>> mappings = new ArrayList<>();
    internalStream()
        .forEach(element -> lookupElement(element, definition, mappings, lookupFunction));
    return constructor.create(this, mappings, definition, retracer);
  }

  @Override
  public RetraceFrameResultImpl lookupFrame(
      RetraceStackTraceContext context, OptionalInt position, String methodName) {
    return lookupFrame(context, position, MethodDefinition.create(obfuscatedReference, methodName));
  }

  @Override
  public RetraceFrameResultImpl lookupFrame(
      RetraceStackTraceContext context,
      OptionalInt position,
      String methodName,
      List<TypeReference> formalTypes,
      TypeReference returnType) {
    return lookupFrame(
        context,
        position,
        MethodDefinition.create(
            Reference.method(obfuscatedReference, methodName, formalTypes, returnType)));
  }

  private RetraceFrameResultImpl lookupFrame(
      RetraceStackTraceContext context, OptionalInt position, MethodDefinition definition) {
    return lookupMethod(definition).narrowByPosition(context, position);
  }

  @Override
  public RetraceThrownExceptionResultImpl lookupThrownException(RetraceStackTraceContext context) {
    return new RetraceThrownExceptionResultImpl(
        (RetraceStackTraceContextImpl) context, obfuscatedReference, mapper);
  }

  @Override
  public boolean isEmpty() {
    return mapper != null;
  }

  @Override
  public Stream<RetraceClassElement> stream() {
    return Stream.of(createElement());
  }

  private Stream<RetraceClassElementImpl> internalStream() {
    return Stream.of(createElement());
  }

  private RetraceClassElementImpl createElement() {
    return new RetraceClassElementImpl(
        this,
        RetracedClassReferenceImpl.create(
            mapper == null ? obfuscatedReference : Reference.classFromTypeName(mapper.originalName),
            mapper != null),
        mapper);
  }

  private interface ResultConstructor<T, R, D> {
    R create(
        RetraceClassResultImpl classResult,
        List<Pair<RetraceClassElementImpl, T>> mappings,
        D definition,
        RetracerImpl retracer);
  }

  public static class RetraceClassElementImpl implements RetraceClassElement {

    private final RetraceClassResultImpl classResult;
    private final RetracedClassReferenceImpl classReference;
    private final ClassNamingForNameMapper mapper;

    private RetraceClassElementImpl(
        RetraceClassResultImpl classResult,
        RetracedClassReferenceImpl classReference,
        ClassNamingForNameMapper mapper) {
      this.classResult = classResult;
      this.classReference = classReference;
      this.mapper = mapper;
    }

    @Override
    public RetracedClassReferenceImpl getRetracedClass() {
      return classReference;
    }

    @Override
    public RetracedSourceFile getSourceFile() {
      return RetraceUtils.getSourceFile(classReference, classResult.retracer);
    }

    @Override
    public RetraceClassResultImpl getParentResult() {
      return classResult;
    }

    @Override
    public boolean isCompilerSynthesized() {
      if (classResult.mapper != null) {
        for (MappingInformation info : classResult.mapper.getAdditionalMappingInfo()) {
          if (info.isCompilerSynthesizedMappingInformation()) {
            return true;
          }
        }
      }
      return false;
    }

    @Override
    public RetraceFieldResultImpl lookupField(String fieldName) {
      return lookupField(FieldDefinition.create(classReference.getClassReference(), fieldName));
    }

    private RetraceFieldResultImpl lookupField(FieldDefinition fieldDefinition) {
      return lookup(
          fieldDefinition,
          RetraceClassResultImpl::lookupMemberNamingsForFieldDefinition,
          RetraceFieldResultImpl::new);
    }

    @Override
    public RetraceMethodResultImpl lookupMethod(String methodName) {
      return lookupMethod(MethodDefinition.create(classReference.getClassReference(), methodName));
    }

    private RetraceMethodResultImpl lookupMethod(MethodDefinition methodDefinition) {
      return lookup(
          methodDefinition,
          RetraceClassResultImpl::lookupMappedRangesForMethodDefinition,
          RetraceMethodResultImpl::new);
    }

    private <T, R, D extends Definition> R lookup(
        D definition,
        BiFunction<ClassNamingForNameMapper, D, T> lookupFunction,
        ResultConstructor<T, R, D> constructor) {
      List<Pair<RetraceClassElementImpl, T>> mappings = new ArrayList<>();
      RetraceClassResultImpl.lookupElement(this, definition, mappings, lookupFunction);
      return constructor.create(classResult, mappings, definition, classResult.retracer);
    }

    @Override
    public RetraceFrameResultImpl lookupFrame(
        RetraceStackTraceContext context, OptionalInt position, String methodName) {
      return lookupFrame(
          context,
          position,
          MethodDefinition.create(classReference.getClassReference(), methodName));
    }

    @Override
    public RetraceFrameResult lookupFrame(
        RetraceStackTraceContext context,
        OptionalInt position,
        String methodName,
        List<TypeReference> formalTypes,
        TypeReference returnType) {
      return lookupFrame(
          context,
          position,
          MethodDefinition.create(
              Reference.method(
                  classReference.getClassReference(), methodName, formalTypes, returnType)));
    }

    @Override
    public RetraceFrameResult lookupFrame(
        RetraceStackTraceContext context, OptionalInt position, MethodReference methodReference) {
      return lookupFrame(context, position, MethodDefinition.create(methodReference));
    }

    @Override
    public RetraceUnknownJsonMappingInformationResult getUnknownJsonMappingInformation() {
      return RetraceUnknownJsonMappingInformationResultImpl.build(
          mapper.getAdditionalMappingInfo());
    }

    private RetraceFrameResultImpl lookupFrame(
        RetraceStackTraceContext context, OptionalInt position, MethodDefinition definition) {
      return classResult.lookupFrame(context, position, definition);
    }
  }
}
