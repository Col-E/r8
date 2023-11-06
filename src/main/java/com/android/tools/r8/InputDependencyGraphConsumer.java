// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import java.nio.file.Path;

/** Consumer for receiving file dependencies from inputs. */
@KeepForApi
public interface InputDependencyGraphConsumer {

  /**
   * Callback indicating that file {@code dependency} is referenced from the compiler inputs.
   *
   * <p>Note: this callback may be called on multiple threads.
   *
   * <p>Note: this callback places no guarantees on order of calls or on duplicate calls.
   *
   * @param dependent Origin of input that is has the file {@code dependency}.
   * @param dependency Path of the dependency.
   */
  void accept(Origin dependent, Path dependency);

  /** Callback for proguard `-include` directive. Defaults to call general accept. */
  default void acceptProguardInclude(Origin dependent, Path dependency) {
    accept(dependent, dependency);
  }

  /** Callback for proguard `-injars` directive. Defaults to call general accept. */
  default void acceptProguardInJars(Origin dependent, Path dependency) {
    accept(dependent, dependency);
  }

  /** Callback for proguard `-libraryjars` directive. Defaults to call general accept. */
  default void acceptProguardLibraryJars(Origin dependent, Path dependency) {
    accept(dependent, dependency);
  }

  /** Callback for proguard `-applymapping` directive. Defaults to call general accept. */
  default void acceptProguardApplyMapping(Origin dependent, Path dependency) {
    accept(dependent, dependency);
  }

  /** Callback for proguard `-obfuscationdictionary` directive. Defaults to call general accept. */
  default void acceptProguardObfuscationDictionary(Origin dependent, Path dependency) {
    accept(dependent, dependency);
  }

  /**
   * Callback for proguard `-classobfuscationdictionary` directive. Defaults to call general accept.
   */
  default void acceptProguardClassObfuscationDictionary(Origin dependent, Path dependency) {
    accept(dependent, dependency);
  }

  /**
   * Callback for proguard `-packageobfuscationdictionary` directive. Defaults to call general
   * accept.
   */
  default void acceptProguardPackageObfuscationDictionary(Origin dependent, Path dependency) {
    accept(dependent, dependency);
  }

  /**
   * Callback indicating no more dependencies remain for the active compilation unit.
   *
   * <p>Note: this callback places no other guarantees on number of calls or on which threads.
   */
  void finished();
}
