// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import static com.android.tools.r8.retrace.RetraceUtils.methodReferenceFromMappedRange;

import com.android.tools.r8.naming.ClassNamingForNameMapper.MappedRange;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.utils.Pair;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class RetraceFrameResult extends Result<RetraceFrameResult.Element, RetraceFrameResult> {

  private final RetraceClassResult classResult;
  private final MethodDefinition methodDefinition;
  private final int obfuscatedPosition;
  private final List<Pair<RetraceClassResult.Element, List<MappedRange>>> mappedRanges;
  private final RetraceApi retracer;

  public RetraceFrameResult(
      RetraceClassResult classResult,
      List<Pair<RetraceClassResult.Element, List<MappedRange>>> mappedRanges,
      MethodDefinition methodDefinition,
      int obfuscatedPosition,
      RetraceApi retracer) {
    this.classResult = classResult;
    this.methodDefinition = methodDefinition;
    this.obfuscatedPosition = obfuscatedPosition;
    this.mappedRanges = mappedRanges;
    this.retracer = retracer;
  }

  @Override
  public boolean isAmbiguous() {
    return mappedRanges.size() > 1;
  }

  @Override
  public Stream<Element> stream() {
    return mappedRanges.stream()
        .map(
            mappedRangePair -> {
              RetraceClassResult.Element classElement = mappedRangePair.getFirst();
              List<MappedRange> mappedRanges = mappedRangePair.getSecond();
              if (mappedRanges == null || mappedRanges.isEmpty()) {
                return new Element(
                    this,
                    classElement,
                    RetracedMethod.create(
                        methodDefinition.substituteHolder(
                            classElement.getRetracedClass().getClassReference())),
                    ImmutableList.of(),
                    obfuscatedPosition);
              }
              MappedRange mappedRange = mappedRanges.get(0);
              MethodReference methodReference =
                  methodReferenceFromMappedRange(
                      mappedRange, classElement.getRetracedClass().getClassReference());
              RetracedMethod retracedMethod =
                  RetracedMethod.create(
                      methodReference, mappedRange.getOriginalLineNumber(obfuscatedPosition));
              return new Element(
                  this, classElement, retracedMethod, mappedRanges, obfuscatedPosition);
            });
  }

  @Override
  public RetraceFrameResult forEach(Consumer<Element> resultConsumer) {
    stream().forEach(resultConsumer);
    return this;
  }

  public static class Element implements RetraceClassMemberElement<RetracedMethod> {

    private final RetracedMethod methodReference;
    private final RetraceFrameResult retraceFrameResult;
    private final RetraceClassResult.Element classElement;
    private final List<MappedRange> mappedRanges;
    private final int obfuscatedPosition;

    public Element(
        RetraceFrameResult retraceFrameResult,
        RetraceClassResult.Element classElement,
        RetracedMethod methodReference,
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
    public boolean isFrameElement() {
      return true;
    }

    @Override
    public Element asFrameElement() {
      return this;
    }

    @Override
    public RetracedMethod getMember() {
      return methodReference;
    }

    public RetracedMethod getTopFrame() {
      return methodReference;
    }

    @Override
    public RetraceClassResult.Element getClassElement() {
      return classElement;
    }

    @Override
    public void visitFrames(BiConsumer<RetracedMethod, Integer> consumer) {
      int counter = 0;
      consumer.accept(methodReference, counter++);
      for (RetracedMethod outerFrame : getOuterFrames()) {
        consumer.accept(outerFrame, counter++);
      }
    }

    @Override
    public RetraceSourceFileResult retraceSourceFile(RetracedClassMember frame, String sourceFile) {
      return RetraceUtils.getSourceFile(
          classElement, frame.getHolderClass(), sourceFile, retraceFrameResult.retracer);
    }

    public List<RetracedMethod> getOuterFrames() {
      if (mappedRanges == null) {
        return Collections.emptyList();
      }
      List<RetracedMethod> outerFrames = new ArrayList<>();
      for (int i = 1; i < mappedRanges.size(); i++) {
        MappedRange mappedRange = mappedRanges.get(i);
        MethodReference methodReference =
            methodReferenceFromMappedRange(
                mappedRange, classElement.getRetracedClass().getClassReference());
        outerFrames.add(
            RetracedMethod.create(
                MethodDefinition.create(methodReference),
                mappedRange.getOriginalLineNumber(obfuscatedPosition)));
      }
      return outerFrames;
    }
  }
}
