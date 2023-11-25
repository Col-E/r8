// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackaging;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexAnnotationElement;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexEncodedAnnotation;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.ParameterAnnotationsList;
import com.android.tools.r8.graph.lens.GraphLens;

public class RepackagingAnnotationTracer {

  private final GraphLens graphLens;
  private final RepackagingUseRegistry registry;

  public RepackagingAnnotationTracer(
      AppView<? extends AppInfoWithClassHierarchy> appView, RepackagingUseRegistry registry) {
    this.graphLens = appView.graphLens();
    this.registry = registry;
  }

  public void trace(DexAnnotationSet annotations) {
    annotations.forEach(this::traceAnnotation);
  }

  public void trace(ParameterAnnotationsList annotations) {
    annotations.forEachAnnotation(this::traceAnnotation);
  }

  private void traceAnnotation(DexAnnotation annotation) {
    traceEncodedAnnotation(annotation.annotation);
  }

  private void traceEncodedAnnotation(DexEncodedAnnotation annotation) {
    registry.registerTypeReference(annotation.type, graphLens);
    annotation.forEachElement(this::traceAnnotationElement);
  }

  private void traceAnnotationElement(DexAnnotationElement element) {
    traceDexValue(element.value);
  }

  private void traceDexValue(DexValue value) {
    switch (value.getValueKind()) {
      case BOOLEAN:
      case BYTE:
      case CHAR:
      case DOUBLE:
      case FLOAT:
      case INT:
      case LONG:
      case NULL:
      case SHORT:
      case STRING:
        break;

      case ANNOTATION:
        traceEncodedAnnotation(value.asDexValueAnnotation().getValue());
        break;

      case ARRAY:
        value.asDexValueArray().forEachElement(this::traceDexValue);
        break;

      case ENUM:
        registry.registerFieldAccess(value.asDexValueEnum().getValue());
        break;

      case FIELD:
        registry.registerFieldAccess(value.asDexValueField().getValue());
        break;

      case METHOD:
        registry.registerMethodReference(value.asDexValueMethod().getValue());
        break;

      case METHOD_HANDLE:
        {
          DexMethodHandle handle = value.asDexValueMethodHandle().getValue();
          if (handle.isFieldHandle()) {
            registry.registerFieldAccess(handle.asField());
          } else {
            assert handle.isMethodHandle();
            registry.registerMethodReference(handle.asMethod());
          }
        }
        break;

      case METHOD_TYPE:
        value
            .asDexValueMethodType()
            .getValue()
            .forEachType(type -> registry.registerTypeReference(type, graphLens));
        break;

      case TYPE:
        registry.registerTypeReference(value.asDexValueType().getValue(), graphLens);
        break;

      default:
        throw new Unreachable();
    }
  }
}
