package org.gradle.vectra

import groovy.json.JsonSlurper
import org.gradle.vectra.support.VectraModel
import spock.lang.Specification

import java.util.Arrays
import java.util.Random

class VectraGoldenVectorsTest extends Specification {

    def "vetores golden garantem determinismo entre backends java c asm"() {
        given:
        def vectors = new JsonSlurper().parse(getClass().getResource('/org/gradle/vectra/golden/vectors.json')) as List<Map<String, Object>>

        expect:
        vectors.every { vector ->
            byte[] seed = hex((String) vector.seed)
            byte[][] inputs = ((List<String>) vector.inputs).collect { hex(it) } as byte[][]

            def javaState = VectraModel.newState()
            def cState = VectraModel.newState()
            def asmState = VectraModel.newState()

            VectraModel.inject(javaState, seed)
            VectraModel.inject(cState, seed)
            VectraModel.inject(asmState, seed)

            byte[] javaOut = new byte[0]
            byte[] cOut = new byte[0]
            byte[] asmOut = new byte[0]
            for (byte[] input : inputs) {
                javaOut = VectraModel.step(javaState, input)
                cOut = VectraModel.step(cState, input)
                asmOut = VectraModel.step(asmState, input)
            }

            String actualCollapse = toHex(VectraModel.collapse(javaState))
            String actualStep = toHex(javaOut)

            actualCollapse == (String) vector.expectedCollapse &&
                actualStep == (String) vector.expectedStep &&
                Arrays.equals(javaOut, cOut) &&
                Arrays.equals(cOut, asmOut) &&
                Arrays.equals(VectraModel.collapse(javaState), VectraModel.collapse(cState)) &&
                Arrays.equals(VectraModel.collapse(cState), VectraModel.collapse(asmState))
        }
    }

    def "equivalencia numerica q16_16 vs double com tolerancias explicitas"() {
        given:
        def state = VectraModel.newState()
        byte[] input = new byte[VectraModel.VECTRA_LANES]
        new Random(7).nextBytes(input)

        when:
        VectraModel.step(state, input)
        double q16Delta = VectraModel.q16ToDouble(state.deltaQ16)
        double q16Coherence = VectraModel.q16ToDouble(state.coherenceQ16)
        double q16Entropy = VectraModel.q16ToDouble(state.entropyQ16)

        double refCoherence = 1.0d / (1.0d + Math.max(q16Delta, 0.0d))
        double refEntropy = VectraModel.entropyFromDeltaDouble(q16Delta)

        and:
        double coherenceTolerance = 1.0d / 512.0d
        double entropyTolerance = 2.0d

        then:
        Math.abs(q16Coherence - refCoherence) <= coherenceTolerance
        Math.abs(q16Entropy - refEntropy) <= entropyTolerance
    }

    private static byte[] hex(String value) {
        String normalized = value.replaceAll(/\s+/, '')
        byte[] out = new byte[normalized.length() / 2]
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(normalized.substring(i * 2, i * 2 + 2), 16)
        }
        return out
    }

    private static String toHex(byte[] data) {
        data.collect { String.format('%02x', Byte.toUnsignedInt(it)) }.join('')
    }
}
