// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexAnnotationElement;
import com.android.tools.r8.graph.DexEncodedAnnotation;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.DexValue.DexItemBasedValueString;
import com.android.tools.r8.graph.DexValue.DexValueAnnotation;
import com.android.tools.r8.graph.DexValue.DexValueArray;
import com.android.tools.r8.graph.DexValue.DexValueEnum;
import com.android.tools.r8.graph.DexValue.DexValueType;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.utils.ArrayUtils;

public class AnnotationFixer {

  private final GraphLens lens;
  private final GraphLens annotationLens;

  public AnnotationFixer(GraphLens lens, GraphLens annotationLens) {
    this.lens = lens;
    this.annotationLens = annotationLens;
  }

  private DexType lookupType(DexType type) {
    return lens.lookupType(type, annotationLens);
  }

  public void run(Iterable<DexProgramClass> classes) {
    for (DexProgramClass clazz : classes) {
      clazz.setAnnotations(clazz.annotations().rewrite(this::rewriteAnnotation));
      clazz.forEachMethod(this::processMethod);
      clazz.forEachField(this::processField);
    }
  }

  private void processMethod(DexEncodedMethod method) {
    method.rewriteAllAnnotations(
        (annotation, isParameterAnnotation) -> rewriteAnnotation(annotation));
  }

  private void processField(DexEncodedField field) {
    field.setAnnotations(field.annotations().rewrite(this::rewriteAnnotation));
  }

  private DexAnnotation rewriteAnnotation(DexAnnotation original) {
    return original.rewrite(this::rewriteEncodedAnnotation);
  }

  private DexEncodedAnnotation rewriteEncodedAnnotation(DexEncodedAnnotation original) {
    DexEncodedAnnotation rewritten =
        original.rewrite(this::lookupType, this::rewriteAnnotationElement);
    assert rewritten != null;
    return rewritten;
  }

  @SuppressWarnings("ReferenceEquality")
  private DexAnnotationElement rewriteAnnotationElement(DexAnnotationElement original) {
    DexValue rewrittenValue = rewriteComplexValue(original.value);
    if (rewrittenValue != original.value) {
      return new DexAnnotationElement(original.name, rewrittenValue);
    }
    return original;
  }

  @SuppressWarnings("ReferenceEquality")
  private DexValue rewriteComplexValue(DexValue value) {
    if (value.isDexValueArray()) {
      DexValue[] originalValues = value.asDexValueArray().getValues();
      DexValue[] rewrittenValues =
          ArrayUtils.map(originalValues, this::rewriteComplexValue, DexValue.EMPTY_ARRAY);
      if (rewrittenValues != originalValues) {
        return new DexValueArray(rewrittenValues);
      }
    } else if (value.isDexValueAnnotation()) {
      DexValueAnnotation original = value.asDexValueAnnotation();
      DexEncodedAnnotation rewritten = rewriteEncodedAnnotation(original.getValue());
      if (original.value == rewritten) {
        return value;
      }
      return new DexValueAnnotation(rewritten);
    }
    return rewriteNestedValue(value);
  }

  @SuppressWarnings("ReferenceEquality")
  private DexValue rewriteNestedValue(DexValue value) {
    if (value.isDexItemBasedValueString()) {
      DexItemBasedValueString valueString = value.asDexItemBasedValueString();
      DexReference original = valueString.value;
      DexReference rewritten = lens.getRenamedReference(original, annotationLens);
      if (original != rewritten) {
        return new DexItemBasedValueString(rewritten, valueString.getNameComputationInfo());
      }
    } else if (value.isDexValueEnum()) {
      DexField original = value.asDexValueEnum().value;
      DexField rewritten = lens.lookupField(original, annotationLens);
      if (original != rewritten) {
        return new DexValueEnum(rewritten);
      }
    } else if (value.isDexValueField()) {
      throw new Unreachable("Unexpected field in annotation");
    } else if (value.isDexValueMethod()) {
      throw new Unreachable("Unexpected method in annotation");
    } else if (value.isDexValueMethodHandle()) {
      throw new Unreachable("Unexpected method handle in annotation");
    } else if (value.isDexValueMethodType()) {
      throw new Unreachable("Unexpected method type in annotation");
    } else if (value.isDexValueString()) {
      // If we identified references in the string it would be a DexItemBasedValueString.
    } else if (value.isDexValueType()) {
      DexType originalType = value.asDexValueType().value;
      DexType rewrittenType = lookupType(originalType);
      if (rewrittenType != originalType) {
        return new DexValueType(rewrittenType);
      }
    } else {
      // Assert that we have not forgotten a value.
      assert !value.isNestedDexValue();
    }
    return value;
  }
}
