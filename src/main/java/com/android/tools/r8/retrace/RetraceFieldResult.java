// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.Keep;
import com.android.tools.r8.naming.MemberNaming;
import com.android.tools.r8.naming.MemberNaming.FieldSignature;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.Pair;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Keep
public class RetraceFieldResult extends Result<RetraceFieldResult.Element, RetraceFieldResult> {

  private final RetraceClassResult classResult;
  private final List<Pair<RetraceClassResult.Element, List<MemberNaming>>> memberNamings;
  private final FieldDefinition fieldDefinition;
  private final RetraceApi retracer;

  RetraceFieldResult(
      RetraceClassResult classResult,
      List<Pair<RetraceClassResult.Element, List<MemberNaming>>> memberNamings,
      FieldDefinition fieldDefinition,
      RetraceApi retracer) {
    this.classResult = classResult;
    this.memberNamings = memberNamings;
    this.fieldDefinition = fieldDefinition;
    this.retracer = retracer;
    assert classResult != null;
    assert !memberNamings.isEmpty();
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

  @Override
  public Stream<Element> stream() {
    return memberNamings.stream()
        .flatMap(
            mappedNamePair -> {
              RetraceClassResult.Element classElement = mappedNamePair.getFirst();
              List<MemberNaming> memberNamings = mappedNamePair.getSecond();
              if (memberNamings == null) {
                return Stream.of(
                    new RetraceFieldResult.Element(
                        this,
                        classElement,
                        RetracedField.create(
                            fieldDefinition.substituteHolder(
                                classElement.getRetracedClass().getClassReference()))));
              }
              return memberNamings.stream()
                  .map(
                      memberNaming -> {
                        FieldSignature fieldSignature =
                            memberNaming.getOriginalSignature().asFieldSignature();
                        RetracedClass holder =
                            fieldSignature.isQualified()
                                ? RetracedClass.create(
                                    Reference.classFromDescriptor(
                                        DescriptorUtils.javaTypeToDescriptor(
                                            fieldSignature.toHolderFromQualified())))
                                : classElement.getRetracedClass();
                        return new Element(
                            this,
                            classElement,
                            RetracedField.create(
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
  public RetraceFieldResult forEach(Consumer<Element> resultConsumer) {
    stream().forEach(resultConsumer);
    return this;
  }

  public static class Element {

    private final RetracedField fieldReference;
    private final RetraceFieldResult retraceFieldResult;
    private final RetraceClassResult.Element classElement;

    private Element(
        RetraceFieldResult retraceFieldResult,
        RetraceClassResult.Element classElement,
        RetracedField fieldReference) {
      this.classElement = classElement;
      this.fieldReference = fieldReference;
      this.retraceFieldResult = retraceFieldResult;
    }

    public boolean isUnknown() {
      return fieldReference.isUnknown();
    }

    public RetracedField getField() {
      return fieldReference;
    }

    public RetraceFieldResult getRetraceFieldResult() {
      return retraceFieldResult;
    }

    public RetraceClassResult.Element getClassElement() {
      return classElement;
    }
  }
}
