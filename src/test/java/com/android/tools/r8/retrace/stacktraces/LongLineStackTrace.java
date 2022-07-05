// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.stacktraces;

import java.util.Arrays;
import java.util.List;

public class LongLineStackTrace implements StackTraceForTest {

  @Override
  public List<String> obfuscatedStackTrace() {
    return Arrays.asList(
        "asdf():::asfasidfsadfsafassdfsalfkaskldfasjkl908435 439593409 5309 843 5980349085 9043598"
            + " 04930 5 9084389 549 385 908435098435980 4390 5890435908 4389 0509345890"
            + " 23904239s909090safasiofas90f0-safads0-fas0-f-0f-0fasdf0-asswioj df jaiowj fioweoji"
            + " fqiwope fopiwqej fqweiopj fwqejiof qwoepijf eiwoj fqwioepjf wiqeof jqweoifiqu"
            + " t8324981qu2398 rt3289 rt2489t euhiorjg kdfgf8u432iojt3u8io432jk t3u49t 489u4389u"
            + " t438u9 t43y89t 3 489t y8934t34 89ytu8943tu8 984u3 t"
            + " 8u934asdf(:asdfas0dfasd0fa0)S)DFD)SDF_SD)FSDKFJlsalk;dfjaklsdf())(SDFSdfaklsdfas0d9fwe89rio223oi4rwoiuqaoiwqiowpjaklcvewtujoiwrjweof"
            + " asdfjaswdj foisadj f aswioj df jaiowj fioweoji fqiwope fopiwqej fqweiopj fwqejiof"
            + " qwoepijf eiwoj fqwioepjf wiqeof jqweoifiqu t8324981qu2398 rt3289 rt2489t euhiorjg"
            + " kdfgf8u432iojt3u8io432jk t3u49t 489u4389u t438u9 t43y89t 3 489t y8934t34"
            + " 89ytu8943tu8 984u3 t"
            + " 8u93asdf(:asdfas0dfasd0fa0)S)DFD)SDF_SD)FSDKFJlsalk;dfjaklsdf())(SDFSdfaklsdfas0d9fwe89rio223oi4rwoiuqaoiwqiowpjaklcvewtujoiwrjweof"
            + " asdfjaswdj foisadj f aswioj df jaiowj fioweoji fqiwope fopiwqej fqweiopj fwqejiof"
            + " qwoepijf eiwoj fqwioepjf wiqeof jqweoifiqu t8324981qu2398 rt3289 rt2489t euhiorjg"
            + " kdfgf8u432iojt3u8io432jk t3u49t 489u4389u t438u9 t43y89t 3 489t y8934t34"
            + " 89ytu8943tu8 984u3 t"
            + " 8u9344asdf(:asdfas0dfasd0fa0)S)DFD)SDF_SD)FSDKFJlsalk;dfjaklsdf())(SDFSdfaklsdfas0d9fwe89rio223oi4rwoiuqaoiwqiowpjaklcvewtujoiwrjweof"
            + " asdfjaswdj foisadj f aswioj df jaiowj fioweoji fqiwope fopiwqej fqweiopj fwqejiof"
            + " qwoepijf eiwoj fqwioepjf wiqeof jqweoifiqu t8324981qu2398 rt3289 rt2489t euhiorjg"
            + " kdfgfasdfsdf123asdfas9dfas09df9a090asdasdfasfasdf290909009fw90wf9w0f9e09w0f90w");
  }

  @Override
  public String mapping() {
    return "";
  }

