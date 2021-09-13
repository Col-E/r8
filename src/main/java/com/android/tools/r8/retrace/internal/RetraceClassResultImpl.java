// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;


import com.android.tools.r8.naming.ClassNamingForNameMapper;
import com.android.tools.r8.naming.ClassNamingForNameMapper.MappedRange;
import com.android.tools.r8.naming.ClassNamingForNameMapper.MappedRangesOfName;
import com.android.tools.r8.naming.MemberNaming;
import com.android.tools.r8.naming.mappinginformation.MappingInformation;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.retrace.RetraceClassElement;
import com.android.tools.r8.retrace.RetraceClassResult;
import com.android.tools.r8.retrace.RetraceFrameResult;
import com.android.tools.r8.retrace.RetraceSourceFileResult;
import com.android.tools.r8.retrace.RetraceUnknownJsonMappingInformationResult;
import com.android.tools.r8.retrace.Retracer;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.Pair;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
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

  RetracerImpl getRetracerImpl() {
    return retracer;
  }

  @Override
  public RetraceFieldResultImpl lookupField(String fieldName) {
    return lookupField(FieldDefinition.create(obfuscatedReference, fieldName));
  }

  @Override
  public RetraceFieldResultImpl lookupField(String fieldName, TypeReference fieldType) {
    return lookupField(
        FieldDefinition.create(Reference.field(obfuscatedReference, fieldName, fieldType)));
  }

  @Override
  public RetraceMethodResultImpl lookupMethod(String methodName) {
    return lookupMethod(MethodDefinition.create(obfuscatedReference, methodName));
  }

  @Override
  public RetraceMethodResultImpl lookupMethod(
      String methodName, List<TypeReference> formalTypes, TypeReference returnType) {
    return lookupMethod(
        MethodDefinition.create(
            Reference.method(obfuscatedReference, methodName, formalTypes, returnType)));
  }

  private RetraceFieldResultImpl lookupField(FieldDefinition fieldDefinition) {
    return lookup(
        fieldDefinition,
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
        (mapper, name) -> {
          MappedRangesOfName mappedRanges = mapper.mappedRangesByRenamedName.get(name);
          if (mappedRanges == null || mappedRanges.getMappedRanges().isEmpty()) {
            return null;
          }
          return mappedRanges.getMappedRanges();
        },
        RetraceMethodResultImpl::new);
  }

  private <T, R, D extends Definition> R lookup(
      D definition,
      BiFunction<ClassNamingForNameMapper, String, T> lookupFunction,
      ResultConstructor<T, R, D> constructor) {
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
    return constructor.create(this, mappings, definition, retracer);
  }

  @Override
  public RetraceFrameResultImpl lookupFrame(String methodName) {
    return lookupFrame(MethodDefinition.create(obfuscatedReference, methodName), -1);
  }

  @Override
  public RetraceFrameResultImpl lookupFrame(String methodName, int position) {
    return lookupFrame(MethodDefinition.create(obfuscatedReference, methodName), position);
  }

  @Override
  public RetraceFrameResultImpl lookupFrame(
      String methodName, int position, List<TypeReference> formalTypes, TypeReference returnType) {
    return lookupFrame(
        MethodDefinition.create(
            Reference.method(obfuscatedReference, methodName, formalTypes, returnType)),
        position);
  }

  private RetraceFrameResultImpl lookupFrame(MethodDefinition definition, int position) {
    List<Pair<RetraceClassElementImpl, List<MappedRange>>> mappings = new ArrayList<>();
    internalStream()
        .forEach(
            element -> {
              getMappedRangesForFrame(element, definition, position)
                  .forEach(
                      mappedRanges -> {
                        mappings.add(new Pair<>(element, mappedRanges));
                      });
            });
    return new RetraceFrameResultImpl(this, mappings, definition, position, retracer);
  }

  private List<List<MappedRange>> getMappedRangesForFrame(
      RetraceClassElementImpl element, MethodDefinition definition, int position) {
    List<List<MappedRange>> overloadedRanges = new ArrayList<>();
    if (mapper == null) {
      overloadedRanges.add(null);
      return overloadedRanges;
    }
    assert element.mapper != null;
    MappedRangesOfName mappedRanges = mapper.mappedRangesByRenamedName.get(definition.getName());
    if (mappedRanges == null || mappedRanges.getMappedRanges().isEmpty()) {
      overloadedRanges.add(null);
      return overloadedRanges;
    }
    List<MappedRange> mappedRangesForPosition = null;
    if (position >= 0) {
      mappedRangesForPosition = mappedRanges.allRangesForLine(position, false);
    }
    if (mappedRangesForPosition == null || mappedRangesForPosition.isEmpty()) {
      mappedRangesForPosition = mappedRanges.getMappedRanges();
    }
    assert mappedRangesForPosition != null && !mappedRangesForPosition.isEmpty();
    // Mapped ranges can have references to overloaded signatures. We distinguish those by looking
    // at the cardinal mapping range.
    for (MappedRange mappedRange : mappedRangesForPosition) {
      if (overloadedRanges.isEmpty()
          || mappedRange.originalRange == null
          || !mappedRange.originalRange.isCardinal) {
        overloadedRanges.add(new ArrayList<>());
      }
      ListUtils.last(overloadedRanges).add(mappedRange);
    }
    return overloadedRanges;
  }

  @Override
  public boolean hasRetraceResult() {
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

  private interface ResultConstructor<T, R, D> {
    R create(
        RetraceClassResultImpl classResult,
        List<Pair<RetraceClassElementImpl, T>> mappings,
        D definition,
        Retracer retracer);
  }

  public static class RetraceClassElementImpl implements RetraceClassElement {

    private final RetraceClassResultImpl classResult;
    private final RetracedClassReferenceImpl classReference;
    private final ClassNamingForNameMapper mapper;

    public RetraceClassElementImpl(
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
    public RetraceSourceFileResult getSourceFile() {
      if (classResult.mapper != null) {
        for (MappingInformation info : classResult.mapper.getAdditionalMappingInfo()) {
          if (info.isFileNameInformation()) {
            return new RetraceSourceFileResultImpl(info.asFileNameInformation().getFileName());
          }
        }
      }
      return new RetraceSourceFileResultImpl(null);
    }

    @Override
    public RetraceClassResultImpl getRetraceResultContext() {
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
          (mapper, name) -> {
            MappedRangesOfName mappedRanges = mapper.mappedRangesByRenamedName.get(name);
            if (mappedRanges == null || mappedRanges.getMappedRanges().isEmpty()) {
              return null;
            }
            return mappedRanges.getMappedRanges();
          },
          RetraceMethodResultImpl::new);
    }

    private <T, R, D extends Definition> R lookup(
        D definition,
        BiFunction<ClassNamingForNameMapper, String, T> lookupFunction,
        ResultConstructor<T, R, D> constructor) {
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
      return constructor.create(classResult, mappings, definition, classResult.retracer);
    }

    @Override
    public RetraceFrameResultImpl lookupFrame(String methodName) {
      return lookupFrame(methodName, -1);
    }

    @Override
    public RetraceFrameResultImpl lookupFrame(String methodName, int position) {
      return lookupFrame(
          MethodDefinition.create(classReference.getClassReference(), methodName), position);
    }

    @Override
    public RetraceFrameResult lookupFrame(
        String methodName,
        int position,
        List<TypeReference> formalTypes,
        TypeReference returnType) {
      return lookupFrame(
          MethodDefinition.create(
              Reference.method(
                  classReference.getClassReference(), methodName, formalTypes, returnType)),
          position);
    }

    @Override
    public RetraceUnknownJsonMappingInformationResult getUnknownJsonMappingInformation() {
      return RetraceUnknownJsonMappingInformationResultImpl.build(
          mapper.getAdditionalMappingInfo());
    }

    private RetraceFrameResultImpl lookupFrame(MethodDefinition definition, int position) {
      MethodDefinition methodDefinition =
          MethodDefinition.create(classReference.getClassReference(), definition.getName());
      ImmutableList.Builder<Pair<RetraceClassElementImpl, List<MappedRange>>> builder =
          ImmutableList.builder();
      classResult
          .getMappedRangesForFrame(this, methodDefinition, position)
          .forEach(
              mappedRanges -> {
                builder.add(new Pair<>(this, mappedRanges));
              });
      return new RetraceFrameResultImpl(
          classResult, builder.build(), methodDefinition, position, classResult.retracer);
    }
  }
}
