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

.text
.globl vectra_pulse_mix
.type vectra_pulse_mix,%function
vectra_pulse_mix:
    cbz x3, .Ldone
    mov x4, #0
.Lloop:
    ldr w5, [x0, x4, lsl #2]
    ldr w6, [x1, x4, lsl #2]
    eor w5, w5, w6
    ldr w6, [x2, x4, lsl #2]
    add w5, w5, w6
    ror w5, w5, #25
    str w5, [x0, x4, lsl #2]
    add x4, x4, #1
    cmp x4, x3
    b.ne .Lloop
.Ldone:
    ret
.size vectra_pulse_mix, .-vectra_pulse_mix
