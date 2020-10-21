// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import static com.android.tools.r8.retrace.internal.RetraceUtils.methodReferenceFromMappedRange;

import com.android.tools.r8.naming.ClassNamingForNameMapper.MappedRange;
import com.android.tools.r8.naming.Range;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.retrace.RetraceFrameResult;
import com.android.tools.r8.retrace.RetraceSourceFileResult;
import com.android.tools.r8.retrace.RetracedClassMember;
import com.android.tools.r8.retrace.RetracedMethod;
import com.android.tools.r8.utils.Pair;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class RetraceFrameResultImpl implements RetraceFrameResult {

  private final RetraceClassResultImpl classResult;
  private final MethodDefinition methodDefinition;
  private final int obfuscatedPosition;
  private final List<Pair<RetraceClassResultImpl.ElementImpl, List<MappedRange>>> mappedRanges;
  private final RetracerImpl retracer;

  public RetraceFrameResultImpl(
      RetraceClassResultImpl classResult,
      List<Pair<RetraceClassResultImpl.ElementImpl, List<MappedRange>>> mappedRanges,
      MethodDefinition methodDefinition,
      int obfuscatedPosition,
      RetracerImpl retracer) {
    this.classResult = classResult;
    this.methodDefinition = methodDefinition;
    this.obfuscatedPosition = obfuscatedPosition;
    this.mappedRanges = mappedRanges;
    this.retracer = retracer;
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
                                classElement.getRetracedClass().getClassReference())),
                        ImmutableList.of(),
                        obfuscatedPosition));
              }
              // Iterate over mapped ranges that may have different positions than specified.
              List<ElementImpl> ambiguousFrames = new ArrayList<>();
              Range minifiedRange = mappedRanges.get(0).minifiedRange;
              List<MappedRange> mappedRangesForElement = Lists.newArrayList(mappedRanges.get(0));
              for (int i = 1; i < mappedRanges.size(); i++) {
                MappedRange mappedRange = mappedRanges.get(i);
                if (minifiedRange == null || !minifiedRange.equals(mappedRange.minifiedRange)) {
                  // This is a new frame
                  ambiguousFrames.add(
                      elementFromMappedRanges(mappedRangesForElement, classElement));
                  mappedRangesForElement = new ArrayList<>();
                }
                mappedRangesForElement.add(mappedRange);
              }
              ambiguousFrames.add(elementFromMappedRanges(mappedRangesForElement, classElement));
              return ambiguousFrames.stream();
            });
  }

  private ElementImpl elementFromMappedRanges(
      List<MappedRange> mappedRangesForElement, RetraceClassResultImpl.ElementImpl classElement) {
    MappedRange topFrame = mappedRangesForElement.get(0);
    MethodReference methodReference =
        methodReferenceFromMappedRange(
            topFrame, classElement.getRetracedClass().getClassReference());
    return new ElementImpl(
        this,
        classElement,
        getRetracedMethod(methodReference, topFrame, obfuscatedPosition),
        mappedRangesForElement,
        obfuscatedPosition);
  }

  @Override
  public RetraceFrameResultImpl forEach(Consumer<Element> resultConsumer) {
    stream().forEach(resultConsumer);
    return this;
  }

  private RetracedMethodImpl getRetracedMethod(
      MethodReference methodReference, MappedRange mappedRange, int obfuscatedPosition) {
    if (obfuscatedPosition == -1
        || mappedRange.minifiedRange == null
        || !mappedRange.minifiedRange.contains(obfuscatedPosition)) {
      return RetracedMethodImpl.create(methodReference);
    }
    return RetracedMethodImpl.create(
        methodReference, mappedRange.getOriginalLineNumber(obfuscatedPosition));
  }

  public static class ElementImpl implements RetraceFrameResult.Element {

    private final RetracedMethodImpl methodReference;
    private final RetraceFrameResultImpl retraceFrameResult;
    private final RetraceClassResultImpl.ElementImpl classElement;
    private final List<MappedRange> mappedRanges;
    private final int obfuscatedPosition;

    public ElementImpl(
        RetraceFrameResultImpl retraceFrameResult,
        RetraceClassResultImpl.ElementImpl classElement,
        RetracedMethodImpl methodReference,
        List<MappedRange> mappedRanges,
        int obfuscatedPosition) {
      this.methodReference = methodReference;
      this.retraceFrameResult = retraceFrameResult;
      this.classElement = classElement;
      this.mappedRanges = mappedRanges;
      this.obfuscatedPosition = obfuscatedPosition;
    }

    @Override
    public boolean isUnknown() {
      return methodReference.isUnknown();
    }

    @Override
    public RetracedMethodImpl getTopFrame() {
      return methodReference;
    }

    @Override
    public RetraceClassResultImpl.ElementImpl getClassElement() {
      return classElement;
    }

    @Override
    public void visitFrames(BiConsumer<RetracedMethod, Integer> consumer) {
      int counter = 0;
      consumer.accept(getTopFrame(), counter++);
      for (RetracedMethodImpl outerFrame : getOuterFrames()) {
        consumer.accept(outerFrame, counter++);
      }
    }

    @Override
    public RetraceSourceFileResult retraceSourceFile(RetracedClassMember frame, String sourceFile) {
      return RetraceUtils.getSourceFile(
          classElement, frame.getHolderClass(), sourceFile, retraceFrameResult.retracer);
    }

    @Override
    public List<RetracedMethodImpl> getOuterFrames() {
      if (mappedRanges == null) {
        return Collections.emptyList();
      }
      List<RetracedMethodImpl> outerFrames = new ArrayList<>();
      for (int i = 1; i < mappedRanges.size(); i++) {
        MappedRange mappedRange = mappedRanges.get(i);
        MethodReference methodReference =
            methodReferenceFromMappedRange(
                mappedRange, classElement.getRetracedClass().getClassReference());
        outerFrames.add(
            retraceFrameResult.getRetracedMethod(methodReference, mappedRange, obfuscatedPosition));
      }
      return outerFrames;
    }
  }
}
