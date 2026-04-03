package org.maibot.core.util

object EasterEgg {
    fun randEly(): String {
        // 随机爱莉台词
        val mottos = arrayOf(
            "嗨，想我了吗？",
            "笑一笑，好吗？",
            "可爱的少女心可是无所不能的噢～♪",
            "哇，好高好高！",
            "只有付出真心，才能换得肯定。",
            "来一场华丽的舞会吧～♪",
            "多夸夸我好吗？我会很开心的～♪",
            "你好！新的一天，从一场美妙的邂逅开始。",
            "愿你前行的道路有群星闪耀；愿你留下的足迹有百花绽放。"
        )

        return mottos[(Math.random() * mottos.size).toInt()]
    }
}
