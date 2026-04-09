/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.vectra

import org.gradle.vectra.runtime.JavaPureVectraEngine
import spock.lang.Specification

import java.nio.ByteBuffer

class JavaPureVectraEngineTest extends Specification {

    def "pure java engine is deterministic for identical input stream"() {
        given:
        def engine = new JavaPureVectraEngine()
        def stateA = ByteBuffer.allocateDirect(512)
        def scratchA = ByteBuffer.allocateDirect(512)
        def stateB = ByteBuffer.allocateDirect(512)
        def scratchB = ByteBuffer.allocateDirect(512)
        long handleA = engine.init(stateA, scratchA)
        long handleB = engine.init(stateB, scratchB)

        byte[] input = (0..<32).collect { (byte) it }.toArray(new byte[0])
        ByteBuffer outA = ByteBuffer.allocateDirect(32)
        ByteBuffer outB = ByteBuffer.allocateDirect(32)

        when:
        10.times {
            outA.clear()
            outB.clear()
            engine.step(handleA, ByteBuffer.wrap(input), outA)
            engine.step(handleB, ByteBuffer.wrap(input), outB)
        }
        byte[] collapseA = new byte[32]
        byte[] collapseB = new byte[32]
        engine.collapse(handleA, ByteBuffer.wrap(collapseA))
        engine.collapse(handleB, ByteBuffer.wrap(collapseB))

        then:
        collapseA == collapseB
    }

    def "inject changes future collapse output"() {
        given:
        def engine = new JavaPureVectraEngine()
        long handle = engine.init(ByteBuffer.allocateDirect(512), ByteBuffer.allocateDirect(512))
        byte[] input = new byte[32]
        ByteBuffer output = ByteBuffer.allocateDirect(32)

        when:
        engine.step(handle, ByteBuffer.wrap(input), output)
        byte[] before = new byte[32]
        engine.collapse(handle, ByteBuffer.wrap(before))
        engine.inject(handle, ByteBuffer.wrap([1, 2, 3, 4] as byte[]))
        byte[] after = new byte[32]
        engine.collapse(handle, ByteBuffer.wrap(after))

        then:
        before != after
    }
}
