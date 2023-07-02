// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import static com.android.tools.r8.utils.StringUtils.LINE_SEPARATOR;

import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.contexts.CompilationContext;
import com.android.tools.r8.graph.GenericSignature.ClassSignature;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.conversion.MethodConversionOptions;
import com.android.tools.r8.ir.conversion.MethodProcessorEventConsumer;
import com.android.tools.r8.ir.conversion.OneTimeMethodProcessor;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackIgnore;
import com.android.tools.r8.kotlin.Kotlin;
import com.android.tools.r8.kotlin.KotlinMetadataWriter;
import com.android.tools.r8.synthesis.SyntheticItems.GlobalSyntheticsStrategy;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.RetracerForCodePrinting;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.StringUtils.BraceType;
import com.android.tools.r8.utils.Timing;
import java.io.BufferedReader;
import java.io.PrintStream;
import java.io.StringReader;
import java.util.stream.Collectors;

public class AssemblyWriter extends DexByteCodeWriter {

  private final boolean writeAllClassInfo;
  private final boolean writeFields;
  private final boolean writeAnnotations;
  private final boolean writeIR;
  private final boolean writeCode;
  private final AppInfo appInfo;
  private final Kotlin kotlin;
  private final Timing timing = new Timing("AssemblyWriter");
  private final CompilationContext compilationContext;
  private final RetracerForCodePrinting retracer;

  public AssemblyWriter(
      DexApplication application,
      InternalOptions options,
      boolean allInfo,
      boolean writeIR,
      boolean writeCode) {
    super(application, options);
    this.compilationContext = CompilationContext.createInitialContext(options);
    this.writeAllClassInfo = allInfo;
    this.writeFields = allInfo;
    this.writeAnnotations = allInfo;
    this.writeIR = writeIR;
    this.writeCode = writeCode;
    if (writeIR) {
      this.appInfo =
          AppInfo.createInitialAppInfo(
              application.toDirect(), GlobalSyntheticsStrategy.forNonSynthesizing());
      if (options.programConsumer == null) {
        // Use class-file backend, since the CF frontend for testing does not support desugaring of
        // synchronized methods for the DEX backend (b/109789541).
        options.programConsumer = ClassFileConsumer.emptyConsumer();
      }
      options.outline.enabled = false;
    } else {
      this.appInfo = null;
    }
    kotlin = new Kotlin(application.dexItemFactory);
    retracer =
        RetracerForCodePrinting.create(application.getProguardMap(), application.options.reporter);
  }

  public static String getFileEnding() {
    return ".dump";
  }

  @Override
  void writeClassHeader(DexProgramClass clazz, PrintStream ps) {
    String clazzName = retracer.toSourceString(clazz.getType());
    ps.println("# Bytecode for");
    ps.println("# Class: '" + clazzName + "'");
    if (writeAllClassInfo) {
      ClassSignature signature = clazz.getClassSignature();
      if (signature != null && signature.hasSignature()) {
        ps.println("# Signature: " + signature);
      }
      writeAnnotations(clazz, clazz.annotations(), ps);
      ps.println("# Flags: '" + clazz.accessFlags + "'");
      if (clazz.superType != application.dexItemFactory.objectType) {
        ps.println("# Extends: '" + retracer.toSourceString(clazz.superType) + "'");
      }
      for (DexType value : clazz.interfaces.values) {
        ps.println("# Implements: '" + retracer.toSourceString(value) + "'");
      }
      if (!clazz.getInnerClasses().isEmpty()) {
        ps.println("# InnerClasses:");
        for (InnerClassAttribute innerClassAttribute : clazz.getInnerClasses()) {
          ps.println(
              "#  Outer: "
                  + (innerClassAttribute.getOuter() != null
                      ? retracer.toSourceString(innerClassAttribute.getOuter())
                      : "-")
                  + ", inner: "
                  + retracer.toSourceString(innerClassAttribute.getInner())
                  + ", inner name: "
                  + innerClassAttribute.getInnerName()
                  + ", access: "
                  + Integer.toHexString(innerClassAttribute.getAccess()));
        }
      }
      EnclosingMethodAttribute enclosingMethodAttribute = clazz.getEnclosingMethodAttribute();
      if (enclosingMethodAttribute != null) {
        ps.println("# EnclosingMethod:");
        if (enclosingMethodAttribute.getEnclosingClass() != null) {
          ps.println(
              "#  Class: " + retracer.toSourceString(enclosingMethodAttribute.getEnclosingClass()));
        } else {
          ps.println(
              "#  Method: "
                  + retracer.toSourceString(enclosingMethodAttribute.getEnclosingMethod()));
        }
      }
    }
    ps.println();
  }

