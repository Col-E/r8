// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.android.tools.r8.ir.desugar.DesugaredLibraryWrapperSynthesizer.TYPE_WRAPPER_SUFFIX;
import static com.android.tools.r8.ir.desugar.DesugaredLibraryWrapperSynthesizer.VIVIFIED_TYPE_WRAPPER_SUFFIX;
import static com.android.tools.r8.ir.desugar.InterfaceMethodRewriter.COMPANION_CLASS_NAME_SUFFIX;
import static com.android.tools.r8.ir.desugar.InterfaceMethodRewriter.DISPATCH_CLASS_NAME_SUFFIX;
import static com.android.tools.r8.ir.desugar.InterfaceMethodRewriter.EMULATE_LIBRARY_CLASS_NAME_SUFFIX;
import static com.android.tools.r8.ir.desugar.LambdaRewriter.LAMBDA_CLASS_NAME_PREFIX;
import static com.android.tools.r8.ir.desugar.LambdaRewriter.LAMBDA_GROUP_CLASS_NAME_PREFIX;
import static com.android.tools.r8.ir.optimize.enums.UnboxedEnumMemberRelocator.ENUM_UNBOXING_UTILITY_CLASS_SUFFIX;

import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.horizontalclassmerging.SyntheticArgumentClass;
import com.android.tools.r8.ir.desugar.DesugaredLibraryRetargeter;
import com.android.tools.r8.ir.desugar.NestBasedAccessDesugaring;
import com.android.tools.r8.ir.desugar.TwrCloseResourceRewriter;
import com.android.tools.r8.ir.optimize.ServiceLoaderRewriter;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.synthesis.SyntheticItems;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions.OutlineOptions;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class DexType extends DexReference implements PresortedComparable<DexType> {
  public static final DexType[] EMPTY_ARRAY = {};

  // Bundletool is merging classes that may originate from a build with an old version of R8.
  // Allow merging of classes that use names from older versions of R8.
  private static List<String> OLD_SYNTHESIZED_NAMES =
      ImmutableList.of("$r8$backportedMethods$utility", "$r8$java8methods$utility");

  public final DexString descriptor;
  private String toStringCache = null;

  DexType(DexString descriptor) {
    assert !descriptor.toString().contains(".") : "Malformed descriptor: " + descriptor.toString();
    this.descriptor = descriptor;
  }

  @Override
  public DexType getContextType() {
    return this;
  }

  public DexString getDescriptor() {
    return descriptor;
  }

  @Override
  public int computeHashCode() {
    return descriptor.hashCode();
  }

  @Override
  public boolean computeEquals(Object other) {
    if (other instanceof DexType) {
      return descriptor.equals(((DexType) other).descriptor);
    }
    return false;
  }

  public boolean classInitializationMayHaveSideEffects(AppView<?> appView) {
    return classInitializationMayHaveSideEffects(
        appView, Predicates.alwaysFalse(), Sets.newIdentityHashSet());
  }

  public boolean classInitializationMayHaveSideEffects(
      AppView<?> appView, Predicate<DexType> ignore, Set<DexType> seen) {
    DexClass clazz = appView.definitionFor(this);
    return clazz == null || clazz.classInitializationMayHaveSideEffects(appView, ignore, seen);
  }

  public boolean initializationOfParentTypesMayHaveSideEffects(AppView<?> appView) {
    return initializationOfParentTypesMayHaveSideEffects(
        appView, Predicates.alwaysFalse(), Sets.newIdentityHashSet());
  }

  public boolean initializationOfParentTypesMayHaveSideEffects(
      AppView<?> appView, Predicate<DexType> ignore, Set<DexType> seen) {
    DexClass clazz = appView.definitionFor(this);
    return clazz == null
        || clazz.initializationOfParentTypesMayHaveSideEffects(appView, ignore, seen);
  }

  public boolean isAlwaysNull(AppView<AppInfoWithLiveness> appView) {
    if (isClassType()) {
      DexProgramClass clazz = asProgramClassOrNull(appView.definitionFor(this));
      if (clazz == null) {
        return false;
      }
      if (appView.options().enableUninstantiatedTypeOptimizationForInterfaces) {
        return !appView.appInfo().isInstantiatedDirectlyOrIndirectly(clazz);
      } else {
        return !clazz.isInterface() && !appView.appInfo().isInstantiatedDirectlyOrIndirectly(clazz);
      }
    }
    return false;
  }

  public boolean isSamePackage(DexType other) {
    return getPackageDescriptor().equals(other.getPackageDescriptor());
  }

  public String toDescriptorString() {
    return descriptor.toString();
  }

  public String toBinaryName() {
    String descriptor = toDescriptorString();
    assert descriptor.length() > 1
        && descriptor.charAt(0) == 'L'
        && descriptor.charAt(descriptor.length() - 1) == ';';
    return descriptor.substring(1, descriptor.length() - 1);
  }

  @Override
  public <T> T apply(
      Function<DexType, T> classConsumer,
      Function<DexField, T> fieldConsumer,
      Function<DexMethod, T> methodConsumer) {
    return classConsumer.apply(this);
  }

  @Override
  public void accept(
      Consumer<DexType> classConsumer,
      Consumer<DexField> fieldConsumer,
      Consumer<DexMethod> methodConsumer) {
    classConsumer.accept(this);
  }

  @Override
  public <T> void accept(
      BiConsumer<DexType, T> classConsumer,
      BiConsumer<DexField, T> fieldConsumer,
      BiConsumer<DexMethod, T> methodConsumer,
      T arg) {
    classConsumer.accept(this, arg);
  }

  @Override
  public String toSourceString() {
    if (toStringCache == null) {
      // TODO(ager): Pass in a ProguardMapReader to map names back to original names.
      if (DexItemFactory.isInternalSentinel(this)) {
        toStringCache = descriptor.toString();
      } else {
        toStringCache = DescriptorUtils.descriptorToJavaType(toDescriptorString());
      }
    }
    return toStringCache;
  }

  public char toShorty() {
    char c = (char) descriptor.content[0];
    return c == '[' ? 'L' : c;
  }

  @Override
  public String toSmaliString() {
    return toDescriptorString();
  }

  @Override
  public String toString() {
    return toSourceString();
  }

  @Override
  public void collectIndexedItems(IndexedItemCollection collection) {
    if (collection.addType(this)) {
      collection.getRenamedDescriptor(this).collectIndexedItems(collection);
    }
  }

  @Override
  public void flushCachedValues() {
    super.flushCachedValues();
    toStringCache = null;
  }

  @Override
  public int getOffset(ObjectToOffsetMapping mapping) {
    return mapping.getOffsetFor(this);
  }

  @Override
  public boolean isDexType() {
    return true;
  }

  @Override
  public DexType asDexType() {
    return this;
  }

  @Override
  public int slowCompareTo(DexType other) {
    return descriptor.slowCompareTo(other.descriptor);
  }

  @Override
  public int slowCompareTo(DexType other, NamingLens namingLens) {
    DexString thisDescriptor = namingLens.lookupDescriptor(this);
    DexString otherDescriptor = namingLens.lookupDescriptor(other);
    return thisDescriptor.slowCompareTo(otherDescriptor, namingLens);
  }

  public boolean isPrimitiveType() {
    return DescriptorUtils.isPrimitiveType((char) descriptor.content[0]);
  }

  public boolean isVoidType() {
    return (char) descriptor.content[0] == 'V';
  }

  public boolean isBooleanType() {
    return descriptor.content[0] == 'Z';
  }

  public boolean isByteType() {
    return descriptor.content[0] == 'B';
  }

  public boolean isCharType() {
    return descriptor.content[0] == 'C';
  }

  public boolean isShortType() {
    return descriptor.content[0] == 'S';
  }

  public boolean isIntType() {
    return descriptor.content[0] == 'I';
  }

  public boolean isFloatType() {
    return descriptor.content[0] == 'F';
  }

  public boolean isLongType() {
    return descriptor.content[0] == 'J';
  }

  public boolean isDoubleType() {
    return descriptor.content[0] == 'D';
  }

  public boolean isNullValueType() {
    boolean isNullValueType = descriptor.content[0] == 'N';
    assert !isNullValueType || this == DexItemFactory.nullValueType;
    return isNullValueType;
  }

  public boolean isArrayType() {
    char firstChar = (char) descriptor.content[0];
    return firstChar == '[';
  }

  public boolean isClassType() {
    char firstChar = (char) descriptor.content[0];
    return firstChar == 'L';
  }

  public boolean isReferenceType() {
    boolean isReferenceType = isArrayType() || isClassType();
    assert isReferenceType != isPrimitiveType() || isVoidType();
    return isReferenceType;
  }

  public boolean isPrimitiveArrayType() {
    if (!isArrayType()) {
      return false;
    }
    return DescriptorUtils.isPrimitiveType((char) descriptor.content[1]);
  }

  public boolean isWideType() {
    return isDoubleType() || isLongType();
  }

  // TODO(b/158159959): Remove usage of name-based identification.
  public boolean isD8R8SynthesizedLambdaClassType() {
    String name = toSourceString();
    return name.contains(LAMBDA_CLASS_NAME_PREFIX);
  }

  // TODO(b/158159959): Remove usage of name-based identification.
  public boolean isD8R8SynthesizedClassType() {
    String name = toSourceString();
    // The synthesized classes listed here must always be unique to a program context and thus
    // never duplicated for distinct inputs.
    return
    // Hygienic suffix.
    name.contains(COMPANION_CLASS_NAME_SUFFIX)
        // New and hygienic synthesis infrastructure.
        || name.contains(SyntheticItems.INTERNAL_SYNTHETIC_CLASS_SEPARATOR)
        || name.contains(SyntheticItems.EXTERNAL_SYNTHETIC_CLASS_SEPARATOR)
        // Only generated in core lib.
        || name.contains(EMULATE_LIBRARY_CLASS_NAME_SUFFIX)
        || name.contains(TYPE_WRAPPER_SUFFIX)
        || name.contains(VIVIFIED_TYPE_WRAPPER_SUFFIX)
        || name.contains(DesugaredLibraryRetargeter.DESUGAR_LIB_RETARGET_CLASS_NAME_PREFIX)
        // Non-hygienic types.
        || isSynthesizedTypeThatCouldBeDuplicated(name);
  }

  public boolean isLegacySynthesizedTypeAllowedDuplication() {
    String name = toSourceString();
    return isSynthesizedTypeThatCouldBeDuplicated(name) || oldSynthesizedName(name);
  }

  private static boolean isSynthesizedTypeThatCouldBeDuplicated(String name) {
    // Any entry that is removed from here must be added to OLD_SYNTHESIZED_NAMES to ensure that
    // newer releases can be used to merge previous builds.
    return name.contains(ENUM_UNBOXING_UTILITY_CLASS_SUFFIX) // Shared among enums.
        || name.contains(SyntheticArgumentClass.SYNTHETIC_CLASS_SUFFIX)
        || name.contains(LAMBDA_CLASS_NAME_PREFIX) // Could collide.
        || name.contains(LAMBDA_GROUP_CLASS_NAME_PREFIX) // Could collide.
        || name.contains(DISPATCH_CLASS_NAME_SUFFIX) // Shared on reference.
        || name.contains(OutlineOptions.CLASS_NAME) // Global singleton.
        || name.contains(TwrCloseResourceRewriter.UTILITY_CLASS_NAME) // Global singleton.
        || name.contains(NestBasedAccessDesugaring.NEST_CONSTRUCTOR_NAME) // Global singleton.
        || name.contains(ServiceLoaderRewriter.SERVICE_LOADER_CLASS_NAME); // Global singleton.
  }

  private boolean oldSynthesizedName(String name) {
    for (String synthesizedPrefix : OLD_SYNTHESIZED_NAMES) {
      if (name.contains(synthesizedPrefix)) {
        return true;
      }
    }
    return false;
  }

  public boolean isProgramType(DexDefinitionSupplier definitions) {
    DexClass clazz = definitions.definitionFor(this);
    return clazz != null && clazz.isProgramClass();
  }

  public boolean isResolvable(AppView<?> appView) {
    DexClass clazz = appView.definitionFor(this);
    return clazz != null && clazz.isResolvable(appView);
  }

  public int elementSizeForPrimitiveArrayType() {
    assert isPrimitiveArrayType();
    switch (descriptor.content[1]) {
      case 'Z': // boolean
      case 'B': // byte
        return 1;
      case 'S': // short
      case 'C': // char
        return 2;
      case 'I': // int
      case 'F': // float
        return 4;
      case 'J': // long
      case 'D': // double
        return 8;
      default:
        throw new Unreachable("Not array of primitives '" + descriptor + "'");
    }
  }

  public int getNumberOfLeadingSquareBrackets() {
    int leadingSquareBrackets = 0;
    while (descriptor.content[leadingSquareBrackets] == '[') {
      leadingSquareBrackets++;
    }
    return leadingSquareBrackets;
  }

  public DexType toBaseType(DexItemFactory dexItemFactory) {
    int leadingSquareBrackets = getNumberOfLeadingSquareBrackets();
    if (leadingSquareBrackets == 0) {
      return this;
    }
    DexString newDesc =
        dexItemFactory.createString(
            descriptor.size - leadingSquareBrackets,
            Arrays.copyOfRange(
                descriptor.content, leadingSquareBrackets, descriptor.content.length));
    return dexItemFactory.createType(newDesc);
  }

  // Similar to the method above, but performs a lookup only, allowing to use
  // this method also after strings are sorted in the ApplicationWriter.
  public DexType lookupBaseType(DexItemFactory dexItemFactory) {
    int leadingSquareBrackets = getNumberOfLeadingSquareBrackets();
    if (leadingSquareBrackets == 0) {
      return this;
    }
    DexString newDesc =
        dexItemFactory.lookupString(
            descriptor.size - leadingSquareBrackets,
            Arrays.copyOfRange(
                descriptor.content, leadingSquareBrackets, descriptor.content.length));
    return dexItemFactory.lookupType(newDesc);
  }

  public DexType replaceBaseType(DexType newBase, DexItemFactory dexItemFactory) {
    assert this.isArrayType();
    assert !newBase.isArrayType();
    return newBase.toArrayType(getNumberOfLeadingSquareBrackets(), dexItemFactory);
  }

  public DexType replacePackage(String newPackageDescriptor, DexItemFactory dexItemFactory) {
    assert isClassType();
    String descriptorString = toDescriptorString();
    String newDescriptorString = "L";
    if (!newPackageDescriptor.isEmpty()) {
      newDescriptorString += newPackageDescriptor + "/";
    }
    newDescriptorString += DescriptorUtils.getSimpleClassNameFromDescriptor(descriptorString) + ";";
    return dexItemFactory.createType(newDescriptorString);
  }

  public DexType addSuffix(String suffix, DexItemFactory dexItemFactory) {
    assert isClassType();
    String descriptorString = toDescriptorString();
    int endIndex = descriptorString.length() - 1;
    String newDescriptorString = descriptorString.substring(0, endIndex) + suffix + ";";
    return dexItemFactory.createType(newDescriptorString);
  }

  public DexType toArrayType(int dimensions, DexItemFactory dexItemFactory) {
    return dexItemFactory.createType(descriptor.toArrayDescriptor(dimensions, dexItemFactory));
  }

  public DexType toArrayElementType(DexItemFactory dexItemFactory) {
    assert isArrayType();
    DexString newDesc =
        dexItemFactory.createString(
            descriptor.size - 1,
            Arrays.copyOfRange(descriptor.content, 1, descriptor.content.length));
    return dexItemFactory.createType(newDesc);
  }

  private String getPackageOrName(boolean packagePart) {
    assert isClassType();
    String descriptor = toDescriptorString();
    int lastSeparator = descriptor.lastIndexOf('/');
    if (lastSeparator == -1) {
      return packagePart ? "" : descriptor.substring(1, descriptor.length() - 1);
    } else {
      return packagePart
          ? descriptor.substring(1, lastSeparator)
          : descriptor.substring(lastSeparator + 1, descriptor.length() - 1);
    }
  }

  public String getPackageDescriptor() {
    return getPackageOrName(true);
  }

  public String getName() {
    if (isPrimitiveType()) {
      return toSourceString();
    }
    return getPackageOrName(false);
  }

  public String getSimpleName() {
    assert isClassType();
    return DescriptorUtils.getSimpleClassNameFromDescriptor(toDescriptorString());
  }

  public DexType withSimpleName(String newSimpleName, DexItemFactory dexItemFactory) {
    assert isClassType();
    return dexItemFactory.createType(
        DescriptorUtils.replaceSimpleClassNameInDescriptor(toDescriptorString(), newSimpleName));
  }

  /** Get the fully qualified name using '/' in place of '.', aka the "internal type name" in ASM */
  public String getInternalName() {
    assert isClassType() || isArrayType();
    return DescriptorUtils.descriptorToInternalName(toDescriptorString());
  }

  public String getPackageName() {
    return DescriptorUtils.getPackageNameFromBinaryName(toBinaryName());
  }
}
