// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.processor;

import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_SUPER;

import com.android.tools.r8.keepanno.asm.KeepEdgeReader;
import com.android.tools.r8.keepanno.asm.KeepEdgeWriter;
import com.android.tools.r8.keepanno.ast.AnnotationConstants;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.Edge;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.Item;
import com.android.tools.r8.keepanno.ast.KeepCondition;
import com.android.tools.r8.keepanno.ast.KeepConsequences;
import com.android.tools.r8.keepanno.ast.KeepEdge;
import com.android.tools.r8.keepanno.ast.KeepEdge.Builder;
import com.android.tools.r8.keepanno.ast.KeepEdgeException;
import com.android.tools.r8.keepanno.ast.KeepFieldNamePattern;
import com.android.tools.r8.keepanno.ast.KeepFieldPattern;
import com.android.tools.r8.keepanno.ast.KeepItemPattern;
import com.android.tools.r8.keepanno.ast.KeepMethodNamePattern;
import com.android.tools.r8.keepanno.ast.KeepMethodPattern;
import com.android.tools.r8.keepanno.ast.KeepPreconditions;
import com.android.tools.r8.keepanno.ast.KeepQualifiedClassNamePattern;
import com.android.tools.r8.keepanno.ast.KeepTarget;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
  @SuppressWarnings("DoNotClaimAnnotations")
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    Map<String, List<KeepEdge>> collectedEdges = new HashMap<>();
    for (TypeElement annotation : annotations) {
      for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
        KeepEdge edge = processKeepEdge(element);
        if (edge != null) {
          TypeElement enclosingType = getEnclosingTypeElement(element);
          String enclosingTypeName = enclosingType.getQualifiedName().toString();
          collectedEdges.computeIfAbsent(enclosingTypeName, k -> new ArrayList<>()).add(edge);
        }
      }
    }
    for (Entry<String, List<KeepEdge>> entry : collectedEdges.entrySet()) {
      String enclosingTypeName = entry.getKey();
      String edgeTargetClass = getClassTypeNameForSynthesizedEdges(enclosingTypeName);
      byte[] writtenEdge = writeEdges(entry.getValue(), edgeTargetClass);
      Filer filer = processingEnv.getFiler();
      try {
        JavaFileObject classFile = filer.createClassFile(edgeTargetClass);
        classFile.openOutputStream().write(writtenEdge);
      } catch (IOException e) {
        error(e.getMessage());
      }
    }
    return true;
  }

  private static byte[] writeEdges(List<KeepEdge> edges, String classTypeName) {
    String classBinaryName = AnnotationConstants.getBinaryNameFromClassTypeName(classTypeName);
    ClassWriter classWriter = new ClassWriter(0);
    classWriter.visit(
        KeepEdgeReader.ASM_VERSION,
        ACC_PUBLIC | ACC_FINAL | ACC_SUPER,
        classBinaryName,
        null,
        "java/lang/Object",
        null);
    classWriter.visitSource("SynthesizedKeepEdge", null);
    for (KeepEdge edge : edges) {
      KeepEdgeWriter.writeEdge(edge, classWriter);
    }
    classWriter.visitEnd();
    return classWriter.toByteArray();
  }

  @SuppressWarnings("BadImport")
  private KeepEdge processKeepEdge(Element element) {
    AnnotationMirror mirror = getAnnotationMirror(element, AnnotationConstants.Edge.CLASS);
    if (mirror == null) {
      return null;
    }
    Builder edgeBuilder = KeepEdge.builder();
    processPreconditions(edgeBuilder, mirror);
    processConsequences(edgeBuilder, mirror);
    return edgeBuilder.build();
  }

  @SuppressWarnings("BadImport")
  private void processPreconditions(Builder edgeBuilder, AnnotationMirror mirror) {
    AnnotationValue preconditions = getAnnotationValue(mirror, Edge.preconditions);
    if (preconditions == null) {
      return;
    }
    KeepPreconditions.Builder preconditionsBuilder = KeepPreconditions.builder();
    new AnnotationListValueVisitor(
            value -> {
              KeepCondition.Builder conditionBuilder = KeepCondition.builder();
              processCondition(conditionBuilder, AnnotationMirrorValueVisitor.getMirror(value));
              preconditionsBuilder.addCondition(conditionBuilder.build());
            })
        .onValue(preconditions);
    edgeBuilder.setPreconditions(preconditionsBuilder.build());
  }

  @SuppressWarnings("BadImport")
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

  private String getTypeNameForClassConstantElement(DeclaredType type) {
    // The processor API does not expose the descriptor or typename, so we need to depend on the
    // sun.tools internals to extract it. If not, this code will not work for inner classes as
    // we cannot recover the $ separator.
    try {
      Object tsym = type.getClass().getField("tsym").get(type);
      Object flatname = tsym.getClass().getField("flatname").get(tsym);
      return flatname.toString();
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new KeepEdgeException("Unable to obtain the class type name for: " + type);
    }
  }

  private void processCondition(KeepCondition.Builder builder, AnnotationMirror mirror) {
    KeepItemPattern.Builder itemBuilder = KeepItemPattern.builder();
    processItem(itemBuilder, mirror);
    builder.setItemPattern(itemBuilder.build());
  }

  private void processTarget(KeepTarget.Builder builder, AnnotationMirror mirror) {
    KeepItemPattern.Builder itemBuilder = KeepItemPattern.builder();
    processItem(itemBuilder, mirror);
    builder.setItemPattern(itemBuilder.build());
  }

  private void processItem(KeepItemPattern.Builder builder, AnnotationMirror mirror) {
    AnnotationValue classConstantValue = getAnnotationValue(mirror, Item.classConstant);
    if (classConstantValue != null) {
      DeclaredType type = AnnotationClassValueVisitor.getType(classConstantValue);
      String typeName = getTypeNameForClassConstantElement(type);
      builder.setClassPattern(KeepQualifiedClassNamePattern.exact(typeName));
    }
    AnnotationValue methodNameValue = getAnnotationValue(mirror, Item.methodName);
    AnnotationValue fieldNameValue = getAnnotationValue(mirror, Item.fieldName);
    if (methodNameValue != null && fieldNameValue != null) {
      throw new KeepEdgeException("Cannot define both a method and a field name pattern");
    }
    if (methodNameValue != null) {
      String methodName = AnnotationStringValueVisitor.getString(methodNameValue);
      builder.setMemberPattern(
          KeepMethodPattern.builder()
              .setNamePattern(KeepMethodNamePattern.exact(methodName))
              .build());
    } else if (fieldNameValue != null) {
      String fieldName = AnnotationStringValueVisitor.getString(fieldNameValue);
      builder.setMemberPattern(
          KeepFieldPattern.builder().setNamePattern(KeepFieldNamePattern.exact(fieldName)).build());
    }
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

  private static AnnotationMirror getAnnotationMirror(Element element, Class<?> clazz) {
    String clazzName = clazz.getName();
    for (AnnotationMirror m : element.getAnnotationMirrors()) {
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
