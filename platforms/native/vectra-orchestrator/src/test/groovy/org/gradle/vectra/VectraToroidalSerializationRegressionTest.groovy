package org.gradle.vectra

import org.gradle.vectra.support.VectraModel
import spock.lang.Specification

import java.util.Arrays
import java.util.Random

class VectraToroidalSerializationRegressionTest extends Specification {

    def "serializacao e desserializacao toroidal devem ser estaveis"() {
        given:
        def state = VectraModel.newState()
        def random = new Random(99)

        and:
        (0..<50).each {
            byte[] input = new byte[VectraModel.VECTRA_LANES]
            random.nextBytes(input)
            VectraModel.step(state, input)
        }

        when:
        byte[] serialized = VectraModel.serializeToroidalState(state)
        def restored = VectraModel.deserializeToroidalState(serialized)

        then:
        restored.cycle == state.cycle
        restored.tick == state.tick
        Arrays.equals(restored.lane, state.lane)
        Arrays.equals(restored.aux, state.aux)
        Arrays.equals(restored.pulse, state.pulse)
    }

    def "payload toroidal mantem compatibilidade com fixture codificada em fonte"() {
        given:
        byte[] expected = decodeInterlacedFixture('/org/gradle/vectra/golden/toroidal-state.encoded')

        and:
        def state = VectraModel.newState()
        byte[] input = new byte[VectraModel.VECTRA_LANES]
        new Random(2024).nextBytes(input)
        (0..<24).each {
            VectraModel.step(state, input)
        }

        when:
        byte[] actual = VectraModel.serializeToroidalState(state)

        then:
        Arrays.equals(actual, expected)
    }

    private static byte[] decodeInterlacedFixture(String resourcePath) {
        List<String> lines = VectraToroidalSerializationRegressionTest.class.getResource(resourcePath).readLines()
        Map<String, String> values = [:]
        lines.findAll { !it.startsWith('#') && it.contains('=') }
            .each { line ->
                int idx = line.indexOf('=')
                values.put(line.substring(0, idx).trim(), line.substring(idx + 1).trim())
            }

        int payloadSize = Integer.parseInt(values.payload_size)
        int maskA = Integer.decode(values.mask_a)
        int maskB = Integer.decode(values.mask_b)
        byte[] streamA = hex(values.stream_a)
        byte[] streamB = hex(values.stream_b)

        byte[] output = new byte[payloadSize]
        int a = 0
        int b = 0
        for (int i = 0; i < payloadSize; i++) {
            if ((i & 1) == 0) {
                int encoded = Byte.toUnsignedInt(streamA[a])
                output[i] = (byte) (encoded ^ maskA ^ (a % 251))
                a++
            } else {
                int encoded = Byte.toUnsignedInt(streamB[b])
                output[i] = (byte) (encoded ^ maskB ^ (b % 239))
                b++
            }
        }
        return output
    }

    private static byte[] hex(String value) {
        byte[] out = new byte[value.length() / 2]
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16)
        }
        return out
    }
}
