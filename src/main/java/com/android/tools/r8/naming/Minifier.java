// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.naming.ClassNameMinifier.ClassNamingStrategy;
import com.android.tools.r8.naming.ClassNameMinifier.ClassRenaming;
import com.android.tools.r8.naming.ClassNameMinifier.Namespace;
import com.android.tools.r8.naming.ClassNameMinifier.PackageNamingStrategy;
import com.android.tools.r8.naming.FieldNameMinifier.FieldRenaming;
import com.android.tools.r8.naming.MethodNameMinifier.MethodRenaming;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.Timing;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
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
            new MinificationPackageNamingStrategy(),
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

  static class MinificationClassNamingStrategy implements ClassNamingStrategy {

    private final AppView<?> appView;
    private final Object2IntMap<Namespace> namespaceCounters = new Object2IntLinkedOpenHashMap<>();

    MinificationClassNamingStrategy(AppView<?> appView) {
      this.appView = appView;
      namespaceCounters.defaultReturnValue(1);
    }

    @Override
    public DexString next(Namespace namespace, DexType type, char[] packagePrefix) {
      int counter = namespaceCounters.put(namespace, namespaceCounters.getInt(namespace) + 1);
      DexString string =
          appView
              .dexItemFactory()
              .createString(StringUtils.numberToIdentifier(counter, packagePrefix, true));
      return string;
    }

    @Override
    public boolean bypassDictionary() {
      return false;
    }

    @Override
    public boolean noObfuscation(DexType type) {
      return appView.rootSet().mayNotBeMinified(type, appView);
    }
  }

  static class MinificationPackageNamingStrategy implements PackageNamingStrategy {

    private final Object2IntMap<Namespace> namespaceCounters = new Object2IntLinkedOpenHashMap<>();

    public MinificationPackageNamingStrategy() {
      namespaceCounters.defaultReturnValue(1);
    }

    @Override
    public String next(Namespace namespace, char[] packagePrefix) {
      // Note that the differences between this method and the other variant for class renaming are
      // 1) this one uses the different dictionary and counter,
      // 2) this one does not append ';' at the end, and
      // 3) this one removes 'L' at the beginning to make the return value a binary form.
      int counter = namespaceCounters.put(namespace, namespaceCounters.getInt(namespace) + 1);
      return StringUtils.numberToIdentifier(counter, packagePrefix, false).substring(1);
    }

    @Override
    public boolean bypassDictionary() {
      return false;
    }
  }

  static class MinifierMemberNamingStrategy implements MemberNamingStrategy {

    private final DexItemFactory factory;
    // We have to ensure that the names proposed by the minifier is not used in the obfuscation
    // dictionary. We use a list for direct indexing based on a number and a set for looking up.
    private final List<String> obfuscationDictionary;
    private final Set<String> obfuscationDictionaryForLookup;

    private final AppView<?> appView;

    public MinifierMemberNamingStrategy(AppView<?> appView) {
      this.appView = appView;
      this.factory = appView.dexItemFactory();
      this.obfuscationDictionary =
          appView.options().getProguardConfiguration().getObfuscationDictionary();
      this.obfuscationDictionaryForLookup = new HashSet<>(this.obfuscationDictionary);
      assert this.obfuscationDictionary != null;
    }

    @Override
    public DexString next(DexMethod method, MemberNamingInternalState internalState) {
      DexEncodedMethod encodedMethod = appView.definitionFor(method);
      boolean isDirectOrStatic = encodedMethod.isDirectMethod() || encodedMethod.isStatic();
      return getNextName(internalState, isDirectOrStatic);
    }

    @Override
    public DexString next(DexField field, MemberNamingInternalState internalState) {
      return getNextName(internalState, false);
    }

    private DexString getNextName(
        MemberNamingInternalState internalState, boolean isDirectOrStatic) {
      if (internalState.getDictionaryIndex() < obfuscationDictionary.size()) {
        return factory.createString(
            obfuscationDictionary.get(internalState.incrementDictionaryIndex()));
      } else {
        String nextString;
        do {
          int counter = internalState.incrementNameIndex(isDirectOrStatic);
          nextString = StringUtils.numberToIdentifier(counter);
        } while (obfuscationDictionaryForLookup.contains(nextString));
        return factory.createString(nextString);
      }
    }

    @Override
    public boolean breakOnNotAvailable(DexReference source, DexString name) {
      return false;
    }

    @Override
    public boolean noObfuscation(DexReference reference) {
      return appView.rootSet().mayNotBeMinified(reference, appView);
    }
  }
}
