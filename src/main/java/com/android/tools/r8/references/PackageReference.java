// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.references;

import com.android.tools.r8.KeepForRetraceApi;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.utils.DescriptorUtils;
import java.util.Objects;

/** Reference to a package. */
@KeepForApi
@KeepForRetraceApi
public class PackageReference {

  private final String packageName;

  PackageReference(String packageName) {
    if (packageName == null) {
      throw new IllegalArgumentException("Package name cannot be null.");
    }
    if (!packageName.isEmpty() && !DescriptorUtils.isValidJavaType(packageName)) {
      throw new IllegalArgumentException("Package name '" + packageName + "' is not valid.");
    }
    this.packageName = packageName;
  }

  public String getPackageName() {
    return packageName;
  }

  public String getPackageBinaryName() {
    return packageName.replace(
        DescriptorUtils.JAVA_PACKAGE_SEPARATOR, DescriptorUtils.DESCRIPTOR_PACKAGE_SEPARATOR);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof PackageReference)) {
      return false;
    }
    PackageReference that = (PackageReference) o;
    return packageName.equals(that.packageName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(packageName);
  }
}
