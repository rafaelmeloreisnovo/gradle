package org.gradle.vectra

import org.gradle.vectra.support.VectraModel
import spock.lang.Specification

import java.util.Arrays
import java.util.Random

class VectraInvariantsPropertyTest extends Specification {

    def "periodicidade da fase em 56 ciclos"() {
        given:
        def state = VectraModel.newState()
        byte[] input = new byte[VectraModel.VECTRA_LANES]

        when:
        def observed = []
        for (int i = 0; i < VectraModel.VECTRA_PHASE_CYCLES * 3; i++) {
            VectraModel.step(state, input)
            observed << [state.cycle, state.phaseQ16]
        }

        then:
        observed[0] == observed[VectraModel.VECTRA_PHASE_CYCLES]
        observed[1] == observed[VectraModel.VECTRA_PHASE_CYCLES + 1]
        observed[2] == observed[VectraModel.VECTRA_PHASE_CYCLES + 2]
    }

    def "limites de coerencia e entropia para entradas aleatorias"() {
        given:
        def random = new Random(0xC0FFEE)

        expect:
        (0..<60).every {
            def state = VectraModel.newState()
            (0..<300).every {
                byte[] input = new byte[VectraModel.VECTRA_LANES]
                random.nextBytes(input)
                VectraModel.step(state, input)
                state.coherenceQ16 >= 0 &&
                    state.coherenceQ16 <= VectraModel.Q16_ONE &&
                    state.entropyQ16 >= 0 &&
                    state.entropyQ16 <= (31 * VectraModel.Q16_LN2)
            }
        }
    }

    def "estabilidade de atratores apos burn-in"() {
        given:
        def state = VectraModel.newState()
        byte[] input = new byte[VectraModel.VECTRA_LANES]
        Arrays.fill(input, (byte) 0x5A)

        when:
        List<Double> magnitudes = []
        for (int i = 0; i < 900; i++) {
            VectraModel.step(state, input)
            if (i >= 300) {
                double magnitude = Math.sqrt(
                    Math.pow(VectraModel.q16ToDouble(state.x[0]), 2) +
                        Math.pow(VectraModel.q16ToDouble(state.x[1]), 2) +
                        Math.pow(VectraModel.q16ToDouble(state.x[2]), 2)
                )
                magnitudes << magnitude
            }
        }

        then:
        magnitudes.max() < 5000d
        magnitudes.min() > 0.00001d
        magnitudes.takeRight(100).sum() / 100d < magnitudes.take(100).sum() / 100d * 1.4d
    }
}
