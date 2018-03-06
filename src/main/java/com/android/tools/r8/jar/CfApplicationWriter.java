// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jar;

import static org.objectweb.asm.Opcodes.ASM6;

import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.dex.ApplicationWriter;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexAnnotationElement;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexEncodedAnnotation;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.DexValue.DexValueAnnotation;
import com.android.tools.r8.graph.DexValue.DexValueArray;
import com.android.tools.r8.graph.DexValue.DexValueBoolean;
import com.android.tools.r8.graph.DexValue.DexValueByte;
import com.android.tools.r8.graph.DexValue.DexValueChar;
import com.android.tools.r8.graph.DexValue.DexValueDouble;
import com.android.tools.r8.graph.DexValue.DexValueEnum;
import com.android.tools.r8.graph.DexValue.DexValueField;
import com.android.tools.r8.graph.DexValue.DexValueFloat;
import com.android.tools.r8.graph.DexValue.DexValueInt;
import com.android.tools.r8.graph.DexValue.DexValueLong;
import com.android.tools.r8.graph.DexValue.DexValueMethod;
import com.android.tools.r8.graph.DexValue.DexValueMethodHandle;
import com.android.tools.r8.graph.DexValue.DexValueMethodType;
import com.android.tools.r8.graph.DexValue.DexValueNull;
import com.android.tools.r8.graph.DexValue.DexValueShort;
import com.android.tools.r8.graph.DexValue.DexValueString;
import com.android.tools.r8.graph.DexValue.DexValueType;
import com.android.tools.r8.graph.DexValue.UnknownDexValue;
import com.android.tools.r8.graph.InnerClassAttribute;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.naming.ProguardMapSupplier;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.ExecutorService;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.CheckClassAdapter;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

public class CfApplicationWriter {
  private static final boolean RUN_VERIFIER = false;
  private static final boolean PRINT_CF = false;

  private final DexApplication application;
  private final NamingLens namingLens;
  private final InternalOptions options;

  public final ProguardMapSupplier proguardMapSupplier;
  public final String deadCode;
  public final String proguardSeedsData;

  public CfApplicationWriter(
      DexApplication application,
      InternalOptions options,
      String deadCode,
      NamingLens namingLens,
      String proguardSeedsData,
      ProguardMapSupplier proguardMapSupplier) {
    this.application = application;
    this.namingLens = namingLens;
    this.options = options;
    this.proguardMapSupplier = proguardMapSupplier;
    this.deadCode = deadCode;
    this.proguardSeedsData = proguardSeedsData;
  }

  public void write(ClassFileConsumer consumer, ExecutorService executor) throws IOException {
    application.timing.begin("CfApplicationWriter.write");
    try {
      writeApplication(consumer, executor);
    } finally {
      application.timing.end();
    }
  }

  private void writeApplication(ClassFileConsumer consumer, ExecutorService executor)
      throws IOException {
    for (DexProgramClass clazz : application.classes()) {
      if (clazz.getSynthesizedFrom().isEmpty()) {
        writeClass(clazz, consumer);
      } else {
        throw new Unimplemented("No support for synthetics in the Java bytecode backend.");
      }
    }
    ApplicationWriter.supplyAdditionalConsumers(
        application, namingLens, options, deadCode, proguardMapSupplier, proguardSeedsData);
  }

  private void writeClass(DexProgramClass clazz, ClassFileConsumer consumer) throws IOException {
    ClassWriter writer = new ClassWriter(0);
    writer.visitSource(clazz.sourceFile != null ? clazz.sourceFile.toString() : null, null);
    int version = clazz.getClassFileVersion();
    int access = clazz.accessFlags.getAsCfAccessFlags();
    String desc = clazz.type.toDescriptorString();
    String name = clazz.type.getInternalName();
    String signature = getSignature(clazz.annotations);
    String superName =
        clazz.type == options.itemFactory.objectType ? null : clazz.superType.getInternalName();
    String[] interfaces = new String[clazz.interfaces.values.length];
    for (int i = 0; i < clazz.interfaces.values.length; i++) {
      interfaces[i] = clazz.interfaces.values[i].getInternalName();
    }
    writer.visit(version, access, name, signature, superName, interfaces);
    writeAnnotations(writer::visitAnnotation, clazz.annotations.annotations);
    ImmutableMap<DexString, DexValue> defaults = getAnnotationDefaults(clazz.annotations);

    if (clazz.getEnclosingMethod() != null) {
      clazz.getEnclosingMethod().write(writer);
    }

    for (InnerClassAttribute entry : clazz.getInnerClasses()) {
      entry.write(writer);
    }

    for (DexEncodedField field : clazz.staticFields()) {
      writeField(field, writer);
    }
    for (DexEncodedField field : clazz.instanceFields()) {
      writeField(field, writer);
    }
    for (DexEncodedMethod method : clazz.directMethods()) {
      writeMethod(method, writer, defaults);
    }
    for (DexEncodedMethod method : clazz.virtualMethods()) {
      writeMethod(method, writer, defaults);
    }
    writer.visitEnd();

    byte[] result = writer.toByteArray();
    if (PRINT_CF) {
      System.out.print(printCf(result));
      System.out.flush();
    }
    if (RUN_VERIFIER) {
      // Generally, this will fail with ClassNotFoundException,
      // so don't assert that verifyCf() returns true.
      verifyCf(result);
    }
    ExceptionUtils.withConsumeResourceHandler(
        options.reporter, handler -> consumer.accept(result, desc, handler));
  }

