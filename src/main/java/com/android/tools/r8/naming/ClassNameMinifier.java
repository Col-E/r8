// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.DescriptorUtils.DESCRIPTOR_PACKAGE_SEPARATOR;
import static com.android.tools.r8.utils.DescriptorUtils.INNER_CLASS_SEPARATOR;
import static com.android.tools.r8.utils.DescriptorUtils.getClassBinaryNameFromDescriptor;
import static com.android.tools.r8.utils.DescriptorUtils.getDescriptorFromClassBinaryName;
import static com.android.tools.r8.utils.DescriptorUtils.getPackageBinaryNameFromJavaType;

import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexDefinition;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.InnerClassAttribute;
import com.android.tools.r8.naming.signature.GenericSignatureAction;
import com.android.tools.r8.naming.signature.GenericSignatureParser;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.shaking.Enqueuer.AppInfoWithLiveness;
import com.android.tools.r8.shaking.RootSetBuilder.RootSet;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.InternalOptions.PackageObfuscationMode;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.lang.reflect.GenericSignatureFormatError;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

class ClassNameMinifier {

  private final AppInfoWithLiveness appInfo;
  private final Reporter reporter;
  private final PackageObfuscationMode packageObfuscationMode;
  private final boolean isAccessModificationAllowed;
  private final Set<String> noObfuscationPrefixes = Sets.newHashSet();
  private final Set<String> usedPackagePrefixes = Sets.newHashSet();
  private final Set<DexString> usedTypeNames = Sets.newIdentityHashSet();

  private final Map<DexType, DexString> renaming = Maps.newIdentityHashMap();
  private final Map<String, Namespace> states = new HashMap<>();
  private final List<String> packageDictionary;
  private final List<String> classDictionary;
  private final boolean keepInnerClassStructure;

  private final Set<DexType> noObfuscationTypes;
  private final Set<DexType> keepPackageName;

  private final Namespace topLevelState;

  private final GenericSignatureRewriter genericSignatureRewriter = new GenericSignatureRewriter();

  private final GenericSignatureParser<DexType> genericSignatureParser =
      new GenericSignatureParser<>(genericSignatureRewriter);

  ClassNameMinifier(
      AppInfoWithLiveness appInfo,
      RootSet rootSet,
      InternalOptions options) {
    this.appInfo = appInfo;
    this.reporter = options.reporter;
    this.packageObfuscationMode = options.getProguardConfiguration().getPackageObfuscationMode();
    this.isAccessModificationAllowed =
        options.getProguardConfiguration().isAccessModificationAllowed();
    this.packageDictionary = options.getProguardConfiguration().getPackageObfuscationDictionary();
    this.classDictionary = options.getProguardConfiguration().getClassObfuscationDictionary();
    this.keepInnerClassStructure = options.getProguardConfiguration().getKeepAttributes().signature;
    this.noObfuscationTypes =
        DexReference.filterDexType(
            DexDefinition.mapToReference(rootSet.noObfuscation.stream()))
            .collect(Collectors.toSet());
    this.keepPackageName =
        DexReference.filterDexType(
            DexDefinition.mapToReference(rootSet.keepPackageName.stream()))
            .collect(Collectors.toSet());

    // Initialize top-level naming state.
    topLevelState = new Namespace(
        getPackageBinaryNameFromJavaType(options.getProguardConfiguration().getPackagePrefix()));
    states.computeIfAbsent("", k -> topLevelState);
  }

  static class ClassRenaming {
    protected final Map<String, String> packageRenaming;
    protected final Map<DexType, DexString> classRenaming;

    private ClassRenaming(
        Map<DexType, DexString> classRenaming, Map<String, String> packageRenaming) {
      this.classRenaming = classRenaming;
      this.packageRenaming = packageRenaming;
    }
  }

