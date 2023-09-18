// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.stacktraces;

import com.google.common.collect.ImmutableList;
import java.util.List;

/** This is a reproduction of b/300416467 */
public class ExceptionMessageWithClassNameInMessage implements StackTraceForTest {

  @Override
  public List<String> obfuscatedStackTrace() {
    return ImmutableList.of(
        "10-26 19:26:24.749 10159 26250 26363 E Tycho.crl: Exception",
        "10-26 19:26:24.749 10159 26250 26363 E Tycho.crl: java.util.concurrent.ExecutionException:"
            + " ary: eu: Exception in CronetUrlRequest: net::ERR_CONNECTION_CLOSED, ErrorCode=5,"
            + " InternalErrorCode=-100, Retryable=true");
  }

  @Override
  public String mapping() {
    return "foo.bar.baz -> net:";
  }

  @Override
  public List<String> retracedStackTrace() {
    return ImmutableList.of(
        "10-26 19:26:24.749 10159 26250 26363 E Tycho.crl: Exception",
        "10-26 19:26:24.749 10159 26250 26363 E Tycho.crl: java.util.concurrent.ExecutionException:"
            + " ary: eu: Exception in CronetUrlRequest: net::ERR_CONNECTION_CLOSED,"
            + " ErrorCode=5, InternalErrorCode=-100, Retryable=true");
  }

  @Override
  public List<String> retraceVerboseStackTrace() {
    return ImmutableList.of(
        "10-26 19:26:24.749 10159 26250 26363 E Tycho.crl: Exception",
        "10-26 19:26:24.749 10159 26250 26363 E Tycho.crl: java.util.concurrent.ExecutionException:"
            + " ary: eu: Exception in CronetUrlRequest: net::ERR_CONNECTION_CLOSED,"
            + " ErrorCode=5, InternalErrorCode=-100, Retryable=true");
  }

  @Override
  public int expectedWarnings() {
    return 0;
  }
}
