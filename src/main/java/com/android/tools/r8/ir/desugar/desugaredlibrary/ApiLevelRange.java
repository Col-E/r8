// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary;

import com.android.tools.r8.utils.AndroidApiLevel;
import java.util.Objects;

public class ApiLevelRange {

  private final AndroidApiLevel apiLevelBelowOrEqual;
  private final AndroidApiLevel apiLevelGreaterOrEqual;

  public ApiLevelRange(int apiLevelBelowOrEqual) {
    this(AndroidApiLevel.getAndroidApiLevel(apiLevelBelowOrEqual), null);
  }

  public ApiLevelRange(int apiLevelBelowOrEqual, int apiLevelGreaterOrEqual) {
    this(
        AndroidApiLevel.getAndroidApiLevel(apiLevelBelowOrEqual),
        AndroidApiLevel.getAndroidApiLevel(apiLevelGreaterOrEqual));
  }

  public ApiLevelRange(
      AndroidApiLevel apiLevelBelowOrEqual, AndroidApiLevel apiLevelGreaterOrEqual) {
    this.apiLevelBelowOrEqual = apiLevelBelowOrEqual;
    this.apiLevelGreaterOrEqual = apiLevelGreaterOrEqual;
  }

  public int getApiLevelBelowOrEqualAsInt() {
    return apiLevelBelowOrEqual.getLevel();
  }

  public int getApiLevelGreaterOrEqualAsInt() {
    return apiLevelGreaterOrEqual.getLevel();
  }

  public boolean hasApiLevelGreaterOrEqual() {
    return apiLevelGreaterOrEqual != null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ApiLevelRange)) {
      return false;
    }
    ApiLevelRange that = (ApiLevelRange) o;
    return apiLevelBelowOrEqual.equals(that.apiLevelBelowOrEqual)
        && Objects.equals(apiLevelGreaterOrEqual, that.apiLevelGreaterOrEqual);
  }

  @Override
  public int hashCode() {
    return Objects.hash(apiLevelBelowOrEqual, apiLevelGreaterOrEqual);
  }

  public int deterministicOrder(ApiLevelRange other) {
    int compare = apiLevelBelowOrEqual.compareTo(other.apiLevelBelowOrEqual);
    if (compare != 0) {
      return compare;
    }
    if (apiLevelGreaterOrEqual == null) {
      if (other.apiLevelGreaterOrEqual == null) {
        return 0;
      }
      return 1;
    }
    if (other.apiLevelGreaterOrEqual == null) {
      return -1;
    }
    return apiLevelGreaterOrEqual.compareTo(other.apiLevelGreaterOrEqual);
  }
}
