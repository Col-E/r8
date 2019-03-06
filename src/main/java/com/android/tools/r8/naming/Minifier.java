// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.naming.ClassNameMinifier.ClassNamingStrategy;
import com.android.tools.r8.naming.ClassNameMinifier.ClassRenaming;
import com.android.tools.r8.naming.ClassNameMinifier.Namespace;
import com.android.tools.r8.naming.ClassNameMinifier.PackageNamingStrategy;
import com.android.tools.r8.naming.FieldNameMinifier.FieldRenaming;
import com.android.tools.r8.naming.MemberNameMinifier.MemberNamingStrategy;
import com.android.tools.r8.naming.MethodNameMinifier.MethodRenaming;
import com.android.tools.r8.naming.NamingState.InternalState;
import com.android.tools.r8.shaking.Enqueuer.AppInfoWithLiveness;
import com.android.tools.r8.shaking.RootSetBuilder.RootSet;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.Timing;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.Set;

public class Minifier {

  private final AppView<AppInfoWithLiveness> appView;
  private final AppInfoWithLiveness appInfo;
  private final RootSet rootSet;
  private final Set<DexCallSite> desugaredCallSites;
  private final InternalOptions options;

  public Minifier(
      AppView<AppInfoWithLiveness> appView,
      RootSet rootSet,
      Set<DexCallSite> desugaredCallSites) {
    this.appView = appView;
    this.appInfo = appView.appInfo();
    this.rootSet = rootSet;
    this.desugaredCallSites = desugaredCallSites;
    this.options = appView.options();
  }

  public NamingLens run(Timing timing) {
    assert options.enableMinification;
    timing.begin("MinifyClasses");
    ClassNameMinifier classNameMinifier =
        new ClassNameMinifier(
            appView,
            rootSet,
            new MinificationClassNamingStrategy(appInfo.dexItemFactory),
            new MinificationPackageNamingStrategy(),
            // Use deterministic class order to make sure renaming is deterministic.
            appInfo.classesWithDeterministicOrder());
    ClassRenaming classRenaming = classNameMinifier.computeRenaming(timing);
    timing.end();

    assert new MinifiedRenaming(
            classRenaming, MethodRenaming.empty(), FieldRenaming.empty(), appInfo)
        .verifyNoCollisions(appInfo.classes(), appInfo.dexItemFactory);

    MemberNamingStrategy minifyMembers = new MinifierMemberNamingStrategy(appView.dexItemFactory());
    timing.begin("MinifyMethods");
    MethodRenaming methodRenaming =
        new MethodNameMinifier(appView, rootSet, minifyMembers)
            .computeRenaming(desugaredCallSites, timing);
    timing.end();

    assert new MinifiedRenaming(classRenaming, methodRenaming, FieldRenaming.empty(), appInfo)
        .verifyNoCollisions(appInfo.classes(), appInfo.dexItemFactory);

    timing.begin("MinifyFields");
    FieldRenaming fieldRenaming =
        new FieldNameMinifier(appView, rootSet, minifyMembers).computeRenaming(timing);
    timing.end();

    NamingLens lens = new MinifiedRenaming(classRenaming, methodRenaming, fieldRenaming, appInfo);
    assert lens.verifyNoCollisions(appInfo.classes(), appInfo.dexItemFactory);

    timing.begin("MinifyIdentifiers");
    new IdentifierMinifier(
        appInfo, options.getProguardConfiguration().getAdaptClassStrings(), lens).run();
    timing.end();
    return lens;
  }

  static class MinificationClassNamingStrategy implements ClassNamingStrategy {

    private final DexItemFactory factory;
    private final Object2IntMap<Namespace> namespaceCounters = new Object2IntLinkedOpenHashMap<>();

    MinificationClassNamingStrategy(DexItemFactory factory) {
      this.factory = factory;
      namespaceCounters.defaultReturnValue(1);
    }

    @Override
    public DexString next(Namespace namespace, DexType type, char[] packagePrefix) {
      int counter = namespaceCounters.put(namespace, namespaceCounters.getInt(namespace) + 1);
      DexString string =
          factory.createString(StringUtils.numberToIdentifier(packagePrefix, counter, true));
      return string;
    }

    @Override
    public boolean bypassDictionary() {
      return false;
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
      return StringUtils.numberToIdentifier(packagePrefix, counter, false).substring(1);
    }

    @Override
    public boolean bypassDictionary() {
      return false;
    }
  }

  static class MinifierMemberNamingStrategy implements MemberNamingStrategy {

    char[] EMPTY_CHAR_ARRAY = new char[0];

    private final DexItemFactory factory;

    public MinifierMemberNamingStrategy(DexItemFactory factory) {
      this.factory = factory;
    }

    @Override
    public DexString next(DexReference dexReference, InternalState internalState) {
      int counter = internalState.incrementAndGet();
      return factory.createString(StringUtils.numberToIdentifier(EMPTY_CHAR_ARRAY, counter, false));
    }

    @Override
    public boolean bypassDictionary() {
      return false;
    }
  }
}
