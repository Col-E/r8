// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.Keep;
import com.android.tools.r8.naming.ClassNamingForNameMapper.MappedRange;
import com.android.tools.r8.naming.ClassNamingForNameMapper.MappedRangesOfName;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.utils.Pair;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Keep
public class RetraceMethodResult extends Result<RetraceMethodResult.Element, RetraceMethodResult> {

  private final MethodDefinition methodDefinition;
  private final RetraceClassResult classResult;
  private final List<Pair<RetraceClassResult.Element, List<MappedRange>>> mappedRanges;
  private final RetraceApi retracer;

  RetraceMethodResult(
      RetraceClassResult classResult,
      List<Pair<RetraceClassResult.Element, List<MappedRange>>> mappedRanges,
      MethodDefinition methodDefinition,
      RetraceApi retracer) {
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

  public RetraceFrameResult narrowByPosition(int position) {
    List<Pair<RetraceClassResult.Element, List<MappedRange>>> narrowedRanges = new ArrayList<>();
    List<Pair<RetraceClassResult.Element, List<MappedRange>>> noMappingRanges = new ArrayList<>();
    for (Pair<RetraceClassResult.Element, List<MappedRange>> mappedRange : mappedRanges) {
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
    return new RetraceFrameResult(
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
              RetraceClassResult.Element classElement = mappedRangePair.getFirst();
              List<MappedRange> mappedRanges = mappedRangePair.getSecond();
              if (mappedRanges == null || mappedRanges.isEmpty()) {
                return Stream.of(
                    new Element(
                        this,
                        classElement,
                        RetracedMethod.create(
                            methodDefinition.substituteHolder(
                                classElement.getRetracedClass().getClassReference()))));
              }
              return mappedRanges.stream()
                  .map(
                      mappedRange -> {
                        MethodReference methodReference =
                            RetraceUtils.methodReferenceFromMappedRange(
                                mappedRange, classElement.getRetracedClass().getClassReference());
                        return new Element(
                            this, classElement, RetracedMethod.create(methodReference));
                      });
            });
  }

  @Override
  public RetraceMethodResult forEach(Consumer<Element> resultConsumer) {
    stream().forEach(resultConsumer);
    return this;
  }

  public static class Element implements RetraceClassMemberElement<RetracedMethod> {

    private final RetracedMethod methodReference;
    private final RetraceMethodResult retraceMethodResult;
    private final RetraceClassResult.Element classElement;

    private Element(
        RetraceMethodResult retraceMethodResult,
        RetraceClassResult.Element classElement,
        RetracedMethod methodReference) {
      this.classElement = classElement;
      this.retraceMethodResult = retraceMethodResult;
      this.methodReference = methodReference;
    }

    @Override
    public boolean isUnknown() {
      return methodReference.isUnknown();
    }

    @Override
    public RetracedMethod getMember() {
      return methodReference;
    }

    public RetraceMethodResult getRetraceMethodResult() {
      return retraceMethodResult;
    }

    @Override
    public RetraceClassResult.Element getClassElement() {
      return classElement;
    }

    @Override
    public void visitFrames(BiConsumer<RetracedMethod, Integer> consumer) {
      consumer.accept(methodReference, 0);
    }

    @Override
    public RetraceSourceFileResult retraceSourceFile(RetracedClassMember frame, String sourceFile) {
      return RetraceUtils.getSourceFile(
          classElement, methodReference.getHolderClass(), sourceFile, retraceMethodResult.retracer);
    }
  }
}
