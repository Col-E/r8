// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.nest;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClasspathClass;
import com.android.tools.r8.graph.DexType;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class Nest {

  private final DexClass hostClass;
  private final List<DexClass> members;
  private final List<DexType> missingMembers;

  private Nest(DexClass hostClass, List<DexClass> members, List<DexType> missingMembers) {
    this.hostClass = hostClass;
    this.members = members;
    this.missingMembers = missingMembers;
  }

  public static Nest create(AppView<?> appView, DexClass clazz) {
    return create(appView, clazz, null);
  }

  public static Nest create(
      AppView<?> appView, DexClass clazz, Consumer<DexClass> missingHostConsumer) {
    assert clazz.isInANest();

    DexClass hostClass = clazz.isNestHost() ? clazz : appView.definitionFor(clazz.getNestHost());
    if (hostClass == null) {
      // Missing nest host means the class is considered as not being part of a nest.
      if (missingHostConsumer != null) {
        missingHostConsumer.accept(clazz);
      }
      return null;
    }

    List<DexClass> members = new ArrayList<>(hostClass.getNestMembersClassAttributes().size());
    List<DexType> missingMembers = new ArrayList<>();
    hostClass.forEachNestMember(
        memberType -> {
          DexClass memberClass = appView.definitionFor(memberType);
          if (memberClass != null) {
            members.add(memberClass);
          } else {
            missingMembers.add(memberType);
          }
        });
    return new Nest(hostClass, members, missingMembers);
  }

  public Iterable<DexClasspathClass> getClasspathMembers() {
    return Iterables.transform(
        Iterables.filter(members, DexClass::isClasspathClass), DexClass::asClasspathClass);
  }

  public DexClass getHostClass() {
    return hostClass;
  }

  public List<DexClass> getMembers() {
    return members;
  }

  public List<DexType> getMissingMembers() {
    return missingMembers;
  }

  public boolean hasLibraryMember() {
    return Iterables.any(members, DexClass::isLibraryClass);
  }

  public boolean hasMissingMembers() {
    return !missingMembers.isEmpty();
  }
}
