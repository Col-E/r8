// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.processor;

import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_SUPER;

import com.android.tools.r8.keepanno.annotations.KeepConstants;
import com.android.tools.r8.keepanno.annotations.KeepConstants.Edge;
import com.android.tools.r8.keepanno.annotations.KeepConstants.Target;
import com.android.tools.r8.keepanno.asm.KeepEdgeReader;
import com.android.tools.r8.keepanno.asm.KeepEdgeWriter;
import com.android.tools.r8.keepanno.ast.KeepConsequences;
import com.android.tools.r8.keepanno.ast.KeepEdge;
import com.android.tools.r8.keepanno.ast.KeepEdge.Builder;
import com.android.tools.r8.keepanno.ast.KeepItemPattern;
import com.android.tools.r8.keepanno.ast.KeepMethodNamePattern;
import com.android.tools.r8.keepanno.ast.KeepMethodPattern;
import com.android.tools.r8.keepanno.ast.KeepQualifiedClassNamePattern;
import com.android.tools.r8.keepanno.ast.KeepTarget;
import com.android.tools.r8.keepanno.utils.Unimplemented;
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
import javax.lang.model.util.SimpleAnnotationValueVisitor7;
import javax.lang.model.util.SimpleTypeVisitor7;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileObject;
import org.objectweb.asm.ClassWriter;

@SupportedAnnotationTypes("com.android.tools.r8.keepanno.annotations.*")
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class KeepEdgeProcessor extends AbstractProcessor {

  public static String getClassTypeNameForSynthesizedEdges(String classTypeName) {
    return classTypeName + "$$KeepEdges";
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (Element rootElement : roundEnv.getRootElements()) {
      TypeElement typeElement = getEnclosingTypeElement(rootElement);
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

  private static byte[] writeEdge(KeepEdge edge, String classTypeName) {
    String classBinaryName = KeepConstants.getBinaryNameFromClassTypeName(classTypeName);
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
    AnnotationValue preconditions = getAnnotationValue(mirror, Edge.preconditions);
    if (preconditions == null) {
      return;
    }
    throw new Unimplemented();
  }

  private void processConsequences(Builder edgeBuilder, AnnotationMirror mirror) {
    AnnotationValue consequences = getAnnotationValue(mirror, Edge.consequences);
    if (consequences == null) {
      return;
    }
    KeepConsequences.Builder consequencesBuilder = KeepConsequences.builder();
    new AnnotationListValueVisitor(
            value -> {
              KeepTarget.Builder targetBuilder = KeepTarget.builder();
              processTarget(targetBuilder, AnnotationMirrorValueVisitor.getMirror(value));
              consequencesBuilder.addTarget(targetBuilder.build());
            })
        .onValue(consequences);
    edgeBuilder.setConsequences(consequencesBuilder.build());
  }

  private void processTarget(KeepTarget.Builder builder, AnnotationMirror mirror) {
    KeepItemPattern.Builder itemBuilder = KeepItemPattern.builder();
    AnnotationValue classConstantValue = getAnnotationValue(mirror, Target.classConstant);
    if (classConstantValue != null) {
      DeclaredType type = AnnotationClassValueVisitor.getType(classConstantValue);
      itemBuilder.setClassPattern(KeepQualifiedClassNamePattern.exact(type.toString()));
    }
    AnnotationValue methodNameValue = getAnnotationValue(mirror, Target.methodName);
    if (methodNameValue != null) {
      String methodName = AnnotationStringValueVisitor.getString(methodNameValue);
      itemBuilder.setMembersPattern(
          KeepMethodPattern.builder()
              .setNamePattern(KeepMethodNamePattern.exact(methodName))
              .build());
    }

    builder.setItem(itemBuilder.build());
  }

  private void error(String message) {
    processingEnv.getMessager().printMessage(Kind.ERROR, message);
  }

  private static TypeElement getEnclosingTypeElement(Element element) {
    while (true) {
      if (element == null || element instanceof TypeElement) {
        return (TypeElement) element;
      }
      element = element.getEnclosingElement();
    }
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

  /// Annotation Visitors

  private abstract static class AnnotationValueVisitorBase<T>
      extends SimpleAnnotationValueVisitor7<T, Object> {
    @Override
    protected T defaultAction(Object o1, Object o2) {
      throw new IllegalStateException();
    }

    public T onValue(AnnotationValue value) {
      return value.accept(this, null);
    }
  }

  private static class AnnotationListValueVisitor
      extends AnnotationValueVisitorBase<AnnotationListValueVisitor> {

    private final Consumer<AnnotationValue> fn;

    public AnnotationListValueVisitor(Consumer<AnnotationValue> fn) {
      this.fn = fn;
    }

    @Override
    public AnnotationListValueVisitor visitArray(
        List<? extends AnnotationValue> values, Object ignore) {
      values.forEach(fn);
      return this;
    }
  }

  private static class AnnotationMirrorValueVisitor
      extends AnnotationValueVisitorBase<AnnotationMirrorValueVisitor> {

    private AnnotationMirror mirror = null;

    public static AnnotationMirror getMirror(AnnotationValue value) {
      return new AnnotationMirrorValueVisitor().onValue(value).mirror;
    }

    @Override
    public AnnotationMirrorValueVisitor visitAnnotation(AnnotationMirror mirror, Object o) {
      this.mirror = mirror;
      return this;
    }
  }

  private static class AnnotationStringValueVisitor
      extends AnnotationValueVisitorBase<AnnotationStringValueVisitor> {
    private String string;

    public static String getString(AnnotationValue value) {
      return new AnnotationStringValueVisitor().onValue(value).string;
    }

    @Override
    public AnnotationStringValueVisitor visitString(String string, Object ignore) {
      this.string = string;
      return this;
    }
  }

  private static class AnnotationClassValueVisitor
      extends AnnotationValueVisitorBase<AnnotationClassValueVisitor> {
    private DeclaredType type = null;

    public static DeclaredType getType(AnnotationValue value) {
      return new AnnotationClassValueVisitor().onValue(value).type;
    }

    @Override
    public AnnotationClassValueVisitor visitType(TypeMirror t, Object ignore) {
      ClassTypeVisitor classTypeVisitor = new ClassTypeVisitor();
      t.accept(classTypeVisitor, null);
      type = classTypeVisitor.type;
      return this;
    }
  }

  private static class TypeVisitorBase<T> extends SimpleTypeVisitor7<T, Object> {
    @Override
    protected T defaultAction(TypeMirror typeMirror, Object ignore) {
      throw new IllegalStateException();
    }
  }

  private static class ClassTypeVisitor extends TypeVisitorBase<ClassTypeVisitor> {
    private DeclaredType type = null;

    @Override
    public ClassTypeVisitor visitDeclared(DeclaredType t, Object ignore) {
      this.type = t;
      return this;
    }
  }
}
