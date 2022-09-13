// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;


import com.android.tools.r8.naming.ClassNamingForNameMapper;
import com.android.tools.r8.naming.ClassNamingForNameMapper.MappedRangesOfName;
import com.android.tools.r8.naming.MemberNaming;
import com.android.tools.r8.naming.MemberNaming.FieldSignature;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.naming.MemberNaming.Signature;
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
import com.android.tools.r8.utils.Pair;
import com.google.common.collect.ImmutableList;
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

  private static MethodSignature getSignature(
      ClassNamingForNameMapper mapper, MethodDefinition definition) {
    if (mapper != null && definition.isFullMethodDefinition()) {
      MemberNaming lookup =
          mapper.lookup(
              MethodSignature.fromMethodReference(
                  definition.asFullMethodDefinition().getMethodReference()));
      if (lookup != null) {
        return lookup.getOriginalSignature().asMethodSignature();
      }
    }
    return null;
  }

  private static FieldSignature getSignature(
      ClassNamingForNameMapper mapper, FieldDefinition definition) {
    if (mapper != null && definition.isFullFieldDefinition()) {
      MemberNaming lookup =
          mapper.lookup(
              FieldSignature.fromFieldReference(
                  definition.asFullFieldDefinition().getFieldReference()));
      if (lookup != null) {
        return lookup.getOriginalSignature().asFieldSignature();
      }
    }
    return null;
  }

  private RetraceFieldResultImpl lookupField(FieldDefinition fieldDefinition) {
    return lookup(
        fieldDefinition,
        getSignature(mapper, fieldDefinition),
        (mapper, name) -> {
          List<MemberNaming> memberNamings = mapper.mappedFieldNamingsByName.get(name);
          if (memberNamings == null || memberNamings.isEmpty()) {
            return null;
          }
          return memberNamings;
        },
        RetraceFieldResultImpl::new);
  }

  private RetraceMethodResultImpl lookupMethod(MethodDefinition methodDefinition) {
    return lookup(
        methodDefinition,
        getSignature(mapper, methodDefinition),
        (mapper, name) -> {
          MappedRangesOfName mappedRanges = mapper.mappedRangesByRenamedName.get(name);
          if (mappedRanges == null || mappedRanges.getMappedRanges().isEmpty()) {
            return null;
          }
          return mappedRanges.getMappedRanges();
        },
        RetraceMethodResultImpl::new);
  }

  private <T, R, D extends Definition, S extends Signature> R lookup(
      D definition,
      S originalSignature,
      BiFunction<ClassNamingForNameMapper, String, T> lookupFunction,
      ResultConstructor<T, R, D, S> constructor) {
    List<Pair<RetraceClassElementImpl, T>> mappings = new ArrayList<>();
    internalStream()
        .forEach(
            element -> {
              if (mapper != null) {
                assert element.mapper != null;
                T mappedElements = lookupFunction.apply(element.mapper, definition.getName());
                if (mappedElements != null) {
                  mappings.add(new Pair<>(element, mappedElements));
                  return;
                }
              }
              mappings.add(new Pair<>(element, null));
            });
    return constructor.create(this, mappings, definition, originalSignature, retracer);
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
            mapper == null
                ? obfuscatedReference
                : Reference.classFromTypeName(mapper.originalName)),
        mapper);
  }

  private interface ResultConstructor<T, R, D, S> {
    R create(
        RetraceClassResultImpl classResult,
        List<Pair<RetraceClassElementImpl, T>> mappings,
        D definition,
        S originalSignature,
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
          getSignature(mapper, fieldDefinition),
          (mapper, name) -> {
            List<MemberNaming> memberNamings = mapper.mappedFieldNamingsByName.get(name);
            if (memberNamings == null || memberNamings.isEmpty()) {
              return null;
            }
            return memberNamings;
          },
          RetraceFieldResultImpl::new);
    }

    @Override
    public RetraceMethodResultImpl lookupMethod(String methodName) {
      return lookupMethod(MethodDefinition.create(classReference.getClassReference(), methodName));
    }

    private RetraceMethodResultImpl lookupMethod(MethodDefinition methodDefinition) {
      return lookup(
          methodDefinition,
          getSignature(mapper, methodDefinition),
          (mapper, name) -> {
            MappedRangesOfName mappedRanges = mapper.mappedRangesByRenamedName.get(name);
            if (mappedRanges == null || mappedRanges.getMappedRanges().isEmpty()) {
              return null;
            }
            return mappedRanges.getMappedRanges();
          },
          RetraceMethodResultImpl::new);
    }

    private <T, R, D extends Definition, S extends Signature> R lookup(
        D definition,
        S originalSignature,
        BiFunction<ClassNamingForNameMapper, String, T> lookupFunction,
        ResultConstructor<T, R, D, S> constructor) {
      List<Pair<RetraceClassElementImpl, T>> mappings = ImmutableList.of();
      if (mapper != null) {
        T result = lookupFunction.apply(mapper, definition.getName());
        if (result != null) {
          mappings = ImmutableList.of(new Pair<>(this, result));
        }
      }
      if (mappings.isEmpty()) {
        mappings = ImmutableList.of(new Pair<>(this, null));
      }
      return constructor.create(
          classResult, mappings, definition, originalSignature, classResult.retracer);
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
