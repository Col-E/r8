// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import static com.android.tools.r8.naming.MemberNaming.NoSignature.NO_SIGNATURE;
import static com.android.tools.r8.retrace.RetraceUtils.synthesizeFileName;

import com.android.tools.r8.Keep;
import com.android.tools.r8.naming.ClassNamingForNameMapper;
import com.android.tools.r8.naming.ClassNamingForNameMapper.MappedRangesOfName;
import com.android.tools.r8.naming.MemberNaming;
import com.android.tools.r8.naming.mappinginformation.MappingInformation;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.retrace.RetraceClassResult.Element;
import com.android.tools.r8.utils.Pair;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Keep
public class RetraceClassResult extends Result<Element, RetraceClassResult> {

  private final ClassReference obfuscatedReference;
  private final ClassNamingForNameMapper mapper;
  private final RetraceApi retracer;

  private RetraceClassResult(
      ClassReference obfuscatedReference, ClassNamingForNameMapper mapper, RetraceApi retracer) {
    this.obfuscatedReference = obfuscatedReference;
    this.mapper = mapper;
    this.retracer = retracer;
  }

  static RetraceClassResult create(
      ClassReference obfuscatedReference, ClassNamingForNameMapper mapper, RetraceApi retracer) {
    return new RetraceClassResult(obfuscatedReference, mapper, retracer);
  }

  public RetraceFieldResult lookupField(String fieldName) {
    return lookupField(FieldDefinition.create(obfuscatedReference, fieldName));
  }

  public RetraceFieldResult lookupField(String fieldName, TypeReference fieldType) {
    return lookupField(
        FieldDefinition.create(Reference.field(obfuscatedReference, fieldName, fieldType)));
  }

  public RetraceMethodResult lookupMethod(String methodName) {
    return lookupMethod(MethodDefinition.create(obfuscatedReference, methodName));
  }

  public RetraceMethodResult lookupMethod(
      String methodName, List<TypeReference> formalTypes, TypeReference returnType) {
    return lookupMethod(
        MethodDefinition.create(
            Reference.method(obfuscatedReference, methodName, formalTypes, returnType)));
  }

  private RetraceFieldResult lookupField(FieldDefinition fieldDefinition) {
    return lookup(
        fieldDefinition,
        (mapper, name) -> {
          List<MemberNaming> memberNamings = mapper.mappedFieldNamingsByName.get(name);
          if (memberNamings == null || memberNamings.isEmpty()) {
            return null;
          }
          return memberNamings;
        },
        RetraceFieldResult::new);
  }

  private RetraceMethodResult lookupMethod(MethodDefinition methodDefinition) {
    return lookup(
        methodDefinition,
        (mapper, name) -> {
          MappedRangesOfName mappedRanges = mapper.mappedRangesByRenamedName.get(name);
          if (mappedRanges == null || mappedRanges.getMappedRanges().isEmpty()) {
            return null;
          }
          return mappedRanges.getMappedRanges();
        },
        RetraceMethodResult::new);
  }

  private <T, R, D extends Definition> R lookup(
      D definition,
      BiFunction<ClassNamingForNameMapper, String, T> lookupFunction,
      ResultConstructor<T, R, D> constructor) {
    List<Pair<Element, T>> mappings = new ArrayList<>();
    forEach(
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

  boolean hasRetraceResult() {
    return mapper != null;
  }

  @Override
  public Stream<Element> stream() {
    return Stream.of(
        new Element(
            this,
            RetracedClass.create(
                mapper == null
                    ? obfuscatedReference
                    : Reference.classFromTypeName(mapper.originalName)),
            mapper));
  }

  @Override
  public RetraceClassResult forEach(Consumer<Element> resultConsumer) {
    stream().forEach(resultConsumer);
    return this;
  }

  private interface ResultConstructor<T, R, D> {
    R create(
        RetraceClassResult classResult,
        List<Pair<Element, T>> mappings,
        D definition,
        RetraceApi retraceApi);
  }

  @Override
  public boolean isAmbiguous() {
    // Currently we have no way of producing ambiguous class results.
    return false;
  }

  public static class Element {

    private final RetraceClassResult classResult;
    private final RetracedClass classReference;
    private final ClassNamingForNameMapper mapper;

    public Element(
        RetraceClassResult classResult,
        RetracedClass classReference,
        ClassNamingForNameMapper mapper) {
      this.classResult = classResult;
      this.classReference = classReference;
      this.mapper = mapper;
    }

    public RetracedClass getRetracedClass() {
      return classReference;
    }

    public RetraceClassResult getRetraceClassResult() {
      return classResult;
    }

    public RetraceSourceFileResult retraceSourceFile(String sourceFile) {
      if (mapper != null && mapper.getAdditionalMappings().size() > 0) {
        List<MappingInformation> mappingInformations =
            mapper.getAdditionalMappings().get(NO_SIGNATURE);
        if (mappingInformations != null) {
          for (MappingInformation mappingInformation : mappingInformations) {
            if (mappingInformation.isFileNameInformation()) {
              return new RetraceSourceFileResult(
                  mappingInformation.asFileNameInformation().getFileName(), false);
            }
          }
        }
      }
      return new RetraceSourceFileResult(
          synthesizeFileName(
              classReference.getTypeName(),
              classResult.obfuscatedReference.getTypeName(),
              sourceFile,
              mapper != null),
          true);
    }

    public RetraceFieldResult lookupField(String fieldName) {
      return lookupField(FieldDefinition.create(classReference.getClassReference(), fieldName));
    }

    private RetraceFieldResult lookupField(FieldDefinition fieldDefinition) {
      return lookup(
          fieldDefinition,
          (mapper, name) -> {
            List<MemberNaming> memberNamings = mapper.mappedFieldNamingsByName.get(name);
            if (memberNamings == null || memberNamings.isEmpty()) {
              return null;
            }
            return memberNamings;
          },
          RetraceFieldResult::new);
    }

    public RetraceMethodResult lookupMethod(String methodName) {
      return lookupMethod(MethodDefinition.create(classReference.getClassReference(), methodName));
    }

    private RetraceMethodResult lookupMethod(MethodDefinition methodDefinition) {
      return lookup(
          methodDefinition,
          (mapper, name) -> {
            MappedRangesOfName mappedRanges = mapper.mappedRangesByRenamedName.get(name);
            if (mappedRanges == null || mappedRanges.getMappedRanges().isEmpty()) {
              return null;
            }
            return mappedRanges.getMappedRanges();
          },
          RetraceMethodResult::new);
    }

    private <T, R, D extends Definition> R lookup(
        D definition,
        BiFunction<ClassNamingForNameMapper, String, T> lookupFunction,
        ResultConstructor<T, R, D> constructor) {
      List<Pair<Element, T>> mappings = ImmutableList.of();
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
  }
}
