// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_PATH;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK8;
import static com.android.tools.r8.utils.DescriptorUtils.descriptorToJavaType;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.StringResource;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GenericSignature.ClassTypeSignature;
import com.android.tools.r8.graph.GenericSignature.FieldTypeSignature;
import com.android.tools.r8.graph.GenericSignature.MethodTypeSignature;
import com.android.tools.r8.graph.GenericSignature.TypeSignature;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibrarySpecificationParser;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineDesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.WrapperDescriptor;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.WorkList;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
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
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ExtractWrapperTypesTest extends DesugaredLibraryTestBase {

  // Types not picked up by the android.jar scan but for which wrappers are needed.
  private static final Set<String> ADDITIONAL_WRAPPERS = ImmutableSet.of();

  private static final Set<String> GENERIC_NOT_NEEDED =
      ImmutableSet.of("java.util.String", "java.util.Locale$LanguageRange");

  // We need wrappers for only a subset of java.nio.channels. The whole package is marked as
  // needing wrappers and this is the exclusion set.
  private static final Set<String> NOT_NEEDED =
      ImmutableSet.of(
          "java.time.InstantSource", // Introduced after Java 11.
          "java.util.HexFormat",
          "java.util.Locale$IsoCountryCode",
          "java.nio.channels.AsynchronousByteChannel",
          "java.nio.channels.AsynchronousChannelGroup",
          "java.nio.channels.AsynchronousServerSocketChannel",
          "java.nio.channels.AsynchronousSocketChannel",
          "java.nio.channels.MembershipKey",
          "java.nio.channels.MulticastChannel",
          "java.nio.channels.NetworkChannel",
          "java.nio.channels.spi.AsynchronousChannelProvider");

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

  private static final Set<String> MISSING_GENERIC_TYPE_CONVERSION = ImmutableSet.of();

  // Missing conversions in JDK8 and JDK11_LEGACY desugared library that are fixed in JDK11.
  private static final Set<String> MISSING_GENERIC_TYPE_CONVERSION_8 =
      ImmutableSet.of(
          "java.util.Set java.util.stream.Collector.characteristics()",
          "java.util.stream.Stream java.util.stream.Stream.flatMap(java.util.function.Function)",
          "java.util.stream.DoubleStream"
              + " java.util.stream.DoubleStream.flatMap(java.util.function.DoubleFunction)",
          "java.util.stream.DoubleStream"
              + " java.util.stream.Stream.flatMapToDouble(java.util.function.Function)",
          "java.util.stream.IntStream"
              + " java.util.stream.Stream.flatMapToInt(java.util.function.Function)",
          "java.util.stream.IntStream"
              + " java.util.stream.IntStream.flatMap(java.util.function.IntFunction)",
          "java.util.stream.LongStream"
              + " java.util.stream.Stream.flatMapToLong(java.util.function.Function)",
          "java.util.stream.LongStream"
              + " java.util.stream.LongStream.flatMap(java.util.function.LongFunction)",
          "java.util.stream.DoubleStream"
              + " java.util.stream.Stream.mapMultiToDouble(java.util.function.BiConsumer)",
          "java.util.stream.Stream java.util.stream.Stream.mapMulti(java.util.function.BiConsumer)",
          "java.util.stream.IntStream"
              + " java.util.stream.Stream.mapMultiToInt(java.util.function.BiConsumer)",
          "java.util.stream.LongStream"
              + " java.util.stream.Stream.mapMultiToLong(java.util.function.BiConsumer)",
          "java.lang.Object java.lang.StackWalker.walk(java.util.function.Function)");

  // TODO(b/238179854): Investigate how to fix these.
  private static final Set<String> MISSING_GENERIC_TYPE_CONVERSION_PATH =
      ImmutableSet.of(
          "java.lang.Iterable java.nio.file.FileSystem.getFileStores()",
          // The Acl seems to be unusable on Android anyway.
          "java.util.Set java.nio.file.attribute.AclEntry.permissions()",
          "java.util.Set java.nio.file.attribute.AclEntry.flags()",
          "java.util.List java.nio.file.attribute.AclFileAttributeView.getAcl()",
          "void java.nio.file.attribute.AclFileAttributeView.setAcl(java.util.List)");

  // TODO(b/238179854): Investigate how to fix these.
  private static final Set<String> MISSING_GENERIC_TYPE_CONVERSION_FLOW =
      ImmutableSet.of(
          "int java.util.concurrent.SubmissionPublisher.offer(java.lang.Object,"
              + " java.util.function.BiPredicate)",
          "java.util.List java.util.concurrent.SubmissionPublisher.getSubscribers()",
          "void java.util.concurrent.SubmissionPublisher.<init>(java.util.concurrent.Executor, int,"
              + " java.util.function.BiConsumer)",
          "int java.util.concurrent.SubmissionPublisher.offer(java.lang.Object, long,"
              + " java.util.concurrent.TimeUnit, java.util.function.BiPredicate)");

  private final LibraryDesugaringSpecification libraryDesugaringSpecification;

  @Parameters(name = "{0}, spec: {1}")
  public static List<Object[]> data() {
    // TODO(b/236356665): Support JDK11 desugared lib.
    return buildParameters(
        getTestParameters().withNoneRuntime().build(), ImmutableList.of(JDK8, JDK11, JDK11_PATH));
  }

  public ExtractWrapperTypesTest(
      TestParameters parameters, LibraryDesugaringSpecification libraryDesugaringSpecification) {
    parameters.assertNoneRuntime();
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
  }

  // TODO: parameterize to check both api<=23 as well as 23<api<26 for which the spec differs.
  private final AndroidApiLevel minApi = AndroidApiLevel.B;
  private final AndroidApiLevel targetApi = AndroidApiLevel.U;

  private Set<String> getMissingGenericTypeConversions() {
    HashSet<String> missing = new HashSet<>(MISSING_GENERIC_TYPE_CONVERSION);
    if (libraryDesugaringSpecification == JDK8) {
      missing.addAll(MISSING_GENERIC_TYPE_CONVERSION_8);
    }
    if (libraryDesugaringSpecification == JDK11_PATH) {
      missing.addAll(MISSING_GENERIC_TYPE_CONVERSION_PATH);
    }
    if (libraryDesugaringSpecification != JDK8) {
      missing.addAll(MISSING_GENERIC_TYPE_CONVERSION_FLOW);
    }
    return missing;
  }

  @Test
  public void checkConsistency() {
    List<Set<String>> sets =
        ImmutableList.of(ADDITIONAL_WRAPPERS, NEEDED_BUT_NOT_IN_DOCS, NOT_NEEDED_NOT_IN_DOCS);
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

  // Filter on types that do not need to be considered for wrapping.
  private boolean doesNotNeedWrapper(
      String type, Set<String> customConversions, Set<String> maintainType) {
    return excludePackage(type)
        || NOT_NEEDED_NOT_IN_DOCS.contains(type)
        || NOT_NEEDED.contains(type)
        || customConversions.contains(type)
        || maintainType.contains(type);
  }

  private boolean excludePackage(String type) {
    return type.startsWith("java.lang.")
        || type.startsWith("java.security.")
        || type.startsWith("java.net.")
        || type.startsWith("java.awt.")
        || (type.startsWith("java.util.concurrent.")
            && (!type.startsWith("java.util.concurrent.Flow")
                || libraryDesugaringSpecification == JDK8))
        || (!libraryDesugaringSpecification.hasNioFileDesugaring(AndroidApiLevel.B)
            && type.startsWith("java.nio."));
  }

  @Test
  public void test() throws Exception {
    Set<ClassReference> preDesugarTypes = getPreDesugarTypes();

    CodeInspector nonDesugaredJar = new CodeInspector(ToolHelper.getAndroidJar(targetApi));
    DexItemFactory factory = nonDesugaredJar.getFactory();
    DesugaredLibrarySpecification spec =
        DesugaredLibrarySpecificationParser.parseDesugaredLibrarySpecification(
            StringResource.fromFile(libraryDesugaringSpecification.getSpecification()),
            factory,
            null,
            false,
            minApi.getLevel());

    DexApplication app =
        libraryDesugaringSpecification.getAppForTesting(
            new InternalOptions(factory, new Reporter()), spec.isLibraryCompilation());
    MachineDesugaredLibrarySpecification specification =
        spec.toMachineSpecification(app, Timing.empty());

    Set<String> wrappersInSpec =
        specification.getWrappers().keySet().stream()
            .map(DexType::toString)
            .collect(Collectors.toSet());
    Set<String> customConversionsOnly =
        specification.getCustomConversions().keySet().stream()
            .map(DexType::toString)
            .collect(Collectors.toSet());
    // Some types are present both as custom conversions and wrappers, so that the custom conversion
    // can catch some specific cases on top of the wrapper. We are not interested in those.
    customConversionsOnly.removeAll(wrappersInSpec);
    Set<String> maintainTypeInSet =
        specification.getMaintainType().stream().map(DexType::toString).collect(Collectors.toSet());
    Map<String, boolean[]> genericConversionsInSpec = new HashMap<>();
    specification
        .getApiGenericConversion()
        .forEach(
            (method, generics) -> {
              boolean[] indexes = new boolean[generics.length];
              for (int i = 0; i < generics.length; i++) {
                indexes[i] = generics[i] != null;
              }
              genericConversionsInSpec.put(method.toString(), indexes);
            });

    Set<DexEncodedMethod> genericDependencies = new HashSet<>();
    Map<ClassReference, Set<MethodReference>> directWrappers =
        getDirectlyReferencedWrapperTypes(
            specification,
            preDesugarTypes,
            nonDesugaredJar,
            customConversionsOnly,
            maintainTypeInSet,
            genericConversionsInSpec,
            genericDependencies);
    Map<ClassReference, Set<ClassReference>> indirectWrappers =
        getIndirectlyReferencedWrapperTypes(
            directWrappers,
            preDesugarTypes,
            nonDesugaredJar,
            customConversionsOnly,
            maintainTypeInSet,
            specification.getWrappers(),
            genericConversionsInSpec,
            genericDependencies);

    {
      Set<String> missingWrappers = getMissingWrappers(directWrappers, wrappersInSpec);
      assertTrue(
          "Missing direct wrappers:\n" + String.join("\n", missingWrappers),
          missingWrappers.isEmpty());
    }

    // java.util.stream.Collector$Characteristics is required for api generic type conversion
    // on JDK8, but that is not supported on legacy specification used for JDK8 and on old
    // R8 compiler versions.
    int expectedMissingWrappers =
        libraryDesugaringSpecification == JDK8
            ? 1
            : (libraryDesugaringSpecification == JDK11_PATH ? 4 : 0);

    {
      Set<String> missingWrappers = getMissingWrappers(indirectWrappers, wrappersInSpec);
      assertEquals(
          "Missing indirect wrappers:\n" + String.join("\n", missingWrappers),
          expectedMissingWrappers,
          missingWrappers.size());
    }

    {
      Set<String> missingGenericDependency = new HashSet<>();
      for (DexEncodedMethod genericDependency : genericDependencies) {
        if (!specification
            .getApiGenericConversion()
            .containsKey(genericDependency.getReference())) {
          missingGenericDependency.add(genericDependency.getReference().toString());
        }
      }
      Set<String> diff = new HashSet<>(missingGenericDependency);
      Set<String> missing = getMissingGenericTypeConversions();
      diff.removeAll(missing);
      // TODO(b/236356665): There should be no missing conversion.
      assertEquals(
          "Missing generic type conversion:\n" + String.join("\n", diff),
          missing,
          missingGenericDependency);
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
        wrappersInSpec.size() + expectedMissingWrappers);
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

  private Map<ClassReference, Set<MethodReference>> getDirectlyReferencedWrapperTypes(
      MachineDesugaredLibrarySpecification specification,
      Set<ClassReference> preDesugarTypes,
      CodeInspector nonDesugaredJar,
      Set<String> customConversions,
      Set<String> maintainType,
      Map<String, boolean[]> genericConversionsInSpec,
      Set<DexEncodedMethod> genericDependencies) {
    Map<ClassReference, Set<MethodReference>> directWrappers = new HashMap<>();
    nonDesugaredJar.forAllClasses(
        clazz ->
            clazz.forAllMethods(
                method -> {
                  if (!method.isPublic() && !method.isProtected()) {
                    return;
                  }
                  // We check the holder type to avoid dealing with methods on desugared types which
                  // are present in Android.jar and not in the desugared library, specifically on
                  // JDK 8 desugared library.
                  if (specification.isSupported(method.getMethod().getReference())) {
                    return;
                  }
                  Consumer<ClassReference> adder =
                      t ->
                          directWrappers
                              .computeIfAbsent(t, k -> new HashSet<>())
                              .add(method.asMethodReference());
                  forEachType(
                      method,
                      t -> addType(adder, t, preDesugarTypes, customConversions, maintainType),
                      genericConversionsInSpec,
                      genericDependencies);
                }));
    return directWrappers;
  }

  private void forEachType(
      FoundMethodSubject subject,
      Function<String, Boolean> process,
      Map<String, boolean[]> genericConversionsInSpec,
      Set<DexEncodedMethod> generics) {
    boolean[] genericConversions = genericConversionsInSpec.get(subject.toString());
    MethodSignature signature = subject.getFinalSignature().asMethodSignature();
    if (genericConversions == null || !genericConversions[genericConversions.length - 1]) {
      process.apply(signature.type);
    }
    for (int i = 0; i < signature.parameters.length; i++) {
      if (genericConversions == null || !genericConversions[i]) {
        process.apply(signature.parameters[i]);
      }
    }
    // Even if the genericConversions are present, we check the generic types since conversions
    // on such types will happen through the hand written custom wrappers.
    MethodTypeSignature genericSignature = subject.getMethod().getGenericSignature();
    if (genericSignature != null) {
      TypeSignature[] typeSignatures = new TypeSignature[signature.parameters.length + 1];
      for (int i = 0; i < signature.parameters.length; i++) {
        typeSignatures[i] = genericSignature.getParameterTypeSignature(i);
      }
      typeSignatures[signature.parameters.length] = genericSignature.returnType().typeSignature();
      for (TypeSignature typeSignature : typeSignatures) {
        if (typeSignature != null) {
          if ((typeSignature instanceof ClassTypeSignature)) {
            for (FieldTypeSignature typeArgument :
                ((ClassTypeSignature) typeSignature).typeArguments()) {
              if (typeArgument instanceof ClassTypeSignature) {
                String type = descriptorToJavaType(typeArgument.toString()).split("<")[0];
                if (!GENERIC_NOT_NEEDED.contains(type)) {
                  boolean added = process.apply(type);
                  if (added) {
                    generics.add(subject.getMethod());
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  private Map<ClassReference, Set<ClassReference>> getIndirectlyReferencedWrapperTypes(
      Map<ClassReference, Set<MethodReference>> directWrappers,
      Set<ClassReference> existing,
      CodeInspector latest,
      Set<String> customConversions,
      Set<String> maintainType,
      Map<DexType, WrapperDescriptor> wrapperDescriptorMap,
      Map<String, boolean[]> genericConversionsInSpec,
      Set<DexEncodedMethod> genericDependencies) {
    Map<ClassReference, Set<ClassReference>> indirectWrappers = new HashMap<>();
    WorkList<ClassReference> worklist = WorkList.newEqualityWorkList(directWrappers.keySet());
    while (worklist.hasNext()) {
      ClassReference reference = worklist.next();
      ClassSubject clazz = latest.clazz(reference);
      Consumer<ClassReference> adder =
          t -> {
            if (worklist.addIfNotSeen(t)) {
              indirectWrappers.computeIfAbsent(t, k -> new HashSet<>()).add(reference);
            }
          };
      if (clazz.getAccessFlags().isEnum()) {
        // Enum are not really wrapped, instead, each instance is converted to the matching
        // instance, so there is no need to wrap indirect parameters and return types.
        continue;
      }
      clazz.forAllVirtualMethods(
          method -> {
            assertTrue(method.toString(), method.isPublic() || method.isProtected());
            forEachType(
                method,
                t -> addType(adder, t, existing, customConversions, maintainType),
                genericConversionsInSpec,
                genericDependencies);
          });
      WrapperDescriptor descriptor = wrapperDescriptorMap.get(clazz.getDexProgramClass().getType());
      if (descriptor != null) {
        for (DexType subwrapper : descriptor.getSubwrappers()) {
          addType(adder, subwrapper.getTypeName(), existing, customConversions, maintainType);
        }
      }
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

  private boolean addType(
      Consumer<ClassReference> additions,
      String type,
      Set<ClassReference> preDesugarTypes,
      Set<String> customConversions,
      Set<String> maintainType) {
    if (type.equals("void")) {
      return false;
    }
    TypeReference typeReference = Reference.typeFromTypeName(type);
    if (typeReference.isArray()) {
      typeReference = typeReference.asArray().getBaseType();
    }
    if (typeReference.isClass()) {
      ClassReference clazz = typeReference.asClass();
      String clazzType = descriptorToJavaType(clazz.getDescriptor());
      if (clazzType.startsWith("java.")
          && !doesNotNeedWrapper(clazzType, customConversions, maintainType)
          // FileChannel is there since B but it needs wrapping due to recently added interfaces.
          && !preDesugarTypes.contains(clazz)) {
        additions.accept(clazz);
        return true;
      }
    }
    return false;
  }
}
