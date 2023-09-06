// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.android.tools.r8.utils.codeinspector.FoundFieldSubject;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import org.hamcrest.Matcher;

public class RelocatorTestCompileResult extends TestRunResult<RelocatorTestCompileResult> {

  private final Path output;

  public RelocatorTestCompileResult(Path output) {
    this.output = output;
  }

  @Override
  RelocatorTestCompileResult self() {
    return this;
  }

  @Override
  public RelocatorTestCompileResult assertSuccess() {
    // If we produced a RelocatorTestRunResult the compilation was a success.
    return self();
  }

  @Override
  public RelocatorTestCompileResult assertStdoutMatches(Matcher<String> matcher) {
    throw new Unreachable("Not implemented");
  }

  @Override
  public RelocatorTestCompileResult assertFailure() {
    throw new Unreachable("Not implemented");
  }

  @Override
  public RelocatorTestCompileResult assertStderrMatches(Matcher<String> matcher) {
    throw new Unreachable("Not implemented");
  }

  @Override
  public <E extends Throwable> RelocatorTestCompileResult inspect(
      ThrowingConsumer<CodeInspector, E> consumer) throws IOException, ExecutionException, E {
    consumer.accept(new CodeInspector(output));
    return self();
  }

  @Override
  public <E extends Throwable> RelocatorTestCompileResult inspectFailure(
      ThrowingConsumer<CodeInspector, E> consumer) throws IOException, E {
    throw new Unreachable("NOT IMPLEMENTED");
  }

  @Override
  public RelocatorTestCompileResult disassemble() throws IOException, ExecutionException {
    throw new Unreachable("NOT IMPLEMENTED");
  }

  public RelocatorTestCompileResult inspectAllClassesRelocated(
      Path original, String originalPrefix, String newPrefix) throws Exception {
    CodeInspector originalInspector = new CodeInspector(original);
    inspect(
        relocatedInspector -> {
          for (FoundClassSubject clazz : originalInspector.allClasses()) {
            if (originalPrefix.isEmpty()
                || clazz
                    .getFinalName()
                    .startsWith(originalPrefix + DescriptorUtils.JAVA_PACKAGE_SEPARATOR)) {
              String relocatedName =
                  newPrefix + clazz.getFinalName().substring(originalPrefix.length());
              ClassSubject relocatedClass = relocatedInspector.clazz(relocatedName);
              assertThat(relocatedClass, isPresent());
            }
          }
        });
    return self();
  }

  public void inspectAllSignaturesNotContainingString(String originalPrefix) throws Exception {
    inspect(
        inspector -> {
          for (FoundClassSubject clazz : inspector.allClasses()) {
            assertThat(clazz.getFinalSignatureAttribute(), not(containsString(originalPrefix)));
            for (FoundMethodSubject method : clazz.allMethods()) {
              assertThat(
                  method.getJvmMethodSignatureAsString(), not(containsString(originalPrefix)));
            }
            for (FoundFieldSubject field : clazz.allFields()) {
              assertThat(field.getJvmFieldSignatureAsString(), not(containsString(originalPrefix)));
            }
          }
        });
  }

  public Path getOutput() {
    return output;
  }
}
