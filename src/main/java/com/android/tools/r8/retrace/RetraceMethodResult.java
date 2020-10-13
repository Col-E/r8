// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.Keep;
import com.android.tools.r8.naming.ClassNamingForNameMapper.MappedRange;
import com.android.tools.r8.naming.ClassNamingForNameMapper.MappedRangesOfName;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.Pair;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Keep
public class RetraceMethodResult extends Result<RetraceMethodResult.Element, RetraceMethodResult> {

  private final String obfuscatedName;
  private final RetraceClassResult classResult;
  private final List<Pair<RetraceClassResult.Element, List<MappedRange>>> mappedRanges;
  private final RetraceApi retracer;

  RetraceMethodResult(
      RetraceClassResult classResult,
      List<Pair<RetraceClassResult.Element, List<MappedRange>>> mappedRanges,
      String obfuscatedName,
      RetraceApi retracer) {
    this.classResult = classResult;
    this.mappedRanges = mappedRanges;
    this.obfuscatedName = obfuscatedName;
    this.retracer = retracer;
    assert classResult != null;
    // TODO(mkroghj): Enable this when we have frame results.
    // assert !mappedRanges.isEmpty();
  }

  @Override
  public boolean isAmbiguous() {
    if (mappedRanges.size() > 1) {
      return true;
    }
    List<MappedRange> methodRanges = mappedRanges.get(0).getSecond();
    if (methodRanges == null || methodRanges.isEmpty()) {
      return false;
    }
    MappedRange lastRange = methodRanges.get(0);
    for (MappedRange mappedRange : methodRanges) {
      if (mappedRange != lastRange
          && (mappedRange.minifiedRange == null
              || !mappedRange.minifiedRange.equals(lastRange.minifiedRange))) {
        return true;
      }
    }
    return false;
  }

  public RetraceMethodResult narrowByLine(int linePosition) {
    List<Pair<RetraceClassResult.Element, List<MappedRange>>> narrowedRanges = new ArrayList<>();
    List<Pair<RetraceClassResult.Element, List<MappedRange>>> noMappingRanges = new ArrayList<>();
    for (Pair<RetraceClassResult.Element, List<MappedRange>> mappedRange : mappedRanges) {
      if (mappedRange.getSecond() == null) {
        noMappingRanges.add(new Pair<>(mappedRange.getFirst(), null));
        continue;
      }
      List<MappedRange> ranges =
          new MappedRangesOfName(mappedRange.getSecond()).allRangesForLine(linePosition, false);
      boolean hasAddedRanges = false;
      if (!ranges.isEmpty()) {
        narrowedRanges.add(new Pair<>(mappedRange.getFirst(), ranges));
        hasAddedRanges = true;
      } else {
        narrowedRanges = new ArrayList<>();
        for (MappedRange mapped : mappedRange.getSecond()) {
          if (mapped.minifiedRange == null) {
            narrowedRanges.add(new Pair<>(mappedRange.getFirst(), ImmutableList.of(mapped)));
            hasAddedRanges = true;
          }
        }
      }
      if (!hasAddedRanges) {
        narrowedRanges.add(new Pair<>(mappedRange.getFirst(), null));
      }
    }
    return new RetraceMethodResult(
        classResult,
        narrowedRanges.isEmpty() ? noMappingRanges : narrowedRanges,
        obfuscatedName,
        retracer);
  }

  @Override
  public Stream<Element> stream() {
    return mappedRanges.stream()
        .flatMap(
            mappedRangePair -> {
              RetraceClassResult.Element classElement = mappedRangePair.getFirst();
              List<MappedRange> mappedRanges = mappedRangePair.getSecond();
              if (mappedRanges == null) {
                return Stream.of(
                    new Element(
                        this,
                        classElement,
                        RetracedMethod.createUnknown(
                            classElement.getRetracedClass(), obfuscatedName),
                        null));
              }
              return mappedRanges.stream()
                  .map(
                      mappedRange -> {
                        MethodSignature signature = mappedRange.signature;
                        RetracedClass holder =
                            mappedRange.signature.isQualified()
                                ? RetracedClass.create(
                                    Reference.classFromDescriptor(
                                        DescriptorUtils.javaTypeToDescriptor(
                                            mappedRange.signature.toHolderFromQualified())))
                                : classElement.getRetracedClass();
                        List<TypeReference> formalTypes =
                            new ArrayList<>(signature.parameters.length);
                        for (String parameter : signature.parameters) {
                          formalTypes.add(Reference.typeFromTypeName(parameter));
                        }
                        TypeReference returnType =
                            Reference.returnTypeFromDescriptor(
                                DescriptorUtils.javaTypeToDescriptor(signature.type));
                        RetracedMethod retracedMethod =
                            RetracedMethod.create(
                                holder,
                                Reference.method(
                                    holder.getClassReference(),
                                    signature.isQualified()
                                        ? signature.toUnqualifiedName()
                                        : signature.name,
                                    formalTypes,
                                    returnType));
                        return new Element(this, classElement, retracedMethod, mappedRange);
                      });
            });
  }

  @Override
  public RetraceMethodResult forEach(Consumer<Element> resultConsumer) {
    stream().forEach(resultConsumer);
    return this;
  }

  public static class Element {

    private final RetracedMethod methodReference;
    private final RetraceMethodResult retraceMethodResult;
    private final RetraceClassResult.Element classElement;
    private final MappedRange mappedRange;

    private Element(
        RetraceMethodResult retraceMethodResult,
        RetraceClassResult.Element classElement,
        RetracedMethod methodReference,
        MappedRange mappedRange) {
      this.classElement = classElement;
      this.retraceMethodResult = retraceMethodResult;
      this.methodReference = methodReference;
      this.mappedRange = mappedRange;
    }

    public boolean isUnknown() {
      return methodReference.isUnknown();
    }

    public boolean hasNoLineNumberRange() {
      return mappedRange == null || mappedRange.minifiedRange == null;
    }

    public boolean containsMinifiedLineNumber(int linePosition) {
      if (hasNoLineNumberRange()) {
        return false;
      }
      return mappedRange.minifiedRange.from <= linePosition
          && linePosition <= mappedRange.minifiedRange.to;
    }

    public RetracedMethod getMethod() {
      return methodReference;
    }

    public RetraceMethodResult getRetraceMethodResult() {
      return retraceMethodResult;
    }

    public RetraceClassResult.Element getClassElement() {
      return classElement;
    }

    public int getOriginalLineNumber(int linePosition) {
      return mappedRange != null ? mappedRange.getOriginalLineNumber(linePosition) : linePosition;
    }

    public int getFirstLineNumberOfOriginalRange() {
      if (hasNoLineNumberRange()) {
        return 0;
      }
      return mappedRange.getFirstLineNumberOfOriginalRange();
    }

    public RetraceSourceFileResult retraceSourceFile(String sourceFile) {
      return RetraceUtils.getSourceFile(
          classElement, methodReference.getHolderClass(), sourceFile, retraceMethodResult.retracer);
    }
  }
}
