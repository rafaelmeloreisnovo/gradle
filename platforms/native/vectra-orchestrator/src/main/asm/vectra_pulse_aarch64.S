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
