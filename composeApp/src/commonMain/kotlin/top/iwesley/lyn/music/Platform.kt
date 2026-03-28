package top.iwesley.lyn.music

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform