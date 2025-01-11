    .globl paramtest
paramtest:
    enter $(8 * 10), $0
    movq %rdi, -8(%rbp)
    movq %rsi, -16(%rbp)
    movq %rdx, -24(%rbp)
    movq %rcx, -32(%rbp)
    movq %r8, -40(%rbp)
    movq %r9, -48(%rbp)
    movq 16(%rbp), %r10
    movq %r10, -56(%rbp)
    movq 24(%rbp), %r10
    movq %r10, -64(%rbp)
    movq 32(%rbp), %r10
    movq %r10, -72(%rbp)
    // NOP
    // NOP
    // NOP
    movq -8(%rbp), %rdi
    call printInt
    // NOP
    call println
    // NOP
    // NOP
    movq -16(%rbp), %rdi
    call printInt
    // NOP
    call println
    // NOP
    // NOP
    movq -24(%rbp), %rdi
    call printInt
    // NOP
    call println
    // NOP
    // NOP
    movq -32(%rbp), %rdi
    call printInt
    // NOP
    call println
    // NOP
    // NOP
    movq -40(%rbp), %rdi
    call printInt
    // NOP
    call println
    // NOP
    // NOP
    movq -48(%rbp), %rdi
    call printInt
    // NOP
    call println
    // NOP
    // NOP
    movq -56(%rbp), %rdi
    call printInt
    // NOP
    call println
    // NOP
    // NOP
    movq -64(%rbp), %rdi
    call printInt
    // NOP
    call println
    // NOP
    // NOP
    movq -72(%rbp), %rdi
    call printInt
    // NOP
    call println
    leave
    ret
    .globl main
main:
    enter $(8 * 10), $0
    // NOP
    // NOP
    movq $1, -8(%rbp)
    movq $2, -16(%rbp)
    movq $3, -24(%rbp)
    movq $4, -32(%rbp)
    movq $5, -40(%rbp)
    movq $6, -48(%rbp)
    movq $7, -56(%rbp)
    movq $8, -64(%rbp)
    movq $9, -72(%rbp)
    movq -8(%rbp), %rdi
    movq -16(%rbp), %rsi
    movq -24(%rbp), %rdx
    movq -32(%rbp), %rcx
    movq -40(%rbp), %r8
    movq -48(%rbp), %r9
    subq $8, %rsp
    movq -72(%rbp), %r10
    pushq %r10
    movq -64(%rbp), %r10
    pushq %r10
    movq -56(%rbp), %r10
    pushq %r10
    call paramtest
    addq $32, %rsp
    leave
    ret