  ClassRenaming computeRenaming(Timing timing) {
    // Use deterministic class order to make sure renaming is deterministic.
    Iterable<DexProgramClass> classes = appInfo.classesWithDeterministicOrder();
    // Collect names we have to keep.
    timing.begin("reserve");
    for (DexClass clazz : classes) {
      if (noObfuscationTypes.contains(clazz.type)) {
        assert !renaming.containsKey(clazz.type);
        registerClassAsUsed(clazz.type);
      }
    }
    timing.end();

    timing.begin("rename-classes");
    for (DexClass clazz : classes) {
      if (!renaming.containsKey(clazz.type)) {
        clazz.annotations = clazz.annotations.keepIf(this::isNotKotlinMetadata);
        DexString renamed = computeName(clazz.type);
        renaming.put(clazz.type, renamed);
      }
    }
    timing.end();

    timing.begin("rename-dangling-types");
    for (DexClass clazz : classes) {
      renameDanglingTypes(clazz);
    }
    timing.end();

    timing.begin("rename-generic");
    renameTypesInGenericSignatures();
    timing.end();

    timing.begin("rename-arrays");
    appInfo.dexItemFactory.forAllTypes(this::renameArrayTypeIfNeeded);
    timing.end();

    return new ClassRenaming(Collections.unmodifiableMap(renaming), getPackageRenaming());
  }

  private Map<String, String> getPackageRenaming() {
    ImmutableMap.Builder<String, String> packageRenaming = ImmutableMap.builder();
    for (Entry<String, Namespace> entry : states.entrySet()) {
      String originalPackageName = entry.getKey();
      String minifiedPackageName = entry.getValue().getPackageName();
      if (!minifiedPackageName.equals(originalPackageName)) {
        packageRenaming.put(originalPackageName, minifiedPackageName);
      }
    }
    return packageRenaming.build();
  }

  private void renameDanglingTypes(DexClass clazz) {
    clazz.forEachMethod(this::renameDanglingTypesInMethod);
    clazz.forEachField(this::renameDanglingTypesInField);
  }

  private void renameDanglingTypesInField(DexEncodedField field) {
    renameDanglingType(field.field.type);
  }

  private void renameDanglingTypesInMethod(DexEncodedMethod method) {
    DexProto proto = method.method.proto;
    renameDanglingType(proto.returnType);
    for (DexType type : proto.parameters.values) {
      renameDanglingType(type);
    }
  }

  private void renameDanglingType(DexType type) {
    if (appInfo.wasPruned(type)
        && !renaming.containsKey(type)
        && !noObfuscationTypes.contains(type)) {
      // We have a type that is defined in the program source but is only used in a proto or
      // return type. As we don't need the class, we can rename it to anything as long as it is
      // unique.
      assert appInfo.definitionFor(type) == null;
      renaming.put(type, topLevelState.nextTypeName());
    }
  }

  private void parseError(
      DexDefinition item, Origin origin, String signature, GenericSignatureFormatError e) {
    StringBuilder message = new StringBuilder("Invalid signature '");
    message.append(signature);
    message.append("' for ");
    if (item.isDexClass()) {
      message.append("class ");
      message.append((item.asDexClass()).getType().toSourceString());
    } else if (item.isDexEncodedField()) {
      message.append("field ");
      message.append(item.toSourceString());
    } else {
      assert item.isDexEncodedMethod();
      message.append("method ");
      message.append(item.toSourceString());
    }
    message.append(".\n");
    message.append("Signature is ignored and will not be present in the output.\n");
    message.append("Parser error: ");
    message.append(e.getMessage());
    reporter.warning(new StringDiagnostic(message.toString(), origin));
  }

  private void renameTypesInGenericSignatures() {
    for (DexClass clazz : appInfo.classes()) {
      clazz.annotations =
          rewriteGenericSignatures(
              clazz.annotations,
              genericSignatureParser::parseClassSignature,
              (signature, e) -> parseError(clazz, clazz.getOrigin(), signature, e));
      clazz.forEachField(
          field ->
              field.annotations =
                  rewriteGenericSignatures(
                      field.annotations,
                      genericSignatureParser::parseFieldSignature,
                      (signature, e) -> parseError(field, clazz.getOrigin(), signature, e)));
      clazz.forEachMethod(
          method ->
              method.annotations =
                  rewriteGenericSignatures(
                      method.annotations,
                      genericSignatureParser::parseMethodSignature,
                      (signature, e) -> parseError(method, clazz.getOrigin(), signature, e)));
    }
  }

