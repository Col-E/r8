// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.utils.DescriptorUtils.descriptorToJavaType;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.GenerateLintFiles;
import com.android.tools.r8.StringResource;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.desugar.DesugaredLibraryConfiguration;
import com.android.tools.r8.ir.desugar.DesugaredLibraryConfigurationParser;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.WorkList;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ExtractWrapperTypesTest extends TestBase {

  // Filter on types that do not need to be considered for wrapping.
  private static boolean doesNotNeedWrapper(String type, Set<String> customConversions) {
    return excludePackage(type)
        || NOT_NEEDED_NOT_IN_DOCS.contains(type)
        || FINAL_CLASSES.contains(type)
        || customConversions.contains(type);
  }

  private static boolean excludePackage(String type) {
    return type.startsWith("java.lang.")
        || type.startsWith("java.nio.")
        || type.startsWith("java.security.")
        || type.startsWith("java.net.")
        || type.startsWith("java.awt.")
        || type.startsWith("java.util.concurrent.");
  }

  // Types not picked up by the android.jar scan but for which wrappers are needed.
  private static final Set<String> ADDITIONAL_WRAPPERS = ImmutableSet.of();

  // Types not in API docs, referenced in android.jar and must be wrapped.
  private static final Set<String> NEEDED_BUT_NOT_IN_DOCS = ImmutableSet.of();

  // Types not in API docs, referenced in android.jar but need not be wrapped.
  private static final Set<String> NOT_NEEDED_NOT_IN_DOCS =
      ImmutableSet.of(
          "java.util.function.ToDoubleBiFunction",
          "java.util.function.ToIntBiFunction",
          "java.util.function.ToLongBiFunction",
          "java.util.Base64$Decoder",
          "java.util.Base64$Encoder",
          "java.util.Calendar$Builder",
          "java.util.Locale$Builder",
          "java.util.Locale$Category",
          "java.util.Locale$FilteringMode",
          "java.util.SplittableRandom");

  // List of referenced final classes (cannot be wrapper converted) with no custom conversions.
  private static final Set<String> FINAL_CLASSES =
      ImmutableSet.of(
          // TODO(b/159304624): Does this need custom conversion?
          "java.time.Period");

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  // TODO: parameterize to check both api<=23 as well as 23<api<26 for which the spec differs.
  private final AndroidApiLevel minApi = AndroidApiLevel.B;
  private final AndroidApiLevel targetApi = AndroidApiLevel.Q;

  public ExtractWrapperTypesTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void checkConsistency() {
    List<Set<String>> sets =
        ImmutableList.of(
            ADDITIONAL_WRAPPERS, NEEDED_BUT_NOT_IN_DOCS, NOT_NEEDED_NOT_IN_DOCS, FINAL_CLASSES);
    for (Set<String> set1 : sets) {
      for (Set<String> set2 : sets) {
        if (set1 != set2) {
          assertEquals(Collections.emptySet(), Sets.intersection(set1, set2));
        }
      }
    }
    for (Set<String> set : sets) {
      for (String type : set) {
        assertFalse(excludePackage(type));
      }
    }
  }

  @Test
  public void test() throws Exception {
    CodeInspector desugaredApiJar = getDesugaredApiJar();
    Set<ClassReference> preDesugarTypes = getPreDesugarTypes();

    DesugaredLibraryConfiguration conf = getDesugaredLibraryConfiguration();
    Set<String> wrappersInSpec =
        conf.getWrapperConversions().stream().map(DexType::toString).collect(Collectors.toSet());
    Set<String> customConversionsInSpec =
        conf.getCustomConversions().keySet().stream()
            .map(DexType::toString)
            .collect(Collectors.toSet());
    assertEquals(
        Collections.emptySet(), Sets.intersection(wrappersInSpec, customConversionsInSpec));
    assertEquals(Collections.emptySet(), Sets.intersection(FINAL_CLASSES, customConversionsInSpec));

    CodeInspector nonDesugaredJar = new CodeInspector(ToolHelper.getAndroidJar(targetApi));
    Map<ClassReference, Set<MethodReference>> directWrappers =
        getDirectlyReferencedWrapperTypes(
            desugaredApiJar, preDesugarTypes, nonDesugaredJar, customConversionsInSpec);
    Map<ClassReference, Set<ClassReference>> indirectWrappers =
        getIndirectlyReferencedWrapperTypes(
            directWrappers, preDesugarTypes, nonDesugaredJar, customConversionsInSpec);

    {
      Set<String> missingWrappers = getMissingWrappers(directWrappers, wrappersInSpec);
      assertTrue(
          "Missing direct wrappers:\n" + String.join("\n", missingWrappers),
          missingWrappers.isEmpty());
    }

    {
      Set<String> missingWrappers = getMissingWrappers(indirectWrappers, wrappersInSpec);
      assertTrue(
          "Missing indirect wrappers:\n" + String.join("\n", missingWrappers),
          missingWrappers.isEmpty());
    }

    Set<String> additionalWrappers = new TreeSet<>();
    for (String wrapper : wrappersInSpec) {
      ClassReference item = Reference.classFromTypeName(wrapper);
      if (!directWrappers.containsKey(item)
          && !indirectWrappers.containsKey(item)
          && !ADDITIONAL_WRAPPERS.contains(wrapper)) {
        additionalWrappers.add(wrapper);
      }
    }
    assertTrue(
        "Additional wrapper:\n" + String.join("\n", additionalWrappers),
        additionalWrappers.isEmpty());

    assertEquals(
        directWrappers.size() + indirectWrappers.size() + ADDITIONAL_WRAPPERS.size(),
        wrappersInSpec.size());
  }

  private static <T> Set<String> getMissingWrappers(
      Map<ClassReference, Set<T>> expected, Set<String> wrappersInSpec) {
    Set<String> missingWrappers = new TreeSet<>();
    for (ClassReference addition : expected.keySet()) {
      String item = descriptorToJavaType(addition.getDescriptor());
      if (!wrappersInSpec.contains(item)) {
        missingWrappers.add(item + " referenced from: " + expected.get(addition));
      }
    }
    return missingWrappers;
  }

  private DesugaredLibraryConfiguration getDesugaredLibraryConfiguration() {
    DesugaredLibraryConfigurationParser parser =
        new DesugaredLibraryConfigurationParser(
            new DexItemFactory(), null, true, minApi.getLevel());
    return parser.parse(StringResource.fromFile(ToolHelper.DESUGAR_LIB_JSON_FOR_TESTING));
  }

  private Map<ClassReference, Set<MethodReference>> getDirectlyReferencedWrapperTypes(
      CodeInspector desugaredApiJar,
      Set<ClassReference> preDesugarTypes,
      CodeInspector nonDesugaredJar,
      Set<String> customConversions) {
    Map<ClassReference, Set<MethodReference>> directWrappers = new HashMap<>();
    nonDesugaredJar.forAllClasses(
        clazz -> {
          clazz.forAllMethods(
              method -> {
                if (!method.isPublic() && !method.isProtected()) {
                  return;
                }
                if (desugaredApiJar.method(method.asMethodReference()).isPresent()) {
                  return;
                }
                Consumer<ClassReference> adder =
                    t ->
                        directWrappers
                            .computeIfAbsent(t, k -> new HashSet<>())
                            .add(method.asMethodReference());
                MethodSignature signature = method.getFinalSignature().asMethodSignature();
                addType(adder, signature.type, preDesugarTypes, customConversions);
                for (String parameter : signature.parameters) {
                  addType(adder, parameter, preDesugarTypes, customConversions);
                }
              });
        });
    return directWrappers;
  }

  private Map<ClassReference, Set<ClassReference>> getIndirectlyReferencedWrapperTypes(
      Map<ClassReference, Set<MethodReference>> directWrappers,
      Set<ClassReference> existing,
      CodeInspector latest,
      Set<String> customConversions) {
    Map<ClassReference, Set<ClassReference>> indirectWrappers = new HashMap<>();
    WorkList<ClassReference> worklist = WorkList.newEqualityWorkList(directWrappers.keySet());
    while (worklist.hasNext()) {
      ClassReference reference = worklist.next();
      ClassSubject clazz = latest.clazz(reference);
      clazz.forAllVirtualMethods(
          method -> {
            assertTrue(method.toString(), method.isPublic() || method.isProtected());
            MethodSignature signature = method.getFinalSignature().asMethodSignature();
            Consumer<ClassReference> adder =
                t -> {
                  if (worklist.addIfNotSeen(t)) {
                    indirectWrappers.computeIfAbsent(t, k -> new HashSet<>()).add(reference);
                  }
                };
            addType(adder, signature.type, existing, customConversions);
            for (String parameter : signature.parameters) {
              addType(adder, parameter, existing, customConversions);
            }
          });
    }
    return indirectWrappers;
  }

  private Set<ClassReference> getPreDesugarTypes() throws IOException {
    Set<ClassReference> existing = new HashSet<>();
    Path androidJar = ToolHelper.getAndroidJar(minApi);
    new CodeInspector(androidJar)
        .forAllClasses(
            clazz -> {
              if (clazz.getFinalName().startsWith("java.")) {
                existing.add(Reference.classFromTypeName(clazz.getFinalName()));
              }
            });
    return existing;
  }

  private CodeInspector getDesugaredApiJar() throws Exception {
    Path out = temp.newFolder().toPath();
    GenerateLintFiles desugaredApi =
        new GenerateLintFiles(
            ToolHelper.DESUGAR_LIB_JSON_FOR_TESTING.toString(),
            ToolHelper.DESUGAR_JDK_LIBS,
            out.toString());
    desugaredApi.run(targetApi.getLevel());
    return new CodeInspector(
        out.resolve("compile_api_level_" + targetApi.getLevel())
            .resolve("desugared_apis_" + targetApi.getLevel() + "_" + minApi.getLevel() + ".jar"));
  }

  private void addType(
      Consumer<ClassReference> additions,
      String type,
      Set<ClassReference> preDesugarTypes,
      Set<String> customConversions) {
    if (type.equals("void")) {
      return;
    }
    TypeReference typeReference = Reference.typeFromTypeName(type);
    if (typeReference.isArray()) {
      typeReference = typeReference.asArray().getBaseType();
    }
    if (typeReference.isClass()) {
      ClassReference clazz = typeReference.asClass();
      String clazzType = descriptorToJavaType(clazz.getDescriptor());
      if (clazzType.startsWith("java.")
          && !doesNotNeedWrapper(clazzType, customConversions)
          && !preDesugarTypes.contains(clazz)) {
        additions.accept(clazz);
      }
    }
  }
}