  @Override
  void writeFieldsHeader(DexProgramClass clazz, PrintStream ps) {
    if (writeFields) {
      ps.println("#");
      ps.println("# Fields:");
      ps.println("#");
    }
  }

  @Override
  void writeField(DexEncodedField field, PrintStream ps) {
    if (writeFields) {
      writeAnnotations(null, field.annotations(), ps);
      ps.print(field.accessFlags + " ");
      ps.print(retracer.toSourceString(field.getReference()));
      if (!retracer.isEmpty()) {
        ps.println("# Residual: '" + field.getReference().toSourceString() + "'");
      }
      if (field.isStatic() && field.hasExplicitStaticValue()) {
        ps.print(" = " + field.getStaticValue());
      }
      ps.println();
    }
  }

  @Override
  void writeFieldsFooter(DexProgramClass clazz, PrintStream ps) {
    ps.println();
  }

  @Override
  void writeMethod(ProgramMethod method, PrintStream ps) {
    DexEncodedMethod definition = method.getDefinition();
    ps.println("#");
    ps.println("# Method: '" + retracer.toSourceString(definition.getReference()) + "':");
    writeAnnotations(null, definition.annotations(), ps);
    ps.println("# " + definition.accessFlags);
    if (!retracer.isEmpty()) {
      ps.println("# Residual: '" + definition.getReference().toSourceString() + "'");
    }
    ps.println("#");
    ps.println();
    if (!writeCode) {
      return;
    }
    Code code = definition.getCode();
    if (code != null) {
      if (writeIR) {
        writeIR(method);
      } else {
        ps.println(code.toString(definition, retracer));
      }
    }
  }

  private void writeIR(ProgramMethod method) {
    IRConverter converter = new IRConverter(appInfo);
    MethodProcessorEventConsumer eventConsumer = MethodProcessorEventConsumer.empty();
    OneTimeMethodProcessor methodProcessor =
        OneTimeMethodProcessor.create(
            method, eventConsumer, compilationContext.createProcessorContext());
    methodProcessor.forEachWaveWithExtension(
        (ignore, methodProcessingContext) ->
            converter.processDesugaredMethod(
                method,
                OptimizationFeedbackIgnore.getInstance(),
                methodProcessor,
                methodProcessingContext,
                MethodConversionOptions.forD8(converter.appView)));
  }

  private void writeAnnotations(
      DexProgramClass clazz, DexAnnotationSet annotations, PrintStream ps) {
    if (writeAnnotations) {
      if (!annotations.isEmpty()) {
        ps.println("# Annotations:");
        String prefix = "#  ";
        for (DexAnnotation annotation : annotations.annotations) {
          if (annotation.annotation.type == kotlin.factory.kotlinMetadataType) {
            assert clazz != null : "Kotlin metadata is a class annotation";
            KotlinMetadataWriter.writeKotlinMetadataAnnotation(prefix, annotation, ps, kotlin);
          } else {
            StringBuilder sb = new StringBuilder();
            sb.append(annotation.getVisibility());
            sb.append(" ");
            sb.append(retracer.toSourceString(annotation.getAnnotationType()));
            sb.append(
                StringUtils.join(
                    ",",
                    annotation.annotation.elements,
                    element ->
                        element.getName().toString() + " = " + getStringValue(element.getValue()),
                    BraceType.SQUARE));
            ps.print(
                new BufferedReader(new StringReader(sb.toString()))
                    .lines()
                    .collect(
                        Collectors.joining(
                            LINE_SEPARATOR + prefix + "  ", prefix, LINE_SEPARATOR)));
          }
        }
      }
    }
  }

  private String getStringValue(DexValue value) {
    if (value.isDexValueType()) {
      return retracer.toSourceString(value.asDexValueType().getValue());
    } else if (value.isDexValueMethodHandle()) {
      return retracer.toSourceString(value.asDexValueMethodHandle().value.asMethod());
    } else if (value.isDexValueMethod()) {
      return retracer.toSourceString(value.asDexValueMethod().value);
    } else if (value.isDexItemBasedValueString()) {
      return retracer.toSourceString(value.asDexItemBasedValueString().value);
    } else if (value.isDexValueEnum()) {
      return retracer.toSourceString(value.asDexValueEnum().value);
    } else if (value.isDexValueField()) {
      return retracer.toSourceString(value.asDexValueField().value);
    } else if (value.isDexValueArray()) {
      return "[" + value.asDexValueArray() + "]";
    } else {
      return value.toString();
    }
  }

  @Override
  void writeClassFooter(DexProgramClass clazz, PrintStream ps) {

  }
}
