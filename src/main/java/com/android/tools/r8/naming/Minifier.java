// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.StringUtils.EMPTY_CHAR_ARRAY;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.naming.ClassNameMinifier.ClassNamingStrategy;
import com.android.tools.r8.naming.ClassNameMinifier.ClassRenaming;
import com.android.tools.r8.naming.ClassNameMinifier.PackageNamingStrategy;
import com.android.tools.r8.naming.FieldNameMinifier.FieldRenaming;
import com.android.tools.r8.naming.MethodNameMinifier.MethodRenaming;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.SymbolGenerationUtils;
import com.android.tools.r8.utils.SymbolGenerationUtils.MixedCasing;
import com.android.tools.r8.utils.Timing;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class Minifier {

  private final AppView<AppInfoWithLiveness> appView;
  private final Set<DexCallSite> desugaredCallSites;

  public Minifier(AppView<AppInfoWithLiveness> appView, Set<DexCallSite> desugaredCallSites) {
    this.appView = appView;
    this.desugaredCallSites = desugaredCallSites;
  }

  public NamingLens run(Timing timing) {
    assert appView.options().isMinifying();
    timing.begin("ComputeInterfaces");
    Set<DexClass> interfaces = new TreeSet<>((a, b) -> a.type.slowCompareTo(b.type));
    interfaces.addAll(appView.appInfo().computeReachableInterfaces(desugaredCallSites));
    timing.end();
    timing.begin("MinifyClasses");
    ClassNameMinifier classNameMinifier =
        new ClassNameMinifier(
            appView,
            new MinificationClassNamingStrategy(appView),
            new MinificationPackageNamingStrategy(appView),
            // Use deterministic class order to make sure renaming is deterministic.
            appView.appInfo().classesWithDeterministicOrder());
    ClassRenaming classRenaming = classNameMinifier.computeRenaming(timing);
    timing.end();

    assert new MinifiedRenaming(
            appView, classRenaming, MethodRenaming.empty(), FieldRenaming.empty())
        .verifyNoCollisions(appView.appInfo().classes(), appView.dexItemFactory());

    MemberNamingStrategy minifyMembers = new MinifierMemberNamingStrategy(appView);
    timing.begin("MinifyMethods");
    MethodRenaming methodRenaming =
        new MethodNameMinifier(appView, minifyMembers)
            .computeRenaming(interfaces, desugaredCallSites, timing);
    timing.end();

    assert new MinifiedRenaming(appView, classRenaming, methodRenaming, FieldRenaming.empty())
        .verifyNoCollisions(appView.appInfo().classes(), appView.dexItemFactory());

    timing.begin("MinifyFields");
    FieldRenaming fieldRenaming =
        new FieldNameMinifier(appView, minifyMembers).computeRenaming(interfaces, timing);
    timing.end();

    NamingLens lens = new MinifiedRenaming(appView, classRenaming, methodRenaming, fieldRenaming);
    assert lens.verifyNoCollisions(appView.appInfo().classes(), appView.dexItemFactory());

    timing.begin("MinifyIdentifiers");
    new IdentifierMinifier(appView, lens).run();
    timing.end();
    return lens;
  }

  abstract static class BaseMinificationNamingStrategy {

    // We have to ensure that the names proposed by the minifier is not used in the obfuscation
    // dictionary. We use a list for direct indexing based on a number and a set for looking up.
    private final List<String> obfuscationDictionary;
    private final Set<String> obfuscationDictionaryForLookup;
    private final MixedCasing mixedCasing;

    BaseMinificationNamingStrategy(List<String> obfuscationDictionary, boolean dontUseMixedCasing) {
      this.obfuscationDictionary = obfuscationDictionary;
      this.obfuscationDictionaryForLookup = new HashSet<>(this.obfuscationDictionary);
      this.mixedCasing =
          dontUseMixedCasing ? MixedCasing.DONT_USE_MIXED_CASE : MixedCasing.USE_MIXED_CASE;
      assert obfuscationDictionary != null;
    }

    String nextName(char[] packagePrefix, InternalNamingState state, boolean isDirectMethodCall) {
      StringBuilder nextName = new StringBuilder();
      nextName.append(packagePrefix);
      if (state.getDictionaryIndex() < obfuscationDictionary.size()) {
        nextName.append(obfuscationDictionary.get(state.incrementDictionaryIndex()));
      } else {
        String nextString;
        do {
          nextString =
              SymbolGenerationUtils.numberToIdentifier(
                  state.incrementNameIndex(isDirectMethodCall), mixedCasing);
        } while (obfuscationDictionaryForLookup.contains(nextString));
        nextName.append(nextString);
      }
      return nextName.toString();
    }
  }

  static class MinificationClassNamingStrategy extends BaseMinificationNamingStrategy
      implements ClassNamingStrategy {

    final AppView<?> appView;
    private final DexItemFactory factory;

    MinificationClassNamingStrategy(AppView<?> appView) {
      super(
          appView.options().getProguardConfiguration().getClassObfuscationDictionary(),
          appView.options().getProguardConfiguration().hasDontUseMixedCaseClassnames());
      this.appView = appView;
      factory = appView.dexItemFactory();
    }

    @Override
    public DexString next(DexType type, char[] packagePrefix, InternalNamingState state) {
      return factory.createString(nextName(packagePrefix, state, false) + ";");
    }

    @Override
    public boolean noObfuscation(DexType type) {
      return appView.rootSet().mayNotBeMinified(type, appView);
    }
  }

  static class MinificationPackageNamingStrategy extends BaseMinificationNamingStrategy
      implements PackageNamingStrategy {

    MinificationPackageNamingStrategy(AppView<?> appView) {
      super(
          appView.options().getProguardConfiguration().getPackageObfuscationDictionary(),
          appView.options().getProguardConfiguration().hasDontUseMixedCaseClassnames());
    }

    @Override
    public String next(char[] packagePrefix, InternalNamingState state) {
      // Note that the differences between this method and the other variant for class renaming are
      // 1) this one uses the different dictionary and counter,
      // 2) this one does not append ';' at the end, and
      // 3) this one removes 'L' at the beginning to make the return value a binary form.
      return nextName(packagePrefix, state, false).substring(1);
    }
  }

  static class MinifierMemberNamingStrategy extends BaseMinificationNamingStrategy
      implements MemberNamingStrategy {

    final AppView<?> appView;
    private final DexItemFactory factory;

    public MinifierMemberNamingStrategy(AppView<?> appView) {
      super(appView.options().getProguardConfiguration().getObfuscationDictionary(), false);
      this.appView = appView;
      this.factory = appView.dexItemFactory();
    }

    @Override
    public DexString next(DexMethod method, InternalNamingState internalState) {
      assert checkAllowMemberRenaming(method.holder);
      DexEncodedMethod encodedMethod = appView.definitionFor(method);
      boolean isDirectOrStatic = encodedMethod.isDirectMethod() || encodedMethod.isStatic();
      return getNextName(internalState, isDirectOrStatic);
    }

    @Override
    public DexString next(DexField field, InternalNamingState internalState) {
      assert checkAllowMemberRenaming(field.holder);
      return getNextName(internalState, false);
    }

    private DexString getNextName(InternalNamingState internalState, boolean isDirectOrStatic) {
      return factory.createString(nextName(EMPTY_CHAR_ARRAY, internalState, isDirectOrStatic));
    }

    @Override
    public DexString getReservedNameOrDefault(
        DexEncodedMethod method, DexClass holder, DexString defaultValue) {
      if (!allowMemberRenaming(holder)
          || holder.accessFlags.isAnnotation()
          || method.accessFlags.isConstructor()
          || appView.rootSet().mayNotBeMinified(method.method, appView)) {
        return method.method.name;
      }
      return defaultValue;
    }

    @Override
    public DexString getReservedNameOrDefault(
        DexEncodedField field, DexClass holder, DexString defaultValue) {
      if (holder.isLibraryClass() || appView.rootSet().mayNotBeMinified(field.field, appView)) {
        return field.field.name;
      }
      return defaultValue;
    }

    @Override
    public boolean allowMemberRenaming(DexClass holder) {
      return holder.isProgramClass();
    }

    public boolean checkAllowMemberRenaming(DexType holder) {
      DexClass clazz = appView.definitionFor(holder);
      assert clazz != null && allowMemberRenaming(clazz);
      return true;
    }

    @Override
    public void reportReservationError(DexReference source, DexString name) {
      assert false;
      // This should only happen when applymapping is used and will be caught in that strategy.
    }
  }
}