  @Override
  public List<String> retracedStackTrace() {
    return Arrays.asList(
        "asdf():::asfasidfsadfsafassdfsalfkaskldfasjkl908435 439593409 5309 843 5980349085 9043598"
            + " 04930 5 9084389 549 385 908435098435980 4390 5890435908 4389 0509345890"
            + " 23904239s909090safasiofas90f0-safads0-fas0-f-0f-0fasdf0-asswioj df jaiowj fioweoji"
            + " fqiwope fopiwqej fqweiopj fwqejiof qwoepijf eiwoj fqwioepjf wiqeof jqweoifiqu"
            + " t8324981qu2398 rt3289 rt2489t euhiorjg kdfgf8u432iojt3u8io432jk t3u49t 489u4389u"
            + " t438u9 t43y89t 3 489t y8934t34 89ytu8943tu8 984u3 t"
            + " 8u934asdf(:asdfas0dfasd0fa0)S)DFD)SDF_SD)FSDKFJlsalk;dfjaklsdf())(SDFSdfaklsdfas0d9fwe89rio223oi4rwoiuqaoiwqiowpjaklcvewtujoiwrjweof"
            + " asdfjaswdj foisadj f aswioj df jaiowj fioweoji fqiwope fopiwqej fqweiopj fwqejiof"
            + " qwoepijf eiwoj fqwioepjf wiqeof jqweoifiqu t8324981qu2398 rt3289 rt2489t euhiorjg"
            + " kdfgf8u432iojt3u8io432jk t3u49t 489u4389u t438u9 t43y89t 3 489t y8934t34"
            + " 89ytu8943tu8 984u3 t"
            + " 8u93asdf(:asdfas0dfasd0fa0)S)DFD)SDF_SD)FSDKFJlsalk;dfjaklsdf())(SDFSdfaklsdfas0d9fwe89rio223oi4rwoiuqaoiwqiowpjaklcvewtujoiwrjweof"
            + " asdfjaswdj foisadj f aswioj df jaiowj fioweoji fqiwope fopiwqej fqweiopj fwqejiof"
            + " qwoepijf eiwoj fqwioepjf wiqeof jqweoifiqu t8324981qu2398 rt3289 rt2489t euhiorjg"
            + " kdfgf8u432iojt3u8io432jk t3u49t 489u4389u t438u9 t43y89t 3 489t y8934t34"
            + " 89ytu8943tu8 984u3 t"
            + " 8u9344asdf(:asdfas0dfasd0fa0)S)DFD)SDF_SD)FSDKFJlsalk;dfjaklsdf())(SDFSdfaklsdfas0d9fwe89rio223oi4rwoiuqaoiwqiowpjaklcvewtujoiwrjweof"
            + " asdfjaswdj foisadj f aswioj df jaiowj fioweoji fqiwope fopiwqej fqweiopj fwqejiof"
            + " qwoepijf eiwoj fqwioepjf wiqeof jqweoifiqu t8324981qu2398 rt3289 rt2489t euhiorjg"
            + " kdfgfasdfsdf123asdfas9dfas09df9a090asdasdfasfasdf290909009fw90wf9w0f9e09w0f90w");
  }

  @Override
  public List<String> retraceVerboseStackTrace() {
    return Arrays.asList(
        "asdf():::asfasidfsadfsafassdfsalfkaskldfasjkl908435 439593409 5309 843 5980349085 9043598"
            + " 04930 5 9084389 549 385 908435098435980 4390 5890435908 4389 0509345890"
            + " 23904239s909090safasiofas90f0-safads0-fas0-f-0f-0fasdf0-asswioj df jaiowj fioweoji"
            + " fqiwope fopiwqej fqweiopj fwqejiof qwoepijf eiwoj fqwioepjf wiqeof jqweoifiqu"
            + " t8324981qu2398 rt3289 rt2489t euhiorjg kdfgf8u432iojt3u8io432jk t3u49t 489u4389u"
            + " t438u9 t43y89t 3 489t y8934t34 89ytu8943tu8 984u3 t"
            + " 8u934asdf(:asdfas0dfasd0fa0)S)DFD)SDF_SD)FSDKFJlsalk;dfjaklsdf())(SDFSdfaklsdfas0d9fwe89rio223oi4rwoiuqaoiwqiowpjaklcvewtujoiwrjweof"
            + " asdfjaswdj foisadj f aswioj df jaiowj fioweoji fqiwope fopiwqej fqweiopj fwqejiof"
            + " qwoepijf eiwoj fqwioepjf wiqeof jqweoifiqu t8324981qu2398 rt3289 rt2489t euhiorjg"
            + " kdfgf8u432iojt3u8io432jk t3u49t 489u4389u t438u9 t43y89t 3 489t y8934t34"
            + " 89ytu8943tu8 984u3 t"
            + " 8u93asdf(:asdfas0dfasd0fa0)S)DFD)SDF_SD)FSDKFJlsalk;dfjaklsdf())(SDFSdfaklsdfas0d9fwe89rio223oi4rwoiuqaoiwqiowpjaklcvewtujoiwrjweof"
            + " asdfjaswdj foisadj f aswioj df jaiowj fioweoji fqiwope fopiwqej fqweiopj fwqejiof"
            + " qwoepijf eiwoj fqwioepjf wiqeof jqweoifiqu t8324981qu2398 rt3289 rt2489t euhiorjg"
            + " kdfgf8u432iojt3u8io432jk t3u49t 489u4389u t438u9 t43y89t 3 489t y8934t34"
            + " 89ytu8943tu8 984u3 t"
            + " 8u9344asdf(:asdfas0dfasd0fa0)S)DFD)SDF_SD)FSDKFJlsalk;dfjaklsdf())(SDFSdfaklsdfas0d9fwe89rio223oi4rwoiuqaoiwqiowpjaklcvewtujoiwrjweof"
            + " asdfjaswdj foisadj f aswioj df jaiowj fioweoji fqiwope fopiwqej fqweiopj fwqejiof"
            + " qwoepijf eiwoj fqwioepjf wiqeof jqweoifiqu t8324981qu2398 rt3289 rt2489t euhiorjg"
            + " kdfgfasdfsdf123asdfas9dfas09df9a090asdasdfasfasdf290909009fw90wf9w0f9e09w0f90w");
  }

  @Override
  public int expectedWarnings() {
    return 0;
  }
}
