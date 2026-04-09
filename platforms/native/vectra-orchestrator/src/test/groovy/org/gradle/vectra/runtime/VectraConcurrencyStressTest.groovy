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

package org.gradle.vectra.runtime

import spock.lang.Specification

import java.nio.ByteBuffer
import java.util.Arrays
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class VectraConcurrencyStressTest extends Specification {

    def "java pure engine remains stable under parallel load"() {
        given:
        def engine = new JavaPureVectraEngine()
        int workers = Math.max(2, Runtime.getRuntime().availableProcessors())
        def pool = Executors.newFixedThreadPool(workers)

        when:
        def tasks = (0..<workers).collect { idx ->
            return (Callable<byte[]>) {
                long handle = engine.init(ByteBuffer.allocateDirect(512), ByteBuffer.allocateDirect(512))
                byte[] input = new byte[32]
                Arrays.fill(input, (byte) idx)
                ByteBuffer out = ByteBuffer.allocateDirect(32)
                200.times {
                    out.clear()
                    int rc = engine.step(handle, ByteBuffer.wrap(input), out)
                    if (rc != 0) {
                        throw new IllegalStateException("step failed")
                    }
                }
                byte[] collapse = new byte[32]
                int collapseRc = engine.collapse(handle, ByteBuffer.wrap(collapse))
                if (collapseRc != 0) {
                    throw new IllegalStateException("collapse failed")
                }
                return collapse
            }
        }

        def futures = tasks.collect { pool.submit(it) }
        def results = futures.collect { it.get(30, TimeUnit.SECONDS) }

        then:
        results.size() == workers
        results.every { it.length == 32 }

        cleanup:
        pool.shutdownNow()
    }
}
