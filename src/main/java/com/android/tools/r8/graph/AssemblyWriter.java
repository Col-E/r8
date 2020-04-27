// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import static com.android.tools.r8.utils.StringUtils.LINE_SEPARATOR;

import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.conversion.OneTimeMethodProcessor;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackIgnore;
import com.android.tools.r8.kotlin.Kotlin;
import com.android.tools.r8.kotlin.KotlinClassMetadataReader;
import com.android.tools.r8.kotlin.KotlinInfo;
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

  private final boolean writeAllClassInfo;
  private final boolean writeFields;
  private final boolean writeAnnotations;
  private final boolean writeIR;
  private final AppInfoWithClassHierarchy appInfo;
  private final Kotlin kotlin;
  private final Timing timing = new Timing("AssemblyWriter");

  public AssemblyWriter(
      DexApplication application, InternalOptions options, boolean allInfo, boolean writeIR) {
    super(application, options);
    this.writeAllClassInfo = allInfo;
    this.writeFields = allInfo;
    this.writeAnnotations = allInfo;
    this.writeIR = writeIR;
    if (writeIR) {
      this.appInfo = new AppInfoWithClassHierarchy(application.toDirect());
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
  void writeMethod(DexEncodedMethod method, PrintStream ps) {
    ClassNameMapper naming = application.getProguardMap();
    String methodName = naming != null
        ? naming.originalSignatureOf(method.method).name
        : method.method.name.toString();
    ps.println("#");
    ps.println("# Method: '" + methodName + "':");
    writeAnnotations(null, method.annotations(), ps);
    ps.println("# " + method.accessFlags);
    ps.println("#");
    ps.println();
    Code code = method.getCode();
    if (code != null) {
      if (writeIR) {
        writeIR(method, ps);
      } else {
        ps.println(code.toString(method, naming));
      }
    }
  }

  private void writeIR(DexEncodedMethod method, PrintStream ps) {
    CfgPrinter printer = new CfgPrinter();
    new IRConverter(appInfo, options, timing, printer)
        .processMethod(
            method,
            OptimizationFeedbackIgnore.getInstance(),
            OneTimeMethodProcessor.getInstance());
    ps.println(printer.toString());
  }

  private void writeAnnotations(
      DexProgramClass clazz, DexAnnotationSet annotations, PrintStream ps) {
    if (writeAnnotations) {
      if (!annotations.isEmpty()) {
        ps.println("# Annotations:");
        for (DexAnnotation annotation : annotations.annotations) {
          if (annotation.annotation.type == kotlin.metadata.kotlinMetadataType) {
            assert clazz != null : "Kotlin metadata is a class annotation";
            writeKotlinMetadata(clazz, annotation, ps);
          } else {
            String annotationString = annotation.toString();
            String prefix = "#  ";
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

  private void writeKotlinMetadata(
      DexProgramClass clazz, DexAnnotation annotation, PrintStream ps) {
    assert annotation.annotation.type == kotlin.metadata.kotlinMetadataType;
    KotlinInfo kotlinInfo =
        KotlinClassMetadataReader.createKotlinInfo(kotlin, clazz, annotation.annotation);
    ps.println(kotlinInfo.toString("#  "));
  }

  @Override
  void writeClassFooter(DexProgramClass clazz, PrintStream ps) {

  }
}