  private DexAnnotationSet rewriteGenericSignatures(
      DexAnnotationSet annotations,
      Consumer<String> parser,
      BiConsumer<String, GenericSignatureFormatError> parseError) {
    // There can be no more than one signature annotation in an annotation set.
    final int VALID = -1;
    int invalid = VALID;
    for (int i = 0; i < annotations.annotations.length && invalid == VALID; i++) {
      DexAnnotation annotation = annotations.annotations[i];
      if (DexAnnotation.isSignatureAnnotation(annotation, appInfo.dexItemFactory)) {
        String signature = DexAnnotation.getSignature(annotation);
        try {
          parser.accept(signature);
          annotations.annotations[i] = DexAnnotation.createSignatureAnnotation(
              genericSignatureRewriter.getRenamedSignature(),
              appInfo.dexItemFactory);
        } catch (GenericSignatureFormatError e) {
          parseError.accept(signature, e);
          invalid = i;
        }
      }
    }

    // Return the rewritten signatures if it was valid and could be rewritten.
    if (invalid == VALID) {
      return annotations;
    }
    // Remove invalid signature if found.
    DexAnnotation[] prunedAnnotations =
        new DexAnnotation[annotations.annotations.length - 1];
    int dest = 0;
    for (int i = 0; i < annotations.annotations.length; i++) {
      if (i != invalid) {
        prunedAnnotations[dest++] = annotations.annotations[i];
      }
    }
    assert dest == prunedAnnotations.length;
    return new DexAnnotationSet(prunedAnnotations);
  }

  /**
   * Registers the given type as used.
   * <p>
   * When {@link #keepInnerClassStructure} is true, keeping the name of an inner class will
   * automatically also keep the name of the outer class, as otherwise the structure would be
   * invalidated.
   */
  private void registerClassAsUsed(DexType type) {
    renaming.put(type, type.descriptor);
    registerPackagePrefixesAsUsed(
        getParentPackagePrefix(getClassBinaryNameFromDescriptor(type.descriptor.toSourceString())));
    usedTypeNames.add(type.descriptor);
    if (keepInnerClassStructure) {
      DexType outerClass = getOutClassForType(type);
      if (outerClass != null) {
        if (!renaming.containsKey(outerClass) && !noObfuscationTypes.contains(outerClass)) {
          // The outer class was not previously kept and will not be kept.
          // We have to force keep the outer class now.
          registerClassAsUsed(outerClass);
        }
      }
    }
  }

  /**
   * Registers the given package prefix and all of parent packages as used.
   */
  private void registerPackagePrefixesAsUsed(String packagePrefix) {
    // If -allowaccessmodification is not set, we may keep classes in their original packages,
    // accounting for package-private accesses.
    if (!isAccessModificationAllowed) {
      noObfuscationPrefixes.add(packagePrefix);
    }
    String usedPrefix = packagePrefix;
    while (usedPrefix.length() > 0) {
      usedPackagePrefixes.add(usedPrefix);
      usedPrefix = getParentPackagePrefix(usedPrefix);
    }
  }

  private DexType getOutClassForType(DexType type) {
    DexClass clazz = appInfo.definitionFor(type);
    if (clazz == null) {
      return null;
    }
    // We do not need to preserve the names for local or anonymous classes, as they do not result
    // in a member type declaration and hence cannot be referenced as nested classes in
    // method signatures.
    // See https://docs.oracle.com/javase/specs/jls/se7/html/jls-8.html#jls-8.5.
    if (clazz.getEnclosingMethod() != null) {
      return null;
    }
    for (InnerClassAttribute innerClassAttribute : clazz.getInnerClasses()) {
      if (innerClassAttribute.getInner() == type) {
        // For DEX inputs this could result in returning the outer class of a local class since we
        // can't distinguish it from a member class based on just the enclosing-class annotation.
        // We could filter out the local classes by looking for a corresponding entry in the
        // inner-classes attribute table which must exist only for member classes. Since DEX files
        // are not a supported mode for R8 we just return the outer class in both cases.
        return innerClassAttribute.getOuter();
      }
    }
    return null;
  }

  private DexString computeName(DexType type) {
    Namespace state = null;
    if (keepInnerClassStructure) {
      // When keeping the nesting structure of inner classes, we have to insert the name
      // of the outer class for the $ prefix.
      DexType outerClass = getOutClassForType(type);
      if (outerClass != null) {
        state = getStateForOuterClass(outerClass);
      }
    }
    if (state == null) {
      state = getStateForClass(type);
    }
    return state.nextTypeName();
  }