  private DexValue getSystemAnnotationValue(DexAnnotationSet annotations, DexType type) {
    DexAnnotation annotation = annotations.getFirstMatching(type);
    if (annotation == null) {
      return null;
    }
    assert annotation.visibility == DexAnnotation.VISIBILITY_SYSTEM;
    DexEncodedAnnotation encodedAnnotation = annotation.annotation;
    assert encodedAnnotation.elements.length == 1;
    return encodedAnnotation.elements[0].value;
  }

  private String getSignature(DexAnnotationSet annotations) {
    DexValueArray value =
        (DexValueArray)
            getSystemAnnotationValue(annotations, application.dexItemFactory.annotationSignature);
    if (value == null) {
      return null;
    }
    DexValue[] parts = value.getValues();
    StringBuilder res = new StringBuilder();
    for (DexValue part : parts) {
      res.append(((DexValueString) part).getValue().toString());
    }
    return res.toString();
  }

  private ImmutableMap<DexString, DexValue> getAnnotationDefaults(DexAnnotationSet annotations) {
    DexValueAnnotation value =
        (DexValueAnnotation)
            getSystemAnnotationValue(annotations, application.dexItemFactory.annotationDefault);
    if (value == null) {
      return ImmutableMap.of();
    }
    DexEncodedAnnotation annotation = value.value;
    Builder<DexString, DexValue> builder = ImmutableMap.builder();
    for (DexAnnotationElement element : annotation.elements) {
      builder.put(element.name, element.value);
    }
    return builder.build();
  }

  private String[] getExceptions(DexAnnotationSet annotations) {
    DexValueArray value =
        (DexValueArray)
            getSystemAnnotationValue(annotations, application.dexItemFactory.annotationThrows);
    if (value == null) {
      return null;
    }
    DexValue[] values = value.getValues();
    String[] res = new String[values.length];
    for (int i = 0; i < values.length; i++) {
      res[i] = ((DexValueType) values[i]).value.getInternalName();
    }
    return res;
  }

  private Object getStaticValue(DexEncodedField field) {
    if (!field.accessFlags.isStatic() || field.staticValue == null) {
      return null;
    }
    return field.staticValue.asAsmEncodedObject();
  }

  private void writeField(DexEncodedField field, ClassWriter writer) {
    int access = field.accessFlags.getAsCfAccessFlags();
    String name = field.field.name.toString();
    String desc = field.field.type.toDescriptorString();
    String signature = getSignature(field.annotations);
    Object value = getStaticValue(field);
    FieldVisitor visitor = writer.visitField(access, name, desc, signature, value);
    writeAnnotations(visitor::visitAnnotation, field.annotations.annotations);
    visitor.visitEnd();
  }

  private void writeMethod(
      DexEncodedMethod method, ClassWriter writer, ImmutableMap<DexString, DexValue> defaults) {
    int access = method.accessFlags.getAsCfAccessFlags();
    String name = method.method.name.toString();
    String desc = method.descriptor();
    String signature = getSignature(method.annotations);
    String[] exceptions = getExceptions(method.annotations);
    MethodVisitor visitor = writer.visitMethod(access, name, desc, signature, exceptions);
    if (defaults.containsKey(method.method.name)) {
      AnnotationVisitor defaultVisitor = visitor.visitAnnotationDefault();
      if (defaultVisitor != null) {
        writeAnnotationElement(defaultVisitor, null, defaults.get(method.method.name));
        defaultVisitor.visitEnd();
      }
    }
    writeAnnotations(visitor::visitAnnotation, method.annotations.annotations);
    for (int i = 0; i < method.parameterAnnotations.values.length; i++) {
      final int iFinal = i;
      writeAnnotations(
          (d, vis) -> visitor.visitParameterAnnotation(iFinal, d, vis),
          method.parameterAnnotations.values[i].annotations);
    }
    if (!method.accessFlags.isAbstract() && !method.accessFlags.isNative()) {
      writeCode(method.getCode(), visitor);
    }
    visitor.visitEnd();
  }

