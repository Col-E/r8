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
import com.android.tools.r8.graph.DexAnnotationElement;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexValue.DexValueType;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.utils.Action;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.ClassReferenceUtils;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
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
      Paths.get(ToolHelper.MAIN_SOURCE_DIR)
          .resolve(PACKAGE_NAME.replace('.', '/'))
          .resolve(CLASS_NAME + ".java");
  private static final AndroidApiLevel GENERATED_FOR_API_LEVEL = AndroidApiLevel.U;

  @Parameter public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void testLibCoreNeedsUpgrading() {
    assertEquals(GENERATED_FOR_API_LEVEL, AndroidApiLevel.API_DATABASE_LEVEL);
  }

  @Test
  public void testCanFindAnnotatedMethodsInJar() throws Exception {
    CovariantMethodsInJarResult covariantMethodsInJar = CovariantMethodsInJarResult.create();
    // These assertions are here to ensure we produce a sane result.
    assertEquals(9, covariantMethodsInJar.methodReferenceMap.keySet().size());
    assertEquals(
        51, covariantMethodsInJar.methodReferenceMap.values().stream().mapToLong(List::size).sum());
  }

  @Test
  public void testGeneratedCodeUpToDate() throws Exception {
    assertEquals(FileUtils.readTextFile(DESTINATION_FILE, StandardCharsets.UTF_8), generateCode());
  }

  public static String generateCode() throws Exception {
    CovariantMethodsInJarResult covariantMethodsInJar = CovariantMethodsInJarResult.create();
    List<Entry<ClassReference, List<MethodReferenceWithApiLevel>>> entries =
        new ArrayList<>(covariantMethodsInJar.methodReferenceMap.entrySet());
    entries.sort(Entry.comparingByKey(ClassReferenceUtils.getClassReferenceComparator()));
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
                            (ignored, covariations) -> {
                              covariations.sort(
                                  Comparator.comparing(
                                      MethodReferenceWithApiLevel::getMethodReference,
                                      MethodReferenceUtils.getMethodReferenceComparator()));
                              covariations.forEach(
                                  covariant ->
                                      registerCovariantMethod(
                                          methodPrinter, covariant.methodReference));
                            })))
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
    private final Map<ClassReference, List<MethodReferenceWithApiLevel>> methodReferenceMap;

    private CovariantMethodsInJarResult(
        Map<ClassReference, List<MethodReferenceWithApiLevel>> methodReferenceMap) {
      this.methodReferenceMap = methodReferenceMap;
    }

    public static CovariantMethodsInJarResult create() throws Exception {
      Map<ClassReference, List<MethodReferenceWithApiLevel>> methodReferenceMap = new HashMap<>();
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
                ClassReference holder = clazz.getOriginalReference();
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
        Map<ClassReference, List<MethodReferenceWithApiLevel>> methodReferenceMap) {
      DexValueType newReturnType =
          covariantAnnotation.annotation.getElement(0).getValue().asDexValueType();
      DexAnnotationElement element = covariantAnnotation.annotation.getElement(1);
      assert element.name.toString().equals("presentAfter");
      AndroidApiLevel apiLevel =
          AndroidApiLevel.getAndroidApiLevel(element.getValue().asDexValueInt().value);
      methodReferenceMap
          .computeIfAbsent(methodReference.getHolderClass(), ignoreKey(ArrayList::new))
          .add(
              new MethodReferenceWithApiLevel(
                  Reference.method(
                      methodReference.getHolderClass(),
                      methodReference.getMethodName(),
                      methodReference.getFormalTypes(),
                      newReturnType.value.asClassReference()),
                  apiLevel));
    }

    public void visitCovariantMethodsForHolder(
        ClassReference reference, Consumer<MethodReferenceWithApiLevel> consumer) {
      List<MethodReferenceWithApiLevel> methodReferences = methodReferenceMap.get(reference);
      if (methodReferences != null) {
        methodReferences.stream()
            .sorted(
                Comparator.comparing(
                    MethodReferenceWithApiLevel::getMethodReference,
                    MethodReferenceUtils.getMethodReferenceComparator()))
            .forEach(consumer);
      }
    }
  }

  public static class MethodReferenceWithApiLevel {

    private final MethodReference methodReference;
    private final AndroidApiLevel apiLevel;

    private MethodReferenceWithApiLevel(MethodReference methodReference, AndroidApiLevel apiLevel) {
      this.methodReference = methodReference;
      this.apiLevel = apiLevel;
    }

    public MethodReference getMethodReference() {
      return methodReference;
    }

    public AndroidApiLevel getApiLevel() {
      return apiLevel;
    }
  }
}
