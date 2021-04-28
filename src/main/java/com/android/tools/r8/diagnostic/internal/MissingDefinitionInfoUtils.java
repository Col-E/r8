// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.diagnostic.internal;

import static com.android.tools.r8.utils.ClassReferenceUtils.getClassReferenceComparator;
import static com.android.tools.r8.utils.FieldReferenceUtils.getFieldReferenceComparator;
import static com.android.tools.r8.utils.MethodReferenceUtils.getMethodReferenceComparator;

import com.android.tools.r8.diagnostic.DefinitionContext;
import com.android.tools.r8.diagnostic.MissingClassInfo;
import com.android.tools.r8.diagnostic.MissingDefinitionInfo;
import com.android.tools.r8.diagnostic.MissingFieldInfo;
import com.android.tools.r8.diagnostic.MissingMethodInfo;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.ClassReferenceUtils;
import com.android.tools.r8.utils.FieldReferenceUtils;
import com.android.tools.r8.utils.MethodReferenceUtils;
import java.util.Comparator;
import java.util.function.Consumer;

public class MissingDefinitionInfoUtils {

  private static final Comparator<MissingDefinitionInfo> COMPARATOR =
      (info, other) -> {
        if (info.isMissingClass()) {
          ClassReference classReference = info.asMissingClass().getClassReference();
          if (other.isMissingClass()) {
            return ClassReferenceUtils.compare(
                classReference, other.asMissingClass().getClassReference());
          }
          if (other.isMissingField()) {
            return ClassReferenceUtils.compare(
                classReference, other.asMissingField().getFieldReference());
          }
          return ClassReferenceUtils.compare(
              classReference, other.asMissingMethod().getMethodReference());
        }
        if (info.isMissingField()) {
          FieldReference fieldReference = info.asMissingField().getFieldReference();
          if (other.isMissingClass()) {
            return FieldReferenceUtils.compare(
                fieldReference, other.asMissingClass().getClassReference());
          }
          if (other.isMissingField()) {
            return FieldReferenceUtils.compare(
                fieldReference, other.asMissingField().getFieldReference());
          }
          return FieldReferenceUtils.compare(
              fieldReference, other.asMissingMethod().getMethodReference());
        }
        MethodReference methodReference = info.asMissingMethod().getMethodReference();
        if (other.isMissingClass()) {
          return MethodReferenceUtils.compare(
              methodReference, other.asMissingClass().getClassReference());
        }
        if (other.isMissingField()) {
          return MethodReferenceUtils.compare(
              methodReference, other.asMissingField().getFieldReference());
        }
        return MethodReferenceUtils.compare(
            methodReference, other.asMissingMethod().getMethodReference());
      };

  public static void accept(
      MissingDefinitionInfo missingDefinitionInfo,
      Consumer<MissingClassInfo> missingClassInfoConsumer,
      Consumer<MissingFieldInfo> missingFieldInfoConsumer,
      Consumer<MissingMethodInfo> missingMethodInfoConsumer) {
    if (missingDefinitionInfo.isMissingClass()) {
      missingClassInfoConsumer.accept(missingDefinitionInfo.asMissingClass());
    } else if (missingDefinitionInfo.isMissingField()) {
      missingFieldInfoConsumer.accept(missingDefinitionInfo.asMissingField());
    } else {
      assert missingDefinitionInfo.isMissingMethod();
      missingMethodInfoConsumer.accept(missingDefinitionInfo.asMissingMethod());
    }
  }

  public static Comparator<MissingDefinitionInfo> getComparator() {
    return COMPARATOR;
  }

  public static void writeDiagnosticMessage(
      StringBuilder builder, MissingDefinitionInfo missingDefinitionInfo) {
    MissingDefinitionInfoUtils.accept(
        missingDefinitionInfo,
        missingClassInfo ->
            builder
                .append("Missing class ")
                .append(missingClassInfo.getClassReference().getTypeName()),
        missingFieldInfo ->
            builder
                .append("Missing field ")
                .append(FieldReferenceUtils.toSourceString(missingFieldInfo.getFieldReference())),
        missingMethodInfo ->
            builder
                .append("Missing method ")
                .append(
                    MethodReferenceUtils.toSourceString(missingMethodInfo.getMethodReference())));
    writeReferencedFromSuffix(builder, missingDefinitionInfo);
  }

  private static void writeReferencedFromSuffix(
      StringBuilder builder, MissingDefinitionInfo missingDefinitionInfo) {
    Box<ClassReference> classContext = new Box<>();
    Box<FieldReference> fieldContext = new Box<>();
    Box<MethodReference> methodContext = new Box<>();
    for (DefinitionContext missingDefinitionContext :
        missingDefinitionInfo.getReferencedFromContexts()) {
      DefinitionContextUtils.accept(
          missingDefinitionContext,
          missingDefinitionClassContext ->
              classContext.setMin(
                  missingDefinitionClassContext.getClassReference(), getClassReferenceComparator()),
          missingDefinitionFieldContext ->
              fieldContext.setMin(
                  missingDefinitionFieldContext.getFieldReference(), getFieldReferenceComparator()),
          missingDefinitionMethodContext ->
              methodContext.setMin(
                  missingDefinitionMethodContext.getMethodReference(),
                  getMethodReferenceComparator()));
    }
    // TODO(b/186506586): Reenable assert once trace references also provide contextual information.
    // assert classContext.isSet() || fieldContext.isSet() || methodContext.isSet();
    if (fieldContext.isSet()) {
      writeReferencedFromSuffix(
          builder, missingDefinitionInfo, FieldReferenceUtils.toSourceString(fieldContext.get()));
    } else if (methodContext.isSet()) {
      writeReferencedFromSuffix(
          builder, missingDefinitionInfo, MethodReferenceUtils.toSourceString(methodContext.get()));
    } else if (classContext.isSet()) {
      writeReferencedFromSuffix(builder, missingDefinitionInfo, classContext.get().getTypeName());
    }
  }

  private static void writeReferencedFromSuffix(
      StringBuilder builder, MissingDefinitionInfo missingDefinitionInfo, String referencedFrom) {
    int numberOfOtherContexts = missingDefinitionInfo.getReferencedFromContexts().size() - 1;
    assert numberOfOtherContexts >= 0;
    builder.append(" (referenced from: ").append(referencedFrom);
    if (numberOfOtherContexts >= 1) {
      builder.append(" and ").append(numberOfOtherContexts).append(" other context");
      if (numberOfOtherContexts >= 2) {
        builder.append("s");
      }
    }
    builder.append(")");
  }
}
