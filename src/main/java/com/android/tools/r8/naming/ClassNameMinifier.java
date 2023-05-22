// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.DescriptorUtils.DESCRIPTOR_PACKAGE_SEPARATOR;
import static com.android.tools.r8.utils.DescriptorUtils.INNER_CLASS_SEPARATOR;
import static com.android.tools.r8.utils.DescriptorUtils.computeInnerClassSeparator;
import static com.android.tools.r8.utils.DescriptorUtils.getClassBinaryNameFromDescriptor;
import static com.android.tools.r8.utils.DescriptorUtils.getPackageBinaryNameFromJavaType;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.InnerClassAttribute;
import com.android.tools.r8.graph.ProgramOrClasspathClass;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Predicate;

class ClassNameMinifier {

  private final AppView<AppInfoWithLiveness> appView;
  private final ClassNamingStrategy classNamingStrategy;
  private final Iterable<? extends ProgramOrClasspathClass> classes;
  private final Set<String> usedTypeNames = Sets.newHashSet();
  private final Map<DexType, DexString> renaming = Maps.newIdentityHashMap();
  private final Map<String, Namespace> states = new HashMap<>();
  private final boolean keepInnerClassStructure;

  private final Namespace topLevelState;
  private final boolean allowMixedCaseNaming;
  private final Predicate<String> isUsed;

  ClassNameMinifier(
      AppView<AppInfoWithLiveness> appView,
      ClassNamingStrategy classNamingStrategy,
      Iterable<? extends ProgramOrClasspathClass> classes) {
    this.appView = appView;
    this.classNamingStrategy = classNamingStrategy;
    this.classes = classes;
    InternalOptions options = appView.options();
    this.keepInnerClassStructure = options.keepInnerClassStructure();

    // Initialize top-level naming state.
    topLevelState = new Namespace("");
    states.put("", topLevelState);

    if (options.getProguardConfiguration().hasDontUseMixedCaseClassnames()) {
      allowMixedCaseNaming = false;
      isUsed = candidate -> usedTypeNames.contains(StringUtils.toLowerCase(candidate));
    } else {
      allowMixedCaseNaming = true;
      isUsed = usedTypeNames::contains;
    }
  }

  private void setUsedTypeName(String typeName) {
    usedTypeNames.add(allowMixedCaseNaming ? typeName : StringUtils.toLowerCase(typeName));
  }

  static class ClassRenaming {
    final Map<String, String> packageRenaming;
    final Map<DexType, DexString> classRenaming;

    private ClassRenaming(
        Map<DexType, DexString> classRenaming, Map<String, String> packageRenaming) {
      this.classRenaming = classRenaming;
      this.packageRenaming = packageRenaming;
    }
  }

  ClassRenaming computeRenaming(Timing timing) {
    // Collect names we have to keep.
    timing.begin("reserve");
    for (ProgramOrClasspathClass clazz : classes) {
      DexString descriptor = classNamingStrategy.reservedDescriptor(clazz.getType());
      if (descriptor != null) {
        assert !renaming.containsKey(clazz.getType());
        registerClassAsUsed(clazz.getType(), descriptor);
      }
    }
    appView
        .appInfo()
        .getMissingClasses()
        .forEach(missingClass -> registerClassAsUsed(missingClass, missingClass.getDescriptor()));
    timing.end();

    timing.begin("rename-classes");
    for (ProgramOrClasspathClass clazz : classes) {
      if (!renaming.containsKey(clazz.getType())) {
        DexString renamed = computeName(clazz.getType());
        renaming.put(clazz.getType(), renamed);
        assert verifyMemberRenamingOfInnerClasses(clazz.asDexClass(), renamed);
      }
    }
    timing.end();

    timing.begin("rename-dangling-types");
    for (ProgramOrClasspathClass clazz : classes) {
      renameDanglingTypes(clazz);
    }
    timing.end();

    return new ClassRenaming(Collections.unmodifiableMap(renaming), getPackageRenaming());
  }

