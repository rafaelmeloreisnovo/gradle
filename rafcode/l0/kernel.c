/* SPDX-FileCopyrightText: 2026 Rafael Melo Reis
 * SPDX-License-Identifier: Apache-2.0
 *
 * RAFCODE authorial L0 build-capability reduction.
 * This file is independently written and is not derived from Gradle code.
 */

typedef unsigned int raf_word;

raf_word raf_gradle_l0_gate(raf_word requested, raf_word available)
{
    raf_word q = requested & 15u;
    raf_word a = available & 15u;
    return (q & a) & 15u;
}
