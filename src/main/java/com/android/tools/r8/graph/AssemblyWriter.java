// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import static com.android.tools.r8.utils.StringUtils.LINE_SEPARATOR;

import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.conversion.MethodProcessingId;
import com.android.tools.r8.ir.conversion.OneTimeMethodProcessor;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackIgnore;
import com.android.tools.r8.kotlin.Kotlin;
import com.android.tools.r8.kotlin.KotlinMetadataWriter;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.naming.MemberNaming.FieldSignature;
import com.android.tools.r8.utils.CfgPrinter;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Timing;
import java.io.BufferedReader;
import java.io.PrintStream;
import java.io.StringReader;
import java.util.stream.Collectors;

public class AssemblyWriter extends DexByteCodeWriter {

  private final MethodProcessingId.Factory methodProcessingIdFactory =
      new MethodProcessingId.Factory();
  private final boolean writeAllClassInfo;
  private final boolean writeFields;
  private final boolean writeAnnotations;
  private final boolean writeIR;
  private final boolean writeCode;
  private final AppInfo appInfo;
  private final Kotlin kotlin;
  private final Timing timing = new Timing("AssemblyWriter");

  public AssemblyWriter(
      DexApplication application,
      InternalOptions options,
      boolean allInfo,
      boolean writeIR,
      boolean writeCode) {
    super(application, options);
    this.writeAllClassInfo = allInfo;
    this.writeFields = allInfo;
    this.writeAnnotations = allInfo;
    this.writeIR = writeIR;
    this.writeCode = writeCode;
    if (writeIR) {
      this.appInfo = AppInfo.createInitialAppInfo(application.toDirect());
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
  }

  @Override
  String getFileEnding() {
    return ".dump";
  }

  @Override
  void writeClassHeader(DexProgramClass clazz, PrintStream ps) {
    String clazzName;
    if (application.getProguardMap() != null) {
      clazzName = application.getProguardMap().originalNameOf(clazz.type);
    } else {
      clazzName = clazz.type.toSourceString();
    }
    ps.println("# Bytecode for");
    ps.println("# Class: '" + clazzName + "'");
    if (writeAllClassInfo) {
      writeAnnotations(clazz, clazz.annotations(), ps);
      ps.println("# Flags: '" + clazz.accessFlags + "'");
      if (clazz.superType != application.dexItemFactory.objectType) {
        ps.println("# Extends: '" + clazz.superType.toSourceString() + "'");
      }
      for (DexType value : clazz.interfaces.values) {
        ps.println("# Implements: '" + value.toSourceString() + "'");
      }
      if (!clazz.getInnerClasses().isEmpty()) {
        ps.println("# InnerClasses:");
        for (InnerClassAttribute innerClassAttribute : clazz.getInnerClasses()) {
          ps.println(
              "#  Outer: "
                  + (innerClassAttribute.getOuter() != null
                      ? innerClassAttribute.getOuter().toSourceString()
                      : "-")
                  + ", inner: "
                  + innerClassAttribute.getInner().toSourceString()
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
          ps.println("#  Class: " + enclosingMethodAttribute.getEnclosingClass().toSourceString());
        } else {
          ps.println(
              "#  Method: " + enclosingMethodAttribute.getEnclosingMethod().toSourceString());
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
      ClassNameMapper naming = application.getProguardMap();
      FieldSignature fieldSignature = naming != null
          ? naming.originalSignatureOf(field.field)
          : FieldSignature.fromDexField(field.field);
      writeAnnotations(null, field.annotations(), ps);
      ps.print(field.accessFlags + " ");
      ps.print(fieldSignature);
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
    ClassNameMapper naming = application.getProguardMap();
    String methodName =
        naming != null
            ? naming.originalSignatureOf(method.getReference()).name
            : method.getReference().name.toString();
    ps.println("#");
    ps.println("# Method: '" + methodName + "':");
    writeAnnotations(null, definition.annotations(), ps);
    ps.println("# " + definition.accessFlags);
    ps.println("#");
    ps.println();
    if (!writeCode) {
      return;
    }
    Code code = definition.getCode();
    if (code != null) {
      if (writeIR) {
        writeIR(method, ps);
      } else {
        ps.println(code.toString(definition, naming));
      }
    }
  }

  private void writeIR(ProgramMethod method, PrintStream ps) {
    CfgPrinter printer = new CfgPrinter();
    IRConverter converter = new IRConverter(appInfo, timing, printer);
    OneTimeMethodProcessor methodProcessor =
        OneTimeMethodProcessor.create(method, methodProcessingIdFactory);
    methodProcessor.forEachWaveWithExtension(
        (ignore, methodProcesingId) ->
            converter.processMethod(
                method,
                OptimizationFeedbackIgnore.getInstance(),
                methodProcessor,
                methodProcesingId));
    ps.println(printer.toString());
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
            String annotationString = annotation.toString();
            ps.print(
                new BufferedReader(new StringReader(annotationString))
                    .lines()
                    .collect(
                        Collectors.joining(
                            LINE_SEPARATOR + prefix + "  ", prefix, LINE_SEPARATOR)));
          }
        }
      }
    }
  }

  @Override
  void writeClassFooter(DexProgramClass clazz, PrintStream ps) {

  }
}
