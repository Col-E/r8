// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.experimental.keepanno.processor;

import static com.android.tools.r8.dex.Constants.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_SUPER;

import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.experimental.keepanno.annotations.KeepConstants;
import com.android.tools.r8.experimental.keepanno.asm.KeepEdgeReader;
import com.android.tools.r8.experimental.keepanno.asm.KeepEdgeWriter;
import com.android.tools.r8.experimental.keepanno.ast.KeepConsequences;
import com.android.tools.r8.experimental.keepanno.ast.KeepEdge;
import com.android.tools.r8.experimental.keepanno.ast.KeepEdge.Builder;
import com.android.tools.r8.experimental.keepanno.ast.KeepItemPattern;
import com.android.tools.r8.experimental.keepanno.ast.KeepQualifiedClassNamePattern;
import com.android.tools.r8.experimental.keepanno.ast.KeepTarget;
import com.android.tools.r8.utils.DescriptorUtils;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleAnnotationValueVisitor9;
import javax.lang.model.util.SimpleTypeVisitor9;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileObject;
import org.objectweb.asm.ClassWriter;

@SupportedAnnotationTypes("com.android.tools.r8.experimental.keepanno.annotations.*")
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class KeepEdgeProcessor extends AbstractProcessor {

  public static String getClassTypeNameForSynthesizedEdges(String classTypeName) {
    return classTypeName + "$$KeepEdges";
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (Element rootElement : roundEnv.getRootElements()) {
      TypeElement typeElement = findEnclosingTypeElement(rootElement);
      KeepEdge edge = processKeepEdge(typeElement, roundEnv);
      if (edge != null) {
        String edgeTargetClass =
            getClassTypeNameForSynthesizedEdges(typeElement.getQualifiedName().toString());
        byte[] writtenEdge = writeEdge(edge, edgeTargetClass);
        Filer filer = processingEnv.getFiler();
        try {
          JavaFileObject classFile = filer.createClassFile(edgeTargetClass);
          classFile.openOutputStream().write(writtenEdge);
        } catch (IOException e) {
          error(e.getMessage());
        }
      }
    }
    return true;
  }

  private static TypeElement findEnclosingTypeElement(Element element) {
    while (true) {
      if (element == null || element instanceof TypeElement) {
        return (TypeElement) element;
      }
      element = element.getEnclosingElement();
    }
  }

  private static byte[] writeEdge(KeepEdge edge, String classTypeName) {
    String classBinaryName = DescriptorUtils.getBinaryNameFromJavaType(classTypeName);
    ClassWriter classWriter = new ClassWriter(0);
    classWriter.visit(
        KeepEdgeReader.ASM_VERSION,
        ACC_PUBLIC | ACC_FINAL | ACC_SUPER,
        classBinaryName,
        null,
        "java/lang/Object",
        null);
    classWriter.visitSource("SynthesizedKeepEdge", null);
    KeepEdgeWriter.writeEdge(edge, classWriter);
    classWriter.visitEnd();
    return classWriter.toByteArray();
  }

  private KeepEdge processKeepEdge(TypeElement keepEdge, RoundEnvironment roundEnv) {
    AnnotationMirror mirror = getAnnotationMirror(keepEdge, KeepConstants.Edge.CLASS);
    if (mirror == null) {
      return null;
    }
    Builder edgeBuilder = KeepEdge.builder();
    processPreconditions(edgeBuilder, mirror);
    processConsequences(edgeBuilder, mirror);
    return edgeBuilder.build();
  }

  private void processPreconditions(Builder edgeBuilder, AnnotationMirror mirror) {
    AnnotationValue preconditions = getAnnotationValue(mirror, "preconditions");
    if (preconditions == null) {
      return;
    }
    throw new Unimplemented();
  }

  private void processConsequences(Builder edgeBuilder, AnnotationMirror mirror) {
    AnnotationValue consequences = getAnnotationValue(mirror, "consequences");
    if (consequences == null) {
      return;
    }
    KeepConsequences.Builder consequencesBuilder = KeepConsequences.builder();
    AnnotationListValueVisitor v =
        new AnnotationListValueVisitor(
            value -> {
              KeepTarget.Builder targetBuilder = KeepTarget.builder();
              processTarget(targetBuilder, getAnnotationMirror(value, KeepConstants.Target.CLASS));
              consequencesBuilder.addTarget(targetBuilder.build());
            });
    consequences.accept(v, null);
    edgeBuilder.setConsequences(consequencesBuilder.build());
  }

  private void processTarget(KeepTarget.Builder builder, AnnotationMirror mirror) {
    KeepItemPattern.Builder itemBuilder = KeepItemPattern.builder();
    AnnotationValue classConstantValue = getAnnotationValue(mirror, "classConstant");
    if (classConstantValue != null) {
      AnnotationClassValueVisitor v = new AnnotationClassValueVisitor();
      classConstantValue.accept(v, null);
      itemBuilder.setClassPattern(KeepQualifiedClassNamePattern.exact(v.type.toString()));
    }
    builder.setItem(itemBuilder.build());
  }

  public static class AnnotationListValueVisitor
      extends SimpleAnnotationValueVisitor9<AnnotationListValueVisitor, Object> {

    private final Consumer<AnnotationValue> fn;

    public AnnotationListValueVisitor(Consumer<AnnotationValue> fn) {
      this.fn = fn;
    }

    @Override
    protected AnnotationListValueVisitor defaultAction(Object o, Object o2) {
      throw new IllegalStateException();
    }

    @Override
    public AnnotationListValueVisitor visitArray(List<? extends AnnotationValue> vals, Object o) {
      vals.forEach(fn::accept);
      return this;
    }
  }

  public static class AnnotationMirrorValueVisitor
      extends SimpleAnnotationValueVisitor9<AnnotationMirrorValueVisitor, Object> {

    private AnnotationMirror mirror = null;

    @Override
    protected AnnotationMirrorValueVisitor defaultAction(Object o, Object o2) {
      throw new IllegalStateException();
    }

    @Override
    public AnnotationMirrorValueVisitor visitAnnotation(AnnotationMirror a, Object o) {
      mirror = a;
      return this;
    }
  }

  public static class AnnotationClassValueVisitor
      extends SimpleAnnotationValueVisitor9<AnnotationClassValueVisitor, Object> {

    private DeclaredType type = null;

    @Override
    protected AnnotationClassValueVisitor defaultAction(Object o, Object o2) {
      throw new IllegalStateException();
    }

    @Override
    public AnnotationClassValueVisitor visitType(TypeMirror t, Object o) {
      ClassTypeVisitor classTypeVisitor = new ClassTypeVisitor();
      t.accept(classTypeVisitor, null);
      type = classTypeVisitor.t;
      return this;
    }
  }

  public static class ClassTypeVisitor extends SimpleTypeVisitor9<ClassTypeVisitor, Object> {

    DeclaredType t = null;

    @Override
    protected ClassTypeVisitor defaultAction(TypeMirror e, Object o) {
      throw new IllegalStateException();
    }

    @Override
    public ClassTypeVisitor visitDeclared(DeclaredType t, Object o) {
      this.t = t;
      return this;
    }
  }

  private void error(String message) {
    processingEnv.getMessager().printMessage(Kind.ERROR, message);
  }

  private static AnnotationMirror getAnnotationMirror(AnnotationValue value, Class<?> annoClass) {
    AnnotationMirrorValueVisitor v = new AnnotationMirrorValueVisitor();
    value.accept(v, null);
    return v.mirror;
  }

  private static AnnotationMirror getAnnotationMirror(TypeElement typeElement, Class<?> clazz) {
    String clazzName = clazz.getName();
    for (AnnotationMirror m : typeElement.getAnnotationMirrors()) {
      if (m.getAnnotationType().toString().equals(clazzName)) {
        return m;
      }
    }
    return null;
  }

  private static AnnotationValue getAnnotationValue(AnnotationMirror annotationMirror, String key) {
    for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
        annotationMirror.getElementValues().entrySet()) {
      if (entry.getKey().getSimpleName().toString().equals(key)) {
        return entry.getValue();
      }
    }
    return null;
  }
}
