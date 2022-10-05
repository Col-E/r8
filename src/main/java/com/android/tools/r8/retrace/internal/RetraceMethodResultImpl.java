// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.naming.ClassNamingForNameMapper.MappedRange;
import com.android.tools.r8.naming.ClassNamingForNameMapper.MappedRangesOfName;
import com.android.tools.r8.naming.MemberNaming;
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
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Stream;

public class RetraceMethodResultImpl implements RetraceMethodResult {

  private final MethodDefinition methodDefinition;
  private final RetraceClassResultImpl classResult;
  private final List<Pair<RetraceClassElementImpl, List<MemberNamingWithMappedRangesOfName>>>
      mappedRanges;
  private final RetracerImpl retracer;

  RetraceMethodResultImpl(
      RetraceClassResultImpl classResult,
      List<Pair<RetraceClassElementImpl, List<MemberNamingWithMappedRangesOfName>>> mappedRanges,
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
    List<MemberNamingWithMappedRangesOfName> mappedRangesOfNames = mappedRanges.get(0).getSecond();
    return mappedRangesOfNames != null && mappedRangesOfNames.size() > 1;
  }

  @Override
  public boolean isEmpty() {
    List<MemberNamingWithMappedRangesOfName> mappedRangesOfNames = mappedRanges.get(0).getSecond();
    return mappedRangesOfNames == null || mappedRangesOfNames.isEmpty();
  }

  @Override
  public RetraceFrameResultImpl narrowByPosition(
      RetraceStackTraceContext context, OptionalInt position) {
    List<Pair<RetraceClassElementImpl, List<MemberNamingWithMappedRangesOfName>>> narrowedRanges =
        new ArrayList<>();
    RetraceStackTraceContextImpl stackTraceContext = null;
    if (context instanceof RetraceStackTraceContextImpl) {
      stackTraceContext = (RetraceStackTraceContextImpl) context;
    }
    for (Pair<RetraceClassElementImpl, List<MemberNamingWithMappedRangesOfName>> mappedRange :
        mappedRanges) {
      List<MemberNamingWithMappedRangesOfName> memberNamingWithMappedRanges =
          mappedRange.getSecond();
      if (memberNamingWithMappedRanges == null) {
        narrowedRanges.add(new Pair<>(mappedRange.getFirst(), null));
        continue;
      }
      boolean hasPosition = position.isPresent() && position.getAsInt() >= 0;
      List<MemberNamingWithMappedRangesOfName> newMemberNamingsResult = new ArrayList<>();
      for (MemberNamingWithMappedRangesOfName memberNamingWithMappedRange :
          memberNamingWithMappedRanges) {
        List<MappedRange> mappedRangesForPosition = null;
        if (hasPosition) {
          mappedRangesForPosition =
              memberNamingWithMappedRange.allRangesForLine(position.getAsInt());
        }
        if (mappedRangesForPosition == null || mappedRangesForPosition.isEmpty()) {
          mappedRangesForPosition =
              hasPosition
                  ? memberNamingWithMappedRange.mappedRangesWithNoMinifiedRange()
                  : memberNamingWithMappedRange.getMappedRanges();
        }
        if (mappedRangesForPosition != null && !mappedRangesForPosition.isEmpty()) {
          MemberNamingWithMappedRangesOfName newMemberNaming =
              new MemberNamingWithMappedRangesOfName(
                  memberNamingWithMappedRange.getMemberNaming(),
                  new MappedRangesOfName(mappedRangesForPosition));
          newMemberNamingsResult.add(newMemberNaming);
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
        }
      }
      narrowedRanges.add(new Pair<>(mappedRange.getFirst(), newMemberNamingsResult));
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
    return mappedRanges.stream()
        .flatMap(
            mappedRangePair -> {
              RetraceClassElementImpl classElement = mappedRangePair.getFirst();
              List<MemberNamingWithMappedRangesOfName> memberNamingsWithMappedRange =
                  mappedRangePair.getSecond();
              if (memberNamingsWithMappedRange == null || memberNamingsWithMappedRange.isEmpty()) {
                return Stream.of(
                    new ElementImpl(
                        this,
                        classElement,
                        RetracedMethodReferenceImpl.create(
                            methodDefinition.substituteHolder(
                                classElement.getRetracedClass().getClassReference())),
                        null));
              }
              return ListUtils.map(
                  memberNamingsWithMappedRange,
                  memberNamingWithMappedRangesOfName -> {
                    MemberNaming memberNaming =
                        memberNamingWithMappedRangesOfName.getMemberNaming();
                    MethodSignature originalSignature =
                        memberNaming != null
                            ? memberNaming.getOriginalSignature().asMethodSignature()
                            : ListUtils.last(memberNamingWithMappedRangesOfName.getMappedRanges())
                                .getOriginalSignature()
                                .asMethodSignature();
                    MethodReference methodReference =
                        RetraceUtils.methodReferenceFromMethodSignature(
                            originalSignature, classElement.getRetracedClass().getClassReference());
                    return new ElementImpl(
                        this,
                        classElement,
                        RetracedMethodReferenceImpl.create(methodReference),
                        memberNamingWithMappedRangesOfName);
                  })
                  .stream();
            });
  }

  public static class ElementImpl implements RetraceMethodElement {

    private final RetracedMethodReferenceImpl methodReference;
    private final RetraceMethodResultImpl retraceMethodResult;
    private final RetraceClassElementImpl classElement;
    private final MemberNamingWithMappedRangesOfName mapping;

    private ElementImpl(
        RetraceMethodResultImpl retraceMethodResult,
        RetraceClassElementImpl classElement,
        RetracedMethodReferenceImpl methodReference,
        MemberNamingWithMappedRangesOfName mapping) {
      this.classElement = classElement;
      this.retraceMethodResult = retraceMethodResult;
      this.methodReference = methodReference;
      this.mapping = mapping;
      assert mapping != null;
    }

    @Override
    public boolean isCompilerSynthesized() {
      if (mapping.getMemberNaming() != null) {
        return mapping.getMemberNaming().isCompilerSynthesized();
      } else {
        List<MappedRange> mappedRanges = mapping.getMappedRanges();
        return !mappedRanges.isEmpty() && ListUtils.last(mappedRanges).isCompilerSynthesized();
      }
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
