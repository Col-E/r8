// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import java.util.Collection;

public class AppInfoWithSubtyping extends AppInfoWithClassHierarchy {

  public AppInfoWithSubtyping(DirectMappedDexApplication application) {
    this(application, application.allClasses());
  }

  public AppInfoWithSubtyping(
      DirectMappedDexApplication application, Collection<DexClass> classes) {
    super(application);
  }

  protected AppInfoWithSubtyping(AppInfoWithSubtyping previous) {
    super(previous);
    assert app() instanceof DirectMappedDexApplication;
  }

  private DirectMappedDexApplication getDirectApplication() {
    // TODO(herhut): Remove need for cast.
    return (DirectMappedDexApplication) app();
  }

  public Iterable<DexLibraryClass> libraryClasses() {
    assert checkIfObsolete();
    return getDirectApplication().libraryClasses();
  }

  @Override
  public boolean hasSubtyping() {
    assert checkIfObsolete();
    return true;
  }

  @Override
  public AppInfoWithSubtyping withSubtyping() {
    assert checkIfObsolete();
    return this;
  }
}
