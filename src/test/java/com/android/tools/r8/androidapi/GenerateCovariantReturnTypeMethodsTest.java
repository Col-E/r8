// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.androidapi;

import static com.android.tools.r8.apimodel.JavaSourceCodePrinter.Type.fromType;
import static com.android.tools.r8.ir.desugar.CovariantReturnTypeAnnotationTransformer.isCovariantReturnTypeAnnotation;
import static com.android.tools.r8.utils.MapUtils.ignoreKey;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.apimodel.JavaSourceCodePrinter;
import com.android.tools.r8.apimodel.JavaSourceCodePrinter.JavaSourceCodeMethodPrinter;
import com.android.tools.r8.apimodel.JavaSourceCodePrinter.KnownType;
import com.android.tools.r8.apimodel.JavaSourceCodePrinter.MethodParameter;
import com.android.tools.r8.apimodel.JavaSourceCodePrinter.ParameterizedType;
import com.android.tools.r8.cfmethodgeneration.MethodGenerationBase;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexValue.DexValueType;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.utils.Action;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.EntryUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.google.common.collect.ImmutableList;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class GenerateCovariantReturnTypeMethodsTest extends TestBase {

  private static final String CLASS_NAME = "CovariantReturnTypeMethods";
  private static final String PACKAGE_NAME = "com.android.tools.r8.androidapi";
  // When updating to support a new api level build libcore in aosp and update the cloud dependency.
  private static final Path PATH_TO_CORE_JAR =
      Paths.get(ToolHelper.THIRD_PARTY_DIR, "android_jar", "libcore_latest", "core-oj.jar");
  private static final Path DESTINATION_FILE =
      Paths.get(ToolHelper.SOURCE_DIR)
          .resolve(PACKAGE_NAME.replace('.', '/'))
          .resolve(CLASS_NAME + ".java");
  private static final AndroidApiLevel GENERATED_FOR_API_LEVEL = AndroidApiLevel.T;

  @Parameter public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void testLibCoreNeedsUpgrading() {
    assertEquals(GENERATED_FOR_API_LEVEL, AndroidApiLevel.LATEST);
  }

  @Test
  public void testCanFindAnnotatedMethodsInJar() throws Exception {
    CovariantMethodsInJarResult covariantMethodsInJar = CovariantMethodsInJarResult.create();
    // These assertions are here to ensure we produce a sane result.
    assertEquals(51, covariantMethodsInJar.methodReferenceMap.size());
  }

  @Test
  public void testGeneratedCodeUpToDate() throws Exception {
    assertEquals(FileUtils.readTextFile(DESTINATION_FILE, StandardCharsets.UTF_8), generateCode());
  }

  public static String generateCode() throws Exception {
    CovariantMethodsInJarResult covariantMethodsInJar = CovariantMethodsInJarResult.create();
    Map<MethodReference, List<MethodReference>> methodReferenceMap =
        covariantMethodsInJar.methodReferenceMap;
    List<Entry<MethodReference, List<MethodReference>>> entries =
        new ArrayList<>(methodReferenceMap.entrySet());
    entries.sort(Entry.comparingByKey(MethodReferenceUtils.getMethodReferenceComparator()));
    JavaSourceCodePrinter printer =
        JavaSourceCodePrinter.builder()
            .setHeader(
                MethodGenerationBase.getHeaderString(
                    2022, GenerateCovariantReturnTypeMethodsTest.class.getSimpleName()))
            .setPackageName(PACKAGE_NAME)
            .setClassName(CLASS_NAME)
            .build();
    String javaSourceCode =
        printer
            .addMethod(
                "public static",
                null,
                "registerMethodsWithCovariantReturnType",
                ImmutableList.of(
                    MethodParameter.build(fromType(KnownType.DexItemFactory), "factory"),
                    MethodParameter.build(
                        ParameterizedType.fromType(
                            KnownType.Consumer, fromType(KnownType.DexMethod)),
                        "consumer")),
                methodPrinter ->
                    entries.forEach(
                        EntryUtils.accept(
                            (ignored, covariations) ->
                                covariations.forEach(
                                    covariant ->
                                        registerCovariantMethod(methodPrinter, covariant)))))
            .toString();
    Path tempFile = Files.createTempFile("output-", ".java");
    Files.write(tempFile, javaSourceCode.getBytes(StandardCharsets.UTF_8));
    return MethodGenerationBase.formatRawOutput(tempFile);
  }

  private static void registerCovariantMethod(
      JavaSourceCodeMethodPrinter methodPrinter, MethodReference covariant) {
    methodPrinter
        .addInstanceMethodCall(
            "consumer",
            "accept",
            () ->
                methodPrinter.addInstanceMethodCall(
                    "factory",
                    "createMethod",
                    callCreateType(methodPrinter, covariant.getHolderClass().getDescriptor()),
                    callCreateProto(
                        methodPrinter,
                        covariant.getReturnType().getDescriptor(),
                        covariant.getFormalTypes().stream()
                            .map(TypeReference::getDescriptor)
                            .collect(Collectors.toList())),
                    methodPrinter.literal(covariant.getMethodName())))
        .addSemicolon()
        .newLine();
  }

  private static Action callCreateType(
      JavaSourceCodeMethodPrinter methodPrinter, String descriptor) {
    return () ->
        methodPrinter.addInstanceMethodCall(
            "factory", "createType", methodPrinter.literal(descriptor));
  }

  private static Action callCreateProto(
      JavaSourceCodeMethodPrinter methodPrinter,
      String returnTypeDescriptor,
      Collection<String> args) {
    List<Action> actionList = new ArrayList<>();
    actionList.add(callCreateType(methodPrinter, returnTypeDescriptor));
    for (String arg : args) {
      actionList.add(callCreateType(methodPrinter, arg));
    }
    return () -> methodPrinter.addInstanceMethodCall("factory", "createProto", actionList);
  }

  public static void main(String[] args) throws Exception {
    Files.write(DESTINATION_FILE, generateCode().getBytes(StandardCharsets.UTF_8));
  }

  public static class CovariantMethodsInJarResult {
    private final Map<MethodReference, List<MethodReference>> methodReferenceMap;

    private CovariantMethodsInJarResult(
        Map<MethodReference, List<MethodReference>> methodReferenceMap) {
      this.methodReferenceMap = methodReferenceMap;
    }

    public static CovariantMethodsInJarResult create() throws Exception {
      Map<MethodReference, List<MethodReference>> methodReferenceMap = new HashMap<>();
      CodeInspector inspector = new CodeInspector(PATH_TO_CORE_JAR);
      DexItemFactory factory = inspector.getFactory();
      for (FoundClassSubject clazz : inspector.allClasses()) {
        clazz.forAllMethods(
            method -> {
              List<DexAnnotation> covariantAnnotations =
                  inspector.findAnnotations(
                      method.getMethod().annotations(),
                      annotation ->
                          isCovariantReturnTypeAnnotation(annotation.annotation, factory));
              if (!covariantAnnotations.isEmpty()) {
                MethodReference methodReference = method.asMethodReference();
                for (DexAnnotation covariantAnnotation : covariantAnnotations) {
                  if (covariantAnnotation.annotation.type
                      == factory.annotationCovariantReturnType) {
                    createCovariantMethodReference(
                        methodReference, covariantAnnotation, methodReferenceMap);
                  } else {
                    fail("There are no such annotations present in libcore");
                  }
                }
              }
            });
      }
      return new CovariantMethodsInJarResult(methodReferenceMap);
    }

    private static void createCovariantMethodReference(
        MethodReference methodReference,
        DexAnnotation covariantAnnotation,
        Map<MethodReference, List<MethodReference>> methodReferenceMap) {
      DexValueType newReturnType =
          covariantAnnotation.annotation.getElement(0).getValue().asDexValueType();
      methodReferenceMap
          .computeIfAbsent(methodReference, ignoreKey(ArrayList::new))
          .add(
              Reference.method(
                  methodReference.getHolderClass(),
                  methodReference.getMethodName(),
                  methodReference.getFormalTypes(),
                  newReturnType.value.asClassReference()));
    }
  }
}