  private interface AnnotationConsumer {
    AnnotationVisitor visit(String desc, boolean visible);
  }

  private void writeAnnotations(AnnotationConsumer visitor, DexAnnotation[] annotations) {
    for (DexAnnotation dexAnnotation : annotations) {
      if (dexAnnotation.visibility == DexAnnotation.VISIBILITY_SYSTEM) {
        // Annotations with VISIBILITY_SYSTEM are not annotations in CF, but are special
        // annotations in Dex, i.e. default, enclosing class, enclosing method, member classes,
        // signature, throws.
        continue;
      }
      AnnotationVisitor v =
          visitor.visit(
              dexAnnotation.annotation.type.toDescriptorString(),
              dexAnnotation.visibility == DexAnnotation.VISIBILITY_RUNTIME);
      if (v != null) {
        writeAnnotation(v, dexAnnotation.annotation);
        v.visitEnd();
      }
    }
  }

  private void writeAnnotation(AnnotationVisitor v, DexEncodedAnnotation annotation) {
    for (DexAnnotationElement element : annotation.elements) {
      writeAnnotationElement(v, element.name.toString(), element.value);
    }
  }

  private void writeAnnotationElement(AnnotationVisitor visitor, String name, DexValue value) {
    if (value instanceof DexValueAnnotation) {
      DexValueAnnotation valueAnnotation = (DexValueAnnotation) value;
      AnnotationVisitor innerVisitor =
          visitor.visitAnnotation(name, valueAnnotation.value.type.toDescriptorString());
      if (innerVisitor != null) {
        writeAnnotation(innerVisitor, valueAnnotation.value);
        innerVisitor.visitEnd();
      }
    } else if (value instanceof DexValueArray) {
      DexValue[] values = ((DexValueArray) value).getValues();
      AnnotationVisitor innerVisitor = visitor.visitArray(name);
      if (innerVisitor != null) {
        for (DexValue arrayValue : values) {
          writeAnnotationElement(innerVisitor, null, arrayValue);
        }
        innerVisitor.visitEnd();
      }
    } else if (value instanceof DexValueEnum) {
      DexValueEnum en = (DexValueEnum) value;
      visitor.visitEnum(name, en.value.type.toDescriptorString(), en.value.name.toString());
    } else if (value instanceof DexValueField) {
      throw new Unreachable("writeAnnotationElement of DexValueField");
    } else if (value instanceof DexValueMethod) {
      throw new Unreachable("writeAnnotationElement of DexValueMethod");
    } else if (value instanceof DexValueMethodHandle) {
      throw new Unreachable("writeAnnotationElement of DexValueMethodHandle");
    } else if (value instanceof DexValueMethodType) {
      throw new Unreachable("writeAnnotationElement of DexValueMethodType");
    } else if (value instanceof DexValueString) {
      DexValueString str = (DexValueString) value;
      visitor.visit(name, str.getValue().toString());
    } else if (value instanceof DexValueType) {
      DexValueType ty = (DexValueType) value;
      visitor.visit(name, Type.getType(ty.value.toDescriptorString()));
    } else if (value instanceof UnknownDexValue) {
      throw new Unreachable("writeAnnotationElement of UnknownDexValue");
    } else {
      visitor.visit(name, value.getBoxedValue());
    }
  }

  private void writeCode(Code code, MethodVisitor visitor) {
    if (code.isJarCode()) {
      code.asJarCode().writeTo(visitor);
    } else {
      assert code.isCfCode();
      code.asCfCode().write(visitor);
    }
  }

  public static String printCf(byte[] result) {
    ClassReader reader = new ClassReader(result);
    ClassNode node = new ClassNode(ASM6);
    reader.accept(node, ASM6);
    StringWriter writer = new StringWriter();
    for (MethodNode method : node.methods) {
      writer.append(method.name).append(method.desc).append('\n');
      TraceMethodVisitor visitor = new TraceMethodVisitor(new Textifier());
      method.accept(visitor);
      visitor.p.print(new PrintWriter(writer));
      writer.append('\n');
    }
    return writer.toString();
  }

  private static void verifyCf(byte[] result) {
    ClassReader reader = new ClassReader(result);
    PrintWriter pw = new PrintWriter(System.out);
    CheckClassAdapter.verify(reader, false, pw);
  }
}
