// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package nesthostexample;

import com.android.tools.r8.NeverInline;

public class NestPvtMethodCallInlined {

  public static class Inner {

    public String methodWithPvtCallToInline() {
      return notInlinedPvtCall();
    }

    @NeverInline
    private String notInlinedPvtCall() {
      return "notInlinedPvtCallInner";
    }

    private String nestPvtCallToInline() {
      return "nestPvtCallToInlineInner";
    }
  }

  public interface InnerInterface {

    default String methodWithPvtCallToInline() {
      return notInlinedPvtCall();
    }

    @NeverInline
    private String notInlinedPvtCall() {
      return "notInlinedPvtCallInnerInterface";
    }

    private String nestPvtCallToInline() {
      return "nestPvtCallToInlineInnerInterface";
    }

    default String dispatch(InnerSub sub) {
      return sub.notInlinedPvtCall();
    }

    @NeverInline
    default String dispatchInlining(InnerSub iSub) {
      return iSub.dispatch(this);
    }
  }

  public static class InnerInterfaceImpl implements InnerInterface {}

  public static class InnerSub extends Inner {

    @NeverInline
    public String dispatchInlining(InnerInterface impl) {
      return impl.dispatch(this);
    }

    public String dispatch(InnerInterface itf) {
      return itf.notInlinedPvtCall();
    }

    @NeverInline
    private String notInlinedPvtCall() {
      return "notInlinedPvtCallInnerSub";
    }

    private String nestPvtCallToInline() {
      return "nestPvtCallToInlineInnerSub";
    }
  }

  public static void main(String[] args) {
    Inner i = new Inner();
    InnerSub iSub = new InnerSub();
    InnerInterface impl = new InnerInterfaceImpl();

    // Inlining through nest access (invoke virtual/interface).
    System.out.println(i.nestPvtCallToInline());
    System.out.println(impl.nestPvtCallToInline());

    // Inlining transformations.
    // Invoke direct -> invoke virtual.
    System.out.println(i.methodWithPvtCallToInline());
    // Invoke interface -> invoke virtual.
    System.out.println(impl.methodWithPvtCallToInline());
    // Invoke virtual -> invoke direct.
    System.out.println(iSub.dispatchInlining(impl));
    // Invoke interface -> invoke direct.
    System.out.println(impl.dispatchInlining(iSub));

    // Inheritance + invoke virtual and nest access.
    // This may mess up lookup logic.
    System.out.println(iSub.nestPvtCallToInline());
    System.out.println(((Inner) iSub).nestPvtCallToInline());
  }
}
