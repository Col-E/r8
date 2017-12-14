// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.ToolHelper.DexVm;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;

/**
 * A JUnit test runner that allows to filter out tests based on VM version.
 */
public class VmTestRunner extends BlockJUnit4ClassRunner {

  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.METHOD, ElementType.TYPE})
  public @interface IgnoreIfVmOlderOrEqualThan {

    DexVm.Version version();
  }

  public VmTestRunner(Class<?> klass) throws InitializationError {
    super(klass);
  }

  @Override
  protected boolean isIgnored(FrameworkMethod child) {
    if (super.isIgnored(child)) {
      return true;
    }
    IgnoreIfVmOlderOrEqualThan annotation = child.getAnnotation(IgnoreIfVmOlderOrEqualThan.class);
    if (annotation != null
        && ToolHelper.getDexVm().getVersion().isOlderThanOrEqual(annotation.version())) {
      return true;
    }
    return false;
  }
}
