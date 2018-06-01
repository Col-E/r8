// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.ToolHelper.DexVm;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;

/**
 * A JUnit test runner that allows to filter out tests based on VM version.
 */
public class VmTestRunner extends BlockJUnit4ClassRunner {

  /**
   * Ignores the test for all VM versions up to {@link #value()}.
   */
  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.METHOD, ElementType.TYPE})
  public @interface IgnoreIfVmOlderThan {

    DexVm.Version value();
  }

  /**
   * Ignores the test for all VM versions up to and includion {@link #value()}.
   */
  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.METHOD, ElementType.TYPE})
  public @interface IgnoreIfVmOlderOrEqualThan {

    DexVm.Version value();
  }

  /**
   * Ignores the test for all specified versions of the VM.
   */
  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.METHOD, ElementType.TYPE})
  public @interface IgnoreForVmVersions {

    DexVm.Version[] value();
  }


  /**
   * Ignores a test for all versions of the Vm between {@link #from()} and {@link #to()} inclusive.
   */
  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.METHOD, ElementType.TYPE})
  public @interface IgnoreForRangeOfVmVersions {

    DexVm.Version from();

    DexVm.Version to();
  }

  public VmTestRunner(Class<?> klass) throws InitializationError {
    super(klass);
  }

  @Override
  protected boolean isIgnored(FrameworkMethod child) {
    // Do not run VM tests if running VMs is not even supported.
    if (!ToolHelper.artSupported() && !ToolHelper.dealsWithGoldenFiles()) {
      return true;
    }
    if (super.isIgnored(child)) {
      return true;
    }
    DexVm.Version currentVersion = ToolHelper.getDexVm().getVersion();
    IgnoreIfVmOlderThan ignoreIfVmOlderThan =
        child.getAnnotation(IgnoreIfVmOlderThan.class);
    if (ignoreIfVmOlderThan != null
        && !currentVersion.isAtLeast(ignoreIfVmOlderThan.value())) {
      return true;
    }
    IgnoreIfVmOlderOrEqualThan ignoreIfVmOlderOrEqualThan =
        child.getAnnotation(IgnoreIfVmOlderOrEqualThan.class);
    if (ignoreIfVmOlderOrEqualThan != null
        && currentVersion.isOlderThanOrEqual(ignoreIfVmOlderOrEqualThan.value())) {
      return true;
    }
    IgnoreForVmVersions ignoreForVmVersions = child.getAnnotation(IgnoreForVmVersions.class);
    if (ignoreForVmVersions != null
        && Arrays.stream(ignoreForVmVersions.value()).anyMatch(currentVersion::equals)) {
      return true;
    }
    IgnoreForRangeOfVmVersions ignoreForRangeOfVmVersions =
        child.getAnnotation(IgnoreForRangeOfVmVersions.class);
    if (ignoreForRangeOfVmVersions != null
        && currentVersion.compareTo(ignoreForRangeOfVmVersions.from()) >= 0
        && currentVersion.compareTo(ignoreForRangeOfVmVersions.to()) <= 0) {
      return true;
    }
    return false;
  }
}
