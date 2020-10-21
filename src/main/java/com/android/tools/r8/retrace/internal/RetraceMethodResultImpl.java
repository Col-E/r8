// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.Keep;
import com.android.tools.r8.naming.ClassNamingForNameMapper.MappedRange;
import com.android.tools.r8.naming.ClassNamingForNameMapper.MappedRangesOfName;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.retrace.RetraceMethodResult;
import com.android.tools.r8.utils.Pair;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Keep
public class RetraceMethodResultImpl implements RetraceMethodResult {

  private final MethodDefinition methodDefinition;
  private final RetraceClassResultImpl classResult;
  private final List<Pair<RetraceClassResultImpl.ElementImpl, List<MappedRange>>> mappedRanges;
  private final RetracerImpl retracer;

  RetraceMethodResultImpl(
      RetraceClassResultImpl classResult,
      List<Pair<RetraceClassResultImpl.ElementImpl, List<MappedRange>>> mappedRanges,
      MethodDefinition methodDefinition,
      RetracerImpl retracer) {
    this.classResult = classResult;
    this.mappedRanges = mappedRanges;
    this.methodDefinition = methodDefinition;
    this.retracer = retracer;
    assert classResult != null;
    assert !mappedRanges.isEmpty();
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

  @Override
  public RetraceFrameResultImpl narrowByPosition(int position) {
    List<Pair<RetraceClassResultImpl.ElementImpl, List<MappedRange>>> narrowedRanges =
        new ArrayList<>();
    List<Pair<RetraceClassResultImpl.ElementImpl, List<MappedRange>>> noMappingRanges =
        new ArrayList<>();
    for (Pair<RetraceClassResultImpl.ElementImpl, List<MappedRange>> mappedRange : mappedRanges) {
      if (mappedRange.getSecond() == null) {
        noMappingRanges.add(new Pair<>(mappedRange.getFirst(), null));
        continue;
      }
      List<MappedRange> ranges =
          new MappedRangesOfName(mappedRange.getSecond()).allRangesForLine(position, false);
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
    return new RetraceFrameResultImpl(
        classResult,
        narrowedRanges.isEmpty() ? noMappingRanges : narrowedRanges,
        methodDefinition,
        position,
        retracer);
  }

  @Override
  public Stream<Element> stream() {
    return mappedRanges.stream()
        .flatMap(
            mappedRangePair -> {
              RetraceClassResultImpl.ElementImpl classElement = mappedRangePair.getFirst();
              List<MappedRange> mappedRanges = mappedRangePair.getSecond();
              if (mappedRanges == null || mappedRanges.isEmpty()) {
                return Stream.of(
                    new ElementImpl(
                        this,
                        classElement,
                        RetracedMethodImpl.create(
                            methodDefinition.substituteHolder(
                                classElement.getRetracedClass().getClassReference()))));
              }
              return mappedRanges.stream()
                  .map(
                      mappedRange -> {
                        MethodReference methodReference =
                            RetraceUtils.methodReferenceFromMappedRange(
                                mappedRange, classElement.getRetracedClass().getClassReference());
                        return new ElementImpl(
                            this, classElement, RetracedMethodImpl.create(methodReference));
                      });
            });
  }

  @Override
  public RetraceMethodResultImpl forEach(Consumer<Element> resultConsumer) {
    stream().forEach(resultConsumer);
    return this;
  }

  public static class ElementImpl implements RetraceMethodResult.Element {

    private final RetracedMethodImpl methodReference;
    private final RetraceMethodResultImpl retraceMethodResult;
    private final RetraceClassResultImpl.ElementImpl classElement;

    private ElementImpl(
        RetraceMethodResultImpl retraceMethodResult,
        RetraceClassResultImpl.ElementImpl classElement,
        RetracedMethodImpl methodReference) {
      this.classElement = classElement;
      this.retraceMethodResult = retraceMethodResult;
      this.methodReference = methodReference;
    }

    @Override
    public boolean isUnknown() {
      return methodReference.isUnknown();
    }

    @Override
    public com.android.tools.r8.retrace.RetracedMethod getRetracedMethod() {
      return null;
    }

    @Override
    public RetraceMethodResultImpl getRetraceMethodResult() {
      return retraceMethodResult;
    }

    @Override
    public RetraceClassResultImpl.ElementImpl getClassElement() {
      return classElement;
    }

    @Override
    public com.android.tools.r8.retrace.RetraceSourceFileResult retraceSourceFile(
        String sourceFile) {
      return RetraceUtils.getSourceFile(
          classElement, methodReference.getHolderClass(), sourceFile, retraceMethodResult.retracer);
    }
  }
}
