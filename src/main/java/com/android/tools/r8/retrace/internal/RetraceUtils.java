// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.naming.ClassNamingForNameMapper.MappedRange;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.retrace.RetraceClassResult.Element;
import com.android.tools.r8.retrace.RetraceSourceFileResult;
import com.android.tools.r8.retrace.RetracedClass;
import com.android.tools.r8.retrace.RetracedMethod;
import com.android.tools.r8.retrace.RetracedMethod.KnownRetracedMethod;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class RetraceUtils {

  private static final Set<String> UNKNOWN_SOURCEFILE_NAMES =
      Sets.newHashSet("", "SourceFile", "Unknown", "Unknown Source", "PG");

  public static String methodDescriptionFromRetraceMethod(
      RetracedMethod methodReference, boolean appendHolder, boolean verbose) {
    StringBuilder sb = new StringBuilder();
    if (appendHolder) {
      sb.append(methodReference.getHolderClass().getTypeName());
      sb.append(".");
    }
    if (!verbose || methodReference.isUnknown()) {
      return sb.append(methodReference.getMethodName()).toString();
    }
    assert methodReference.isKnown();
    KnownRetracedMethod knownRef = methodReference.asKnown();
    sb.append(knownRef.isVoid() ? "void" : knownRef.getReturnType().getTypeName());
    sb.append(" ");
    sb.append(methodReference.getMethodName());
    sb.append("(");
    boolean seenFirstIndex = false;
    for (TypeReference formalType : knownRef.getFormalTypes()) {
      if (seenFirstIndex) {
        sb.append(",");
      }
      seenFirstIndex = true;
      sb.append(formalType.getTypeName());
    }
    sb.append(")");
    return sb.toString();
  }

  public static boolean hasPredictableSourceFileName(String originalClassName, String sourceFile) {
    String synthesizedSourceFileName = getOuterClassSimpleName(originalClassName) + ".java";
    return synthesizedSourceFileName.equals(sourceFile);
  }

  private static String getOuterClassSimpleName(String clazz) {
    int lastIndexOfPeriod = clazz.lastIndexOf(DescriptorUtils.JAVA_PACKAGE_SEPARATOR);
    // Check if we can find a subclass separator.
    int endIndex = clazz.indexOf(DescriptorUtils.INNER_CLASS_SEPARATOR, lastIndexOfPeriod);
    if (lastIndexOfPeriod > endIndex || endIndex < 0) {
      endIndex = clazz.length();
    }
    return clazz.substring(lastIndexOfPeriod + 1, endIndex);
  }

  static RetraceSourceFileResult getSourceFile(
      Element classElement, RetracedClass context, String sourceFile, RetracerImpl retracer) {
    // If no context is specified always retrace using the found class element.
    if (context == null) {
      return classElement.retraceSourceFile(sourceFile);
    }
    if (context.equals(classElement.getRetracedClass())) {
      return classElement.retraceSourceFile(sourceFile);
    } else {
      RetraceClassResultImpl contextClassResult =
          retracer.retraceClass(context.getClassReference());
      assert !contextClassResult.isAmbiguous();
      if (contextClassResult.hasRetraceResult()) {
        Box<RetraceSourceFileResult> retraceSourceFile = new Box<>();
        contextClassResult.forEach(
            element -> retraceSourceFile.set(element.retraceSourceFile(sourceFile)));
        return retraceSourceFile.get();
      } else {
        return new RetraceSourceFileResultImpl(
            synthesizeFileName(
                context.getTypeName(),
                classElement.getRetracedClass().getTypeName(),
                sourceFile,
                true),
            true);
      }
    }
  }

  public static String synthesizeFileName(
      String retracedClassName,
      String minifiedClassName,
      String sourceFile,
      boolean hasRetraceResult) {
    boolean fileNameProbablyChanged =
        hasRetraceResult && !retracedClassName.startsWith(minifiedClassName);
    if (!UNKNOWN_SOURCEFILE_NAMES.contains(sourceFile) && !fileNameProbablyChanged) {
      // We have no new information, only rewrite filename if it is unknown.
      // PG-retrace will always rewrite the filename, but that seems a bit to harsh to do.
      return sourceFile;
    }
    String extension = Files.getFileExtension(sourceFile);
    if (extension.isEmpty()) {
      extension = "java";
    }
    if (!hasRetraceResult) {
      // We have no mapping but but file name is unknown, so the best we can do is take the
      // name of the obfuscated clazz.
      assert minifiedClassName.equals(retracedClassName);
      return getOuterClassSimpleName(minifiedClassName) + "." + extension;
    }
    String newFileName = getOuterClassSimpleName(retracedClassName);
    return newFileName + "." + extension;
  }

  static MethodReference methodReferenceFromMappedRange(
      MappedRange mappedRange, ClassReference classReference) {
    MethodSignature signature = mappedRange.signature;
    ClassReference holder =
        signature.isQualified()
            ? Reference.classFromDescriptor(
                DescriptorUtils.javaTypeToDescriptor(signature.toHolderFromQualified()))
            : classReference;
    List<TypeReference> formalTypes = new ArrayList<>(signature.parameters.length);
    for (String parameter : signature.parameters) {
      formalTypes.add(Reference.typeFromTypeName(parameter));
    }
    TypeReference returnType =
        Reference.returnTypeFromDescriptor(DescriptorUtils.javaTypeToDescriptor(signature.type));
    return Reference.method(
        holder,
        signature.isQualified() ? signature.toUnqualifiedName() : signature.name,
        formalTypes,
        returnType);
  }
}
