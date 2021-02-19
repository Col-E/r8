// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.diagnostic.internal;

import static com.android.tools.r8.utils.ClassReferenceUtils.getClassReferenceComparator;
import static com.android.tools.r8.utils.FieldReferenceUtils.getFieldReferenceComparator;
import static com.android.tools.r8.utils.MethodReferenceUtils.getMethodReferenceComparator;

import com.android.tools.r8.diagnostic.MissingDefinitionContext;
import com.android.tools.r8.diagnostic.MissingDefinitionInfo;
import com.android.tools.r8.diagnostic.MissingDefinitionsDiagnostic;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.FieldReferenceUtils;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class MissingDefinitionsDiagnosticImpl implements MissingDefinitionsDiagnostic {

  private final Collection<MissingDefinitionInfo> missingDefinitions;

  private MissingDefinitionsDiagnosticImpl(Collection<MissingDefinitionInfo> missingDefinitions) {
    assert !missingDefinitions.isEmpty();
    this.missingDefinitions = missingDefinitions;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public Collection<MissingDefinitionInfo> getMissingDefinitions() {
    return missingDefinitions;
  }

  private Collection<MissingDefinitionInfo> getMissingDefinitionsWithDeterministicOrder() {
    List<MissingDefinitionInfo> missingDefinitionsWithDeterministicOrder =
        new ArrayList<>(getMissingDefinitions());
    missingDefinitionsWithDeterministicOrder.sort(MissingDefinitionInfoUtils.getComparator());
    return missingDefinitionsWithDeterministicOrder;
  }

  /** A missing class(es) failure can generally not be attributed to a single origin. */
  @Override
  public Origin getOrigin() {
    return Origin.unknown();
  }

  /** A missing class(es) failure can generally not be attributed to a single position. */
  @Override
  public Position getPosition() {
    return Position.UNKNOWN;
  }

  @Override
  public String getDiagnosticMessage() {
    StringBuilder builder = new StringBuilder();
    Iterator<MissingDefinitionInfo> missingDefinitionsIterator =
        getMissingDefinitionsWithDeterministicOrder().iterator();

    // The diagnostic is always non-empty.
    assert missingDefinitionsIterator.hasNext();

    // Write first line.
    writeMissingDefinition(builder.append("Missing class "), missingDefinitionsIterator.next());

    // Write remaining lines with line separator before.
    missingDefinitionsIterator.forEachRemaining(
        missingDefinition ->
            writeMissingDefinition(
                builder.append(System.lineSeparator()).append("Missing class "),
                missingDefinition));

    return builder.toString();
  }

  private static void writeMissingDefinition(
      StringBuilder builder, MissingDefinitionInfo missingDefinitionInfo) {
    missingDefinitionInfo.getMissingDefinition(
        classReference -> builder.append(classReference.getTypeName()),
        fieldReference -> builder.append(FieldReferenceUtils.toSourceString(fieldReference)),
        methodReference -> builder.append(MethodReferenceUtils.toSourceString(methodReference)));
    writeReferencedFromSuffix(builder, missingDefinitionInfo);
  }

  private static void writeReferencedFromSuffix(
      StringBuilder builder, MissingDefinitionInfo missingDefinitionInfo) {
    Box<ClassReference> classContext = new Box<>();
    Box<FieldReference> fieldContext = new Box<>();
    Box<MethodReference> methodContext = new Box<>();
    for (MissingDefinitionContext missingDefinitionContext :
        missingDefinitionInfo.getReferencedFromContexts()) {
      missingDefinitionContext.getReference(
          classReference -> classContext.setMin(classReference, getClassReferenceComparator()),
          fieldReference -> fieldContext.setMin(fieldReference, getFieldReferenceComparator()),
          methodReference -> methodContext.setMin(methodReference, getMethodReferenceComparator()));
    }
    if (fieldContext.isSet()) {
      writeReferencedFromSuffix(
          builder, missingDefinitionInfo, FieldReferenceUtils.toSourceString(fieldContext.get()));
    } else if (methodContext.isSet()) {
      writeReferencedFromSuffix(
          builder, missingDefinitionInfo, MethodReferenceUtils.toSourceString(methodContext.get()));
    } else if (classContext.isSet()) {
      writeReferencedFromSuffix(builder, missingDefinitionInfo, classContext.get().getTypeName());
    } else {
      // TODO(b/175543745): Once legacy reporting is removed this should never happen.
      builder.append(" (referenced from: <not known>)");
    }
  }

  private static void writeReferencedFromSuffix(
      StringBuilder builder, MissingDefinitionInfo missingDefinitionInfo, String referencedFrom) {
    int numberOfOtherContexts = missingDefinitionInfo.getReferencedFromContexts().size() - 1;
    assert numberOfOtherContexts >= 0;
    builder.append(" (referenced from: ").append(referencedFrom);
    if (numberOfOtherContexts >= 1) {
      builder.append(", and ").append(numberOfOtherContexts).append(" other context");
      if (numberOfOtherContexts >= 2) {
        builder.append("s");
      }
    }
    builder.append(")");
  }

  public static class Builder {

    private ImmutableList.Builder<MissingDefinitionInfo> missingDefinitionsBuilder =
        ImmutableList.builder();

    private Builder() {}

    public Builder addMissingDefinitionInfo(MissingDefinitionInfo missingDefinition) {
      missingDefinitionsBuilder.add(missingDefinition);
      return this;
    }

    public MissingDefinitionsDiagnostic build() {
      return new MissingDefinitionsDiagnosticImpl(missingDefinitionsBuilder.build());
    }
  }
}
