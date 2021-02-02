// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.lambda;

import static junit.framework.TestCase.assertTrue;

import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class KotlinLambdasInInput {

  private final Set<ClassReference> jStyleLambdas;
  private final Set<ClassReference> kStyleLambdas;

  private KotlinLambdasInInput(
      Set<ClassReference> jStyleLambdas, Set<ClassReference> kStyleLambdas) {
    this.jStyleLambdas = jStyleLambdas;
    this.kStyleLambdas = kStyleLambdas;
  }

  public static KotlinLambdasInInput create(List<Path> programFiles, String testName)
      throws IOException {
    CodeInspector inputInspector = new CodeInspector(programFiles);
    Set<ClassReference> jStyleLambdas = new HashSet<>();
    Set<ClassReference> kStyleLambdas = new HashSet<>();
    for (FoundClassSubject classSubject : inputInspector.allClasses()) {
      DexProgramClass clazz = classSubject.getDexProgramClass();
      if (!clazz.getType().getPackageName().startsWith(testName)) {
        continue;
      }
      if (internalIsJStyleLambda(clazz)) {
        jStyleLambdas.add(Reference.classFromTypeName(clazz.getTypeName()));
      } else if (internalIsKStyleLambda(clazz)) {
        kStyleLambdas.add(Reference.classFromTypeName(clazz.getTypeName()));
      }
    }
    return new KotlinLambdasInInput(jStyleLambdas, kStyleLambdas);
  }

  private static boolean internalIsKStyleLambda(DexProgramClass clazz) {
    return clazz.getSuperType().getTypeName().equals("kotlin.jvm.internal.Lambda");
  }

  private static boolean internalIsJStyleLambda(DexProgramClass clazz) {
    if (!clazz.getSuperType().getTypeName().equals(Object.class.getTypeName())
        || clazz.getInterfaces().size() != 1
        || clazz.getMethodCollection().numberOfVirtualMethods() == 0) {
      return false;
    }
    if (clazz
        .getMethodCollection()
        .hasDirectMethods(method -> method.isStatic() && !method.isClassInitializer())) {
      return false;
    }
    int numberOfFinalNonBridgeNonSyntheticMethods = 0;
    for (DexEncodedMethod method : clazz.virtualMethods()) {
      if (method.isFinal() && !method.isBridge() && !method.isSyntheticMethod()) {
        numberOfFinalNonBridgeNonSyntheticMethods++;
      }
    }
    return numberOfFinalNonBridgeNonSyntheticMethods == 1;
  }

  public Set<ClassReference> getAllLambdas() {
    return SetUtils.newIdentityHashSet(jStyleLambdas, kStyleLambdas);
  }

  public Set<ClassReference> getJStyleLambdas() {
    return jStyleLambdas;
  }

  public ClassReference getJStyleLambdaReferenceFromTypeName(String testName, String simpleName) {
    ClassReference classReference = Reference.classFromTypeName(testName + "." + simpleName);
    assertTrue(jStyleLambdas.contains(classReference));
    return classReference;
  }

  public Set<ClassReference> getKStyleLambdas() {
    return kStyleLambdas;
  }

  public ClassReference getKStyleLambdaReferenceFromTypeName(String testName, String simpleName) {
    ClassReference classReference = Reference.classFromTypeName(testName + "." + simpleName);
    assertTrue(
        "Class is not a Kotlin-style lambda: " + classReference.getTypeName(),
        kStyleLambdas.contains(classReference));
    return classReference;
  }

  public int getNumberOfJStyleLambdas() {
    return jStyleLambdas.size();
  }

  public int getNumberOfKStyleLambdas() {
    return kStyleLambdas.size();
  }

  public boolean isJStyleLambda(ClassReference classReference) {
    return jStyleLambdas.contains(classReference);
  }

  public boolean isKStyleLambda(ClassReference classReference) {
    return kStyleLambdas.contains(classReference);
  }

  public void print() {
    System.out.println("Java-style Kotlin lambdas:");
    jStyleLambdas.forEach(lambda -> System.out.println(lambda.getTypeName()));
    System.out.println();
    System.out.println("Kotlin-style Kotlin lambdas:");
    kStyleLambdas.forEach(lambda -> System.out.println(lambda.getTypeName()));
  }
}
