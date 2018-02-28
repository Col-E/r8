// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package properties

class CompanionLateInitProperties {
    companion object {
        private lateinit var privateLateInitProp: String
        internal lateinit var internalLateInitProp: String
        public lateinit var publicLateInitProp: String

        fun callSetterPrivateProp(v: String) {
            privateLateInitProp = v
        }

        fun callGetterPrivateProp(): String {
            return privateLateInitProp
        }
    }
}

fun companionLateInitProperties_noUseOfProperties() {
    CompanionLateInitProperties()
    println("DONE")
}

fun companionLateInitProperties_usePrivateLateInitProp() {
    CompanionLateInitProperties.callSetterPrivateProp("foo")
    println(CompanionLateInitProperties.callGetterPrivateProp())
}

fun companionLateInitProperties_useInternalLateInitProp() {
    CompanionLateInitProperties.internalLateInitProp = "foo"
    println(CompanionLateInitProperties.internalLateInitProp)
}

fun companionLateInitProperties_usePublicLateInitProp() {
    CompanionLateInitProperties.publicLateInitProp = "foo"
    println(CompanionLateInitProperties.publicLateInitProp)
}
