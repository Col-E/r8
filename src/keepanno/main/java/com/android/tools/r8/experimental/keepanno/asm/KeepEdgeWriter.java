// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.experimental.keepanno.asm;

import com.android.tools.r8.experimental.keepanno.annotations.KeepConstants;
import com.android.tools.r8.experimental.keepanno.annotations.KeepConstants.Edge;
import com.android.tools.r8.experimental.keepanno.ast.KeepConsequences;
import com.android.tools.r8.experimental.keepanno.ast.KeepEdge;
import com.android.tools.r8.experimental.keepanno.ast.KeepPreconditions;
import com.android.tools.r8.experimental.keepanno.ast.KeepQualifiedClassNamePattern;
import com.android.tools.r8.experimental.keepanno.utils.Unimplemented;
import java.util.function.BiFunction;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class KeepEdgeWriter implements Opcodes {

  public static void writeEdge(KeepEdge edge, ClassVisitor visitor) {
    writeEdge(edge, visitor::visitAnnotation);
  }

  public static void writeEdge(
      KeepEdge edge, BiFunction<String, Boolean, AnnotationVisitor> getVisitor) {
    new KeepEdgeWriter().writeEdge(edge, getVisitor.apply(Edge.DESCRIPTOR, false));
  }

  private void writeEdge(KeepEdge edge, AnnotationVisitor visitor) {
    writePreconditions(visitor, edge.getPreconditions());
    writeConsequences(visitor, edge.getConsequences());
    visitor.visitEnd();
  }

  private void writePreconditions(AnnotationVisitor visitor, KeepPreconditions preconditions) {
    if (preconditions.isAlways()) {
      return;
    }
    throw new Unimplemented();
  }

  private void writeConsequences(AnnotationVisitor visitor, KeepConsequences consequences) {
    assert !consequences.isEmpty();
    String ignoredArrayValueName = null;
    AnnotationVisitor arrayVisitor = visitor.visitArray(KeepConstants.Edge.consequences);
    consequences.forEachTarget(
        target -> {
          AnnotationVisitor targetVisitor =
              arrayVisitor.visitAnnotation(ignoredArrayValueName, KeepConstants.Target.DESCRIPTOR);
          // No options imply keep all.
          if (!target.getOptions().isKeepAll()) {
            throw new Unimplemented();
          }
          target
              .getItem()
              .match(
                  () -> {
                    throw new Unimplemented();
                  },
                  clazz -> {
                    KeepQualifiedClassNamePattern namePattern = clazz.getClassNamePattern();
                    if (namePattern.isExact()) {
                      Type typeConstant = Type.getType(namePattern.getExactDescriptor());
                      targetVisitor.visit(KeepConstants.Target.classConstant, typeConstant);
                    } else {
                      throw new Unimplemented();
                    }
                    return null;
                  });
          targetVisitor.visitEnd();
        });
    arrayVisitor.visitEnd();
  }
}