  private Namespace getStateForClass(DexType type) {
    String packageName = getPackageBinaryNameFromJavaType(type.getPackageDescriptor());
    // Check whether the given class should be kept.
    // or check whether the given class belongs to a package that is kept for another class.
    if (keepPackageName.contains(type)
        || noObfuscationPrefixes.contains(packageName)) {
      return states.computeIfAbsent(packageName, Namespace::new);
    }
    Namespace state = topLevelState;
    switch (packageObfuscationMode) {
      case NONE:
        // For general obfuscation, rename all the involved package prefixes.
        state = getStateForPackagePrefix(packageName);
        break;
      case REPACKAGE:
        // For repackaging, all classes are repackaged to a single package.
        state = topLevelState;
        break;
      case FLATTEN:
        // For flattening, all packages are repackaged to a single package.
        state = states.computeIfAbsent(packageName, k -> {
          String renamedPackagePrefix = topLevelState.nextPackagePrefix();
          return new Namespace(renamedPackagePrefix);
        });
        break;
    }
    return state;
  }

  private Namespace getStateForPackagePrefix(String prefix) {
    Namespace state = states.get(prefix);
    if (state == null) {
      // Calculate the parent package prefix, e.g., La/b/c -> La/b
      String parentPackage = getParentPackagePrefix(prefix);
      Namespace superState;
      if (noObfuscationPrefixes.contains(parentPackage)) {
        // Restore a state for parent package prefix if it should be kept.
        superState = states.computeIfAbsent(parentPackage, Namespace::new);
      } else {
        // Create a state for parent package prefix, if necessary, in a recursive manner.
        // That recursion should end when the parent package hits the top-level, "".
        superState = getStateForPackagePrefix(parentPackage);
      }
      // From the super state, get a renamed package prefix for the current level.
      String renamedPackagePrefix = superState.nextPackagePrefix();
      // Create a new state, which corresponds to a new name space, for the current level.
      state = new Namespace(renamedPackagePrefix);
      states.put(prefix, state);
    }
    return state;
  }

  private Namespace getStateForOuterClass(DexType outer) {
    String prefix = getClassBinaryNameFromDescriptor(outer.toDescriptorString());
    Namespace state = states.get(prefix);
    if (state == null) {
      // Create a naming state with this classes renaming as prefix.
      DexString renamed = renaming.get(outer);
      if (renamed == null) {
        // The outer class has not been renamed yet, so rename the outer class first.
        // Note that here we proceed unconditionally---w/o regards to the existence of the outer
        // class: it could be the case that the outer class is not renamed because it's shrunk.
        // Even though that's the case, we can _implicitly_ assign a new name to the outer class
        // and then use that renamed name as a base prefix for the current inner class.
        renamed = computeName(outer);
        renaming.put(outer, renamed);
      }
      String binaryName = getClassBinaryNameFromDescriptor(renamed.toString());
      state = new Namespace(binaryName, INNER_CLASS_SEPARATOR);
      states.put(prefix, state);
    }
    return state;
  }

