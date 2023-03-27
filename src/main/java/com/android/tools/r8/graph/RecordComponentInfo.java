// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.graph.GenericSignature.FieldTypeSignature;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.utils.structural.StructuralItem;
import com.android.tools.r8.utils.structural.StructuralMapping;
import com.android.tools.r8.utils.structural.StructuralSpecification;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.RecordComponentVisitor;

public class RecordComponentInfo implements StructuralItem<RecordComponentInfo> {

  private final DexField field;
  private final FieldTypeSignature signature;
  private final List<DexAnnotation> annotations;

  private static void specify(StructuralSpecification<RecordComponentInfo, ?> spec) {
    spec.withItem(RecordComponentInfo::getName).withItem(RecordComponentInfo::getType);
  }

  public RecordComponentInfo(
      DexField field, FieldTypeSignature signature, List<DexAnnotation> annotations) {
    assert field != null;
    assert signature != null;
    assert annotations != null;
    this.field = field;
    this.signature = signature;
    this.annotations = annotations;
  }

  public static List<RecordComponentInfo> emptyList() {
    return Collections.emptyList();
  }

  public DexField getField() {
    return field;
  }

  public DexString getName() {
    return field.getName();
  }

  public DexType getType() {
    return field.getType();
  }

  public FieldTypeSignature getSignature() {
    return signature;
  }

  public List<DexAnnotation> getAnnotations() {
    return annotations;
  }

  public void write(
      ClassWriter writer,
      NamingLens lens,
      Predicate<DexType> isTypeMissing,
      BiConsumer<AnnotationVisitor, DexEncodedAnnotation> annotationWriter) {
    RecordComponentVisitor v =
        writer.visitRecordComponent(
            lens.lookupName(field).toString(),
            lens.lookupDescriptor(getType()).toString(),
            signature.toRenamedString(lens, isTypeMissing));
    for (DexAnnotation annotation : annotations) {
      if (annotation.visibility == DexAnnotation.VISIBILITY_SYSTEM) {
        // Annotations with VISIBILITY_SYSTEM are not annotations in CF, but are special
        // annotations in DEX, i.e. default, enclosing class, enclosing method, member classes,
        // signature, throws.
        continue;
      }
      String desc = lens.lookupDescriptor(annotation.getAnnotationType()).toString();
      boolean visible = annotation.visibility == DexAnnotation.VISIBILITY_RUNTIME;
      AnnotationVisitor av = v.visitAnnotation(desc, visible);
      if (av != null) {
        annotationWriter.accept(av, annotation.annotation);
        av.visitEnd();
      }
    }
    v.visitEnd();
  }

  @Override
  public RecordComponentInfo self() {
    return this;
  }

  @Override
  public StructuralMapping<RecordComponentInfo> getStructuralMapping() {
    return RecordComponentInfo::specify;
  }
}
