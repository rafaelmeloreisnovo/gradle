.text
.globl vectra_pulse_mix
.type vectra_pulse_mix,@function
vectra_pulse_mix:
    test %rcx, %rcx
    je .Ldone
    xor %r8, %r8
.Lloop:
    mov (%rdi,%r8,4), %r9d
    xor (%rsi,%r8,4), %r9d
    add (%rdx,%r8,4), %r9d
    rol $7, %r9d
    mov %r9d, (%rdi,%r8,4)
    inc %r8
    cmp %r8, %rcx
    jne .Lloop
.Ldone:
    ret
.size vectra_pulse_mix, .-vectra_pulse_mix
