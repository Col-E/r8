// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize;

import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.MethodAccessFlags;

public final class ClassAndMemberPublicizer {
  private final DexItemFactory factory;
  private final DexApplication application;

  private ClassAndMemberPublicizer(DexApplication application, DexItemFactory factory) {
    this.application = application;
    this.factory = factory;
  }

  /**
   * Marks all package private and protected methods and fields as public. Also makes
   * all private static methods public.
   * <p>
   * This will destructively update the DexApplication passed in as argument.
   */
  public static DexApplication run(DexApplication application, DexItemFactory factory) {
    return new ClassAndMemberPublicizer(application, factory).run();
  }

  private DexApplication run() {
    for (DexClass clazz : application.classes()) {
      clazz.accessFlags.promoteToPublic();
      clazz.forEachMethod(this::publicizeMethod);
      clazz.forEachField(field -> field.accessFlags.promoteToPublic());
    }
    return application;
  }

  private void publicizeMethod(DexEncodedMethod method) {
    MethodAccessFlags accessFlags = method.accessFlags;
    if (accessFlags.isPublic()) {
      return;
    }

    if (factory.isClassConstructor(method.method)) {
      return;
    }

    if (!accessFlags.isPrivate()) {
      accessFlags.unsetProtected();
      accessFlags.setPublic();
      return;
    }
    assert accessFlags.isPrivate();

    if (factory.isConstructor(method.method)) {
      // TODO: b/72211928
      return;
    }

    if (!accessFlags.isStatic()) {
      // TODO: b/72109068
      return;
    }

    // For private static methods we can just relax the access to private, since
    // even though JLS prevents from declaring static method in derived class if
    // an instance method with same signature exists in superclass, JVM actually
    // does not take into account access of the static methods.
    accessFlags.unsetPrivate();
    accessFlags.setPublic();
  }
}