  private void renameArrayTypeIfNeeded(DexType type) {
    if (type.isArrayType()) {
      DexType base = type.toBaseType(appInfo.dexItemFactory);
      DexString value = renaming.get(base);
      if (value != null) {
        int dimensions = type.descriptor.numberOfLeadingSquareBrackets();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < dimensions; i++) {
          builder.append('[');
        }
        builder.append(value.toString());
        DexString descriptor = appInfo.dexItemFactory.createString(builder.toString());
        renaming.put(type, descriptor);
      }
    }
  }

  private class Namespace {

    private final String packageName;
    private final char[] packagePrefix;
    private int typeCounter = 1;
    private int packageCounter = 1;
    private final Iterator<String> packageDictionaryIterator;
    private final Iterator<String> classDictionaryIterator;

    Namespace(String packageName) {
      this(packageName, DESCRIPTOR_PACKAGE_SEPARATOR);
    }

    Namespace(String packageName, char separator) {
      this.packageName = packageName;
      this.packagePrefix = ("L" + packageName
          // L or La/b/ (or La/b/C$)
          + (packageName.isEmpty() ? "" : separator))
          .toCharArray();
      this.packageDictionaryIterator = packageDictionary.iterator();
      this.classDictionaryIterator = classDictionary.iterator();
    }

    public String getPackageName() {
      return packageName;
    }

    private String nextSuggestedNameForClass() {
      StringBuilder nextName = new StringBuilder();
      if (classDictionaryIterator.hasNext()) {
        nextName.append(packagePrefix).append(classDictionaryIterator.next()).append(';');
        return nextName.toString();
      } else {
        return StringUtils.numberToIdentifier(packagePrefix, typeCounter++, true);
      }
    }

    DexString nextTypeName() {
      DexString candidate;
      do {
        candidate = appInfo.dexItemFactory.createString(nextSuggestedNameForClass());
      } while (usedTypeNames.contains(candidate));
      usedTypeNames.add(candidate);
      return candidate;
    }

    private String nextSuggestedNameForSubpackage() {
      StringBuilder nextName = new StringBuilder();
      // Note that the differences between this method and the other variant for class renaming are
      // 1) this one uses the different dictionary and counter,
      // 2) this one does not append ';' at the end, and
      // 3) this one removes 'L' at the beginning to make the return value a binary form.
      if (packageDictionaryIterator.hasNext()) {
        nextName.append(packagePrefix).append(packageDictionaryIterator.next());
      } else {
        nextName.append(StringUtils.numberToIdentifier(packagePrefix, packageCounter++, false));
      }
      return nextName.toString().substring(1);
    }

    String nextPackagePrefix() {
      String candidate;
      do {
        candidate = nextSuggestedNameForSubpackage();
      } while (usedPackagePrefixes.contains(candidate));
      usedPackagePrefixes.add(candidate);
      return candidate;
    }
  }

  private class GenericSignatureRewriter implements GenericSignatureAction<DexType> {

    private StringBuilder renamedSignature;

    public String getRenamedSignature() {
      return renamedSignature.toString();
    }

    @Override
    public void parsedSymbol(char symbol) {
      renamedSignature.append(symbol);
    }

    @Override
    public void parsedIdentifier(String identifier) {
      renamedSignature.append(identifier);
    }

    @Override
    public DexType parsedTypeName(String name) {
      DexType type = appInfo.dexItemFactory.createType(getDescriptorFromClassBinaryName(name));
      DexString renamedDescriptor = renaming.getOrDefault(type, type.descriptor);
      renamedSignature.append(getClassBinaryNameFromDescriptor(renamedDescriptor.toString()));
      return type;
    }

    @Override
    public DexType parsedInnerTypeName(DexType enclosingType, String name) {
      assert enclosingType.isClassType();
      String enclosingDescriptor = enclosingType.toDescriptorString();
      DexType type =
          appInfo.dexItemFactory.createType(
              getDescriptorFromClassBinaryName(
                  getClassBinaryNameFromDescriptor(enclosingDescriptor)
                      + Minifier.INNER_CLASS_SEPARATOR
                      + name));
      String enclosingRenamedBinaryName =
          getClassBinaryNameFromDescriptor(
              renaming.getOrDefault(enclosingType, enclosingType.descriptor).toString());
      DexString renamedDescriptor = renaming.get(type);
      if (renamedDescriptor != null) {
        // Pick the renamed inner class from the fully renamed binary name.
        String fullRenamedBinaryName =
            getClassBinaryNameFromDescriptor(renamedDescriptor.toString());
        renamedSignature.append(
            fullRenamedBinaryName.substring(enclosingRenamedBinaryName.length() + 1));
      } else {
        // Did not find the class - keep the inner class name as is.
        // TODO(110085899): Warn about missing classes in signatures?
        renamedSignature.append(name);
      }
      return type;
    }

    @Override
    public void start() {
      renamedSignature = new StringBuilder();
    }

    @Override
    public void stop() {
      // nothing to do
    }
  }

  /**
   * Compute parent package prefix from the given package prefix.
   *
   * @param packagePrefix i.e. "Ljava/lang"
   * @return parent package prefix i.e. "Ljava"
   */
  static String getParentPackagePrefix(String packagePrefix) {
    int i = packagePrefix.lastIndexOf(DescriptorUtils.DESCRIPTOR_PACKAGE_SEPARATOR);
    if (i < 0) {
      return "";
    }
    return packagePrefix.substring(0, i);
  }

  private boolean isNotKotlinMetadata(DexAnnotation annotation) {
    return annotation.annotation.type != appInfo.dexItemFactory.kotlin.metadata.kotlinMetadataType;
  }
}
