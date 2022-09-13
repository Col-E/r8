// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.naming.ClassNamingForNameMapper.MappedRange;
import com.android.tools.r8.naming.ClassNamingForNameMapper.MappedRangesOfName;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.naming.mappinginformation.OutlineCallsiteMappingInformation;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.retrace.RetraceMethodElement;
import com.android.tools.r8.retrace.RetraceMethodResult;
import com.android.tools.r8.retrace.RetraceStackTraceContext;
import com.android.tools.r8.retrace.RetracedMethodReference;
import com.android.tools.r8.retrace.RetracedSourceFile;
import com.android.tools.r8.retrace.internal.RetraceClassResultImpl.RetraceClassElementImpl;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.Pair;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Stream;

public class RetraceMethodResultImpl implements RetraceMethodResult {

  private final MethodDefinition methodDefinition;
  private final MethodSignature originalMethodSignature;
  private final RetraceClassResultImpl classResult;
  private final List<Pair<RetraceClassElementImpl, List<MappedRange>>> mappedRanges;
  private final RetracerImpl retracer;

  RetraceMethodResultImpl(
      RetraceClassResultImpl classResult,
      List<Pair<RetraceClassElementImpl, List<MappedRange>>> mappedRanges,
      MethodDefinition methodDefinition,
      MethodSignature originalMethodSignature,
      RetracerImpl retracer) {
    this.classResult = classResult;
    this.mappedRanges = mappedRanges;
    this.methodDefinition = methodDefinition;
    this.originalMethodSignature = originalMethodSignature;
    this.retracer = retracer;
    assert classResult != null;
    assert !mappedRanges.isEmpty();
  }

  @Override
  public boolean isAmbiguous() {
    if (mappedRanges.size() > 1) {
      return true;
    }
    if (originalMethodSignature != null) {
      return false;
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
  public boolean isEmpty() {
    return mappedRanges == null || mappedRanges.isEmpty();
  }

  @Override
  public RetraceFrameResultImpl narrowByPosition(
      RetraceStackTraceContext context, OptionalInt position) {
    List<Pair<RetraceClassElementImpl, List<MappedRange>>> narrowedRanges = new ArrayList<>();
    RetraceStackTraceContextImpl stackTraceContext = null;
    if (context instanceof RetraceStackTraceContextImpl) {
      stackTraceContext = (RetraceStackTraceContextImpl) context;
    }
    for (Pair<RetraceClassElementImpl, List<MappedRange>> mappedRange : mappedRanges) {
      if (mappedRange.getSecond() == null) {
        narrowedRanges.add(new Pair<>(mappedRange.getFirst(), null));
        continue;
      }
      MappedRangesOfName mappedRangesOfElement = new MappedRangesOfName(mappedRange.getSecond());
      List<MappedRange> mappedRangesForPosition = null;
      boolean hasPosition = position.isPresent() && position.getAsInt() >= 0;
      if (hasPosition) {
        mappedRangesForPosition =
            mappedRangesOfElement.allRangesForLine(position.getAsInt(), false);
      }
      if (mappedRangesForPosition == null || mappedRangesForPosition.isEmpty()) {
        mappedRangesForPosition =
            hasPosition
                ? ListUtils.filter(
                    mappedRangesOfElement.getMappedRanges(), range -> range.minifiedRange == null)
                : mappedRangesOfElement.getMappedRanges();
      }
      if (mappedRangesForPosition != null && !mappedRangesForPosition.isEmpty()) {
        if (stackTraceContext != null && stackTraceContext.hasRewritePosition()) {
          List<OutlineCallsiteMappingInformation> outlineCallsiteInformation =
              ListUtils.last(mappedRangesForPosition).getOutlineCallsiteInformation();
          if (!outlineCallsiteInformation.isEmpty()) {
            assert outlineCallsiteInformation.size() == 1
                : "There can only be one outline entry for a line";
            return narrowByPosition(
                stackTraceContext.buildFromThis().clearRewritePosition().build(),
                OptionalInt.of(
                    outlineCallsiteInformation
                        .get(0)
                        .rewritePosition(stackTraceContext.getRewritePosition())));
          }
        }
        narrowedRanges.add(new Pair<>(mappedRange.getFirst(), mappedRangesForPosition));
      }
    }
    return new RetraceFrameResultImpl(
        classResult,
        narrowedRanges,
        methodDefinition,
        position,
        retracer,
        (RetraceStackTraceContextImpl) context);
  }

  @Override
  public Stream<RetraceMethodElement> stream() {
    if (originalMethodSignature != null) {
      // Even if we know exactly the retraced residual definition, the class element
      // may still be ambiguous.
      return mappedRanges.stream()
          .map(
              mappedRangePair -> {
                RetraceClassElementImpl classElement = mappedRangePair.getFirst();
                MethodReference methodReference =
                    RetraceUtils.methodReferenceFromMethodSignature(
                        originalMethodSignature,
                        classElement.getRetracedClass().getClassReference());
                return new ElementImpl(
                    this, classElement, RetracedMethodReferenceImpl.create(methodReference));
              });
    }
    return mappedRanges.stream()
        .flatMap(
            mappedRangePair -> {
              RetraceClassElementImpl classElement = mappedRangePair.getFirst();
              List<MappedRange> mappedRanges = mappedRangePair.getSecond();
              if (mappedRanges == null || mappedRanges.isEmpty()) {
                return Stream.of(
                    new ElementImpl(
                        this,
                        classElement,
                        RetracedMethodReferenceImpl.create(
                            methodDefinition.substituteHolder(
                                classElement.getRetracedClass().getClassReference()))));
              }
              List<ElementImpl> results = new ArrayList<>();
              Set<MethodReference> seenMethodReferences = new HashSet<>();
              for (MappedRange mappedRange : mappedRanges) {
                MethodReference methodReference =
                    RetraceUtils.methodReferenceFromMappedRange(
                        mappedRange, classElement.getRetracedClass().getClassReference());
                if (seenMethodReferences.add(methodReference)) {
                  results.add(
                      new ElementImpl(
                          this, classElement, RetracedMethodReferenceImpl.create(methodReference)));
                }
              }
              return results.stream();
            });
  }

  public static class ElementImpl implements RetraceMethodElement {

    private final RetracedMethodReferenceImpl methodReference;
    private final RetraceMethodResultImpl retraceMethodResult;
    private final RetraceClassElementImpl classElement;

    private ElementImpl(
        RetraceMethodResultImpl retraceMethodResult,
        RetraceClassElementImpl classElement,
        RetracedMethodReferenceImpl methodReference) {
      this.classElement = classElement;
      this.retraceMethodResult = retraceMethodResult;
      this.methodReference = methodReference;
    }

    @Override
    public boolean isCompilerSynthesized() {
      throw new Unimplemented("b/172014416");
    }

    @Override
    public boolean isUnknown() {
      return methodReference.isUnknown();
    }

    @Override
    public RetracedMethodReference getRetracedMethod() {
      return methodReference;
    }

    @Override
    public RetraceMethodResult getParentResult() {
      return retraceMethodResult;
    }

    @Override
    public RetraceClassElementImpl getClassElement() {
      return classElement;
    }

    @Override
    public RetracedSourceFile getSourceFile() {
      return RetraceUtils.getSourceFile(
          methodReference.getHolderClass(), retraceMethodResult.retracer);
    }
  }
}
