// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.Keep;
import com.android.tools.r8.naming.ClassNamingForNameMapper.MappedRange;
import com.android.tools.r8.naming.ClassNamingForNameMapper.MappedRangesOfName;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.naming.Range;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.utils.DescriptorUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Keep
public class RetraceMethodResult extends Result<RetraceMethodResult.Element, RetraceMethodResult> {

  private final String obfuscatedName;
  private final RetraceClassResult.Element classElement;
  private final MappedRangesOfName mappedRanges;
  private final RetraceApi retracer;
  private Boolean isAmbiguousCached = null;

  RetraceMethodResult(
      RetraceClassResult.Element classElement,
      MappedRangesOfName mappedRanges,
      String obfuscatedName,
      RetraceApi retracer) {
    this.classElement = classElement;
    this.mappedRanges = mappedRanges;
    this.obfuscatedName = obfuscatedName;
    this.retracer = retracer;
    assert classElement != null;
  }

  public RetracedMethod getUnknownReference() {
    return RetracedMethod.createUnknown(classElement.getRetracedClass(), obfuscatedName);
  }

  private boolean hasRetraceResult() {
    return mappedRanges != null && mappedRanges.getMappedRanges().size() > 0;
  }

  public boolean isAmbiguous() {
    if (isAmbiguousCached != null) {
      return isAmbiguousCached;
    }
    if (!hasRetraceResult()) {
      return false;
    }
    assert mappedRanges != null;
    Range minifiedRange = null;
    boolean seenNull = false;
    for (MappedRange mappedRange : mappedRanges.getMappedRanges()) {
      if (minifiedRange != null && !minifiedRange.equals(mappedRange.minifiedRange)) {
        isAmbiguousCached = true;
        return true;
      } else if (minifiedRange == null) {
        if (seenNull) {
          isAmbiguousCached = true;
          return true;
        }
        seenNull = true;
      }
      minifiedRange = mappedRange.minifiedRange;
    }
    isAmbiguousCached = false;
    return false;
  }

  public RetraceMethodResult narrowByLine(int linePosition) {
    if (!hasRetraceResult()) {
      return this;
    }
    List<MappedRange> narrowedRanges = this.mappedRanges.allRangesForLine(linePosition, false);
    if (narrowedRanges.isEmpty()) {
      narrowedRanges = new ArrayList<>();
      for (MappedRange mappedRange : this.mappedRanges.getMappedRanges()) {
        if (mappedRange.minifiedRange == null) {
          narrowedRanges.add(mappedRange);
        }
      }
    }
    return new RetraceMethodResult(
        classElement, new MappedRangesOfName(narrowedRanges), obfuscatedName, retracer);
  }

  @Override
  public Stream<Element> stream() {
    if (!hasRetraceResult()) {
      return Stream.of(new Element(this, classElement, getUnknownReference(), null));
    }
    return mappedRanges.getMappedRanges().stream()
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
              List<TypeReference> formalTypes = new ArrayList<>(signature.parameters.length);
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
                          signature.isQualified() ? signature.toUnqualifiedName() : signature.name,
                          formalTypes,
                          returnType));
              return new Element(this, classElement, retracedMethod, mappedRange);
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
