// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.Keep;
import com.android.tools.r8.naming.MemberNaming;
import com.android.tools.r8.naming.MemberNaming.FieldSignature;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.retrace.RetraceFieldResult;
import com.android.tools.r8.retrace.RetraceSourceFileResult;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.Pair;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Keep
public class RetraceFieldResultImpl implements RetraceFieldResult {

  private final RetraceClassResultImpl classResult;
  private final List<Pair<RetraceClassResultImpl.ElementImpl, List<MemberNaming>>> memberNamings;
  private final FieldDefinition fieldDefinition;
  private final RetracerImpl retracer;

  RetraceFieldResultImpl(
      RetraceClassResultImpl classResult,
      List<Pair<RetraceClassResultImpl.ElementImpl, List<MemberNaming>>> memberNamings,
      FieldDefinition fieldDefinition,
      RetracerImpl retracer) {
    this.classResult = classResult;
    this.memberNamings = memberNamings;
    this.fieldDefinition = fieldDefinition;
    this.retracer = retracer;
    assert classResult != null;
    assert !memberNamings.isEmpty();
  }

  @Override
  public Stream<Element> stream() {
    return memberNamings.stream()
        .flatMap(
            mappedNamePair -> {
              RetraceClassResultImpl.ElementImpl classElement = mappedNamePair.getFirst();
              List<MemberNaming> memberNamings = mappedNamePair.getSecond();
              if (memberNamings == null) {
                return Stream.of(
                    new ElementImpl(
                        this,
                        classElement,
                        RetracedFieldImpl.create(
                            fieldDefinition.substituteHolder(
                                classElement.getRetracedClass().getClassReference()))));
              }
              return memberNamings.stream()
                  .map(
                      memberNaming -> {
                        FieldSignature fieldSignature =
                            memberNaming.getOriginalSignature().asFieldSignature();
                        RetracedClassImpl holder =
                            fieldSignature.isQualified()
                                ? RetracedClassImpl.create(
                                    Reference.classFromDescriptor(
                                        DescriptorUtils.javaTypeToDescriptor(
                                            fieldSignature.toHolderFromQualified())))
                                : classElement.getRetracedClass();
                        return new ElementImpl(
                            this,
                            classElement,
                            RetracedFieldImpl.create(
                                Reference.field(
                                    holder.getClassReference(),
                                    fieldSignature.isQualified()
                                        ? fieldSignature.toUnqualifiedName()
                                        : fieldSignature.name,
                                    Reference.typeFromTypeName(fieldSignature.type))));
                      });
            });
  }

  @Override
  public RetraceFieldResultImpl forEach(Consumer<Element> resultConsumer) {
    stream().forEach(resultConsumer);
    return this;
  }

  @Override
  public boolean isAmbiguous() {
    if (memberNamings.size() > 1) {
      return true;
    }
    List<MemberNaming> mappings = memberNamings.get(0).getSecond();
    if (mappings == null) {
      return false;
    }
    return mappings.size() > 1;
  }

  public static class ElementImpl implements RetraceFieldResult.Element {

    private final RetracedFieldImpl fieldReference;
    private final RetraceFieldResultImpl retraceFieldResult;
    private final RetraceClassResultImpl.ElementImpl classElement;

    private ElementImpl(
        RetraceFieldResultImpl retraceFieldResult,
        RetraceClassResultImpl.ElementImpl classElement,
        RetracedFieldImpl fieldReference) {
      this.classElement = classElement;
      this.fieldReference = fieldReference;
      this.retraceFieldResult = retraceFieldResult;
    }

    @Override
    public boolean isUnknown() {
      return fieldReference.isUnknown();
    }

    @Override
    public RetracedFieldImpl getField() {
      return fieldReference;
    }

    @Override
    public RetraceFieldResultImpl getRetraceFieldResult() {
      return retraceFieldResult;
    }

    @Override
    public RetraceClassResultImpl.ElementImpl getClassElement() {
      return classElement;
    }

    @Override
    public RetraceSourceFileResult retraceSourceFile(String sourceFile) {
      return RetraceUtils.getSourceFile(
          classElement, fieldReference.getHolderClass(), sourceFile, retraceFieldResult.retracer);
    }
  }
}