  private boolean verifyMemberRenamingOfInnerClasses(DexClass clazz, DexString renamed) {
    // If the class is a member class and it has used $ separator, its renamed name should have
    // the same separator (as long as inner-class attribute is honored).
    assert !keepInnerClassStructure
            || !clazz.isMemberClass()
            || !clazz.getType().getInternalName().contains(String.valueOf(INNER_CLASS_SEPARATOR))
            || renamed.toString().contains(String.valueOf(INNER_CLASS_SEPARATOR))
            || classNamingStrategy.isRenamedByApplyMapping(clazz.getType())
        : clazz + " -> " + renamed;
    return true;
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

  private void renameDanglingTypes(ProgramOrClasspathClass clazz) {
    clazz.forEachClassMethod(this::renameDanglingTypesInMethod);
    clazz.forEachClassField(this::renameDanglingTypesInField);
  }

  private void renameDanglingTypesInField(DexClassAndField field) {
    renameDanglingType(field.getReference().type);
  }

  private void renameDanglingTypesInMethod(DexClassAndMethod method) {
    DexProto proto = method.getReference().proto;
    renameDanglingType(proto.returnType);
    for (DexType type : proto.parameters.values) {
      renameDanglingType(type);
    }
  }

  private void renameDanglingType(DexType type) {
    if (appView.appInfo().wasPruned(type) && !renaming.containsKey(type)) {
      // We have a type that is defined in the program source but is only used in a proto or
      // return type. As we don't need the class, we can rename it to anything as long as it is
      // unique.
      assert appView.definitionFor(type) == null;
      DexString descriptor = classNamingStrategy.reservedDescriptor(type);
      renaming.put(type, descriptor != null ? descriptor : topLevelState.nextTypeName(type));
    }
  }

  private void registerClassAsUsed(DexType type, DexString descriptor) {
    renaming.put(type, descriptor);
    setUsedTypeName(descriptor.toString());
    if (keepInnerClassStructure) {
      DexType outerClass = getOutClassForType(type);
      if (outerClass != null) {
        if (!renaming.containsKey(outerClass)
            && classNamingStrategy.reservedDescriptor(outerClass) == null) {
          // The outer class was not previously kept and will not be kept.
          // We have to force keep the outer class now.
          registerClassAsUsed(outerClass, outerClass.descriptor);
        }
      }
    }
  }

  private DexType getOutClassForType(DexType type) {
    DexClass clazz = appView.definitionFor(type);
    if (clazz == null) {
      return null;
    }
    // For DEX inputs this could result in returning the outer class of a local class since we
    // can't distinguish it from a member class based on just the enclosing-class annotation.
    // We could filter out the local classes by looking for a corresponding entry in the
    // inner-classes attribute table which must exist only for member classes. Since DEX files
    // are not a supported input for R8 we just return the outer class in both cases.
    InnerClassAttribute attribute = clazz.getInnerClassAttributeForThisClass();
    if (attribute == null) {
      return null;
    }
    return attribute.getLiveContext(appView);
  }

  private DexString computeName(DexType type) {
    Namespace state = null;
    if (keepInnerClassStructure) {
      // When keeping the nesting structure of inner classes, bind this type to the live context.
      // Note that such live context might not be always the enclosing class. E.g., a local class
      // does not have a direct enclosing class, but we use the holder of the enclosing method here.
      DexType outerClass = getOutClassForType(type);
      if (outerClass != null) {
        DexClass clazz = appView.definitionFor(type);
        assert clazz != null;
        InnerClassAttribute attribute = clazz.getInnerClassAttributeForThisClass();
        assert attribute != null;
        // Note that, to be consistent with the way inner-class attribute is written via minifier
        // lens, we are using attribute's outer-class, not the live context.
        String separator =
            computeInnerClassSeparator(attribute.getOuter(), type, attribute.getInnerName());
        if (separator == null) {
          separator = String.valueOf(INNER_CLASS_SEPARATOR);
        }
        state = getStateForOuterClass(outerClass, separator);
      }
    }
    if (state == null) {
      state = getStateForClass(type);
    }
    return state.nextTypeName(type);
  }

  private Namespace getStateForClass(DexType type) {
    String packageName = getPackageBinaryNameFromJavaType(type.getPackageDescriptor());
    // Packages are repackaged and obfuscated when doing repackaging.
    return states.computeIfAbsent(packageName, Namespace::new);
  }

  private Namespace getStateForOuterClass(DexType outer, String innerClassSeparator) {
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
      state = new Namespace(binaryName, innerClassSeparator);
      states.put(prefix, state);
    }
    return state;
  }

  protected class Namespace implements InternalNamingState {

    private final String packageName;
    private final char[] packagePrefix;
    private int dictionaryIndex = 0;
    private int nameIndex = 1;

    Namespace(String packageName) {
      this(packageName, String.valueOf(DESCRIPTOR_PACKAGE_SEPARATOR));
    }

    Namespace(String packageName, String separator) {
      this.packageName = packageName;
      this.packagePrefix = ("L" + packageName
          // L or La/b/ (or La/b/C$)
          + (packageName.isEmpty() ? "" : separator))
          .toCharArray();
    }

    public String getPackageName() {
      return packageName;
    }

    DexString nextTypeName(DexType type) {
      DexString candidate = classNamingStrategy.next(type, packagePrefix, this, isUsed);
      assert !usedTypeNames.contains(candidate.toString());
      setUsedTypeName(candidate.toString());
      return candidate;
    }

    @Override
    public int getDictionaryIndex() {
      return dictionaryIndex;
    }

    @Override
    public int incrementDictionaryIndex() {
      return dictionaryIndex++;
    }

    @Override
    public int incrementNameIndex() {
      return nameIndex++;
    }
  }

  protected interface ClassNamingStrategy {
    DexString next(
        DexType type, char[] packagePrefix, InternalNamingState state, Predicate<String> isUsed);

    /**
     * Returns the reserved descriptor for a type. If the type is not allowed to be obfuscated
     * (minified) it will return the original type descriptor. If applymapping is used, it will try
     * to return the applied name such that it can be reserved. Otherwise, if there are no
     * reservations, it will return null.
     *
     * @param type The type to find a reserved descriptor for
     * @return The reserved descriptor
     */
    DexString reservedDescriptor(DexType type);

    boolean isRenamedByApplyMapping(DexType type);
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
}
