// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.memberrebinding;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.transformers.ClassFileTransformer.MethodPredicate;
import com.android.tools.r8.utils.AndroidApiLevel;
import javax.security.auth.DestroyFailedException;
import javax.security.auth.Destroyable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/* Regression test for b/234613774 */
@RunWith(Parameterized.class)
public class LibraryMemberRebindingSuperTypeWithApiMethodTest extends TestBase {

  private static final String SECRET_KEY_DESCRIPTOR = "Ljavax/crypto/SecretKey;";

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().build();
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addProgramClassFileData(getMainCallingSecretKeyDestroy())
        .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
        .setMinApi(AndroidApiLevel.B)
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            parameters.getDexRuntimeVersion().isOlderThan(Version.V8_1_0),
            result -> result.assertFailureWithErrorThatThrows(NoSuchMethodError.class),
            result -> result.assertFailureWithErrorThatThrows(DestroyFailedException.class));
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassFileData(getMainCallingSecretKeyDestroy())
        .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
        .setMinApi(AndroidApiLevel.B)
        .addKeepMainRule(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            parameters.getDexRuntimeVersion().isOlderThan(Version.V8_1_0),
            // TODO(b/234613774): We should not introduce ICCE.
            result -> result.assertFailureWithErrorThatThrows(IncompatibleClassChangeError.class),
            result -> result.assertFailureWithErrorThatThrows(DestroyFailedException.class));
  }

  private byte[] getMainCallingSecretKeyDestroy() throws Exception {
    return transformer(Main.class)
        .setImplementsClassDescriptors(SECRET_KEY_DESCRIPTOR)
        .replaceClassDescriptorInMethodInstructions(
            descriptor(SecretKey.class), SECRET_KEY_DESCRIPTOR)
        .removeMethods(MethodPredicate.onName("destroy"))
        .transform();
  }

  public interface SecretKey extends Destroyable {

    void destroy();
  }

  public static class Main implements /* javax.crypto */ SecretKey {

    public static void main(String[] args) {

      /* javax.crypto */ SecretKey main = new Main();
      main.destroy();
    }

    @Override
    public void destroy() {
      throw new RuntimeException("Should have been removed");
    }
  }
}
