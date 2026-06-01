package calebxzhou.rdi.model

/**
 * calebxzhou @ 2025-11-19 22:19
 */
enum class Role(val level: Int) {
    OWNER(0),
    ADMIN(1),
    MEMBER(2),
    GUEST(10)
}