package com.kyant.backdrop.catalog.appstore.music

/** One built-in remote script offered by the pdone/lx-music-source list. */
data class RemoteScriptSource(
    val id: String,
    val name: String,
    val url: String
)

/**
 * Built-in catalogue matching the "在线导入" links published in
 * https://github.com/pdone/lx-music-source . The URL is the raw GitHub script;
 * the Android importer automatically retries it through gh-proxy.org when the
 * raw host is unreachable.
 */
val PdoneScriptSources: List<RemoteScriptSource> = listOf(
    RemoteScriptSource("changqing", "ChangQing", "https://raw.githubusercontent.com/pdone/lx-music-source/main/changqing/latest.js"),
    RemoteScriptSource("feichangdao", "FeiChangDao", "https://raw.githubusercontent.com/pdone/lx-music-source/main/feichangdao/latest.js"),
    RemoteScriptSource("fish", "Fish", "https://raw.githubusercontent.com/pdone/lx-music-source/main/fish/latest.js"),
    RemoteScriptSource("flower", "Flower", "https://raw.githubusercontent.com/pdone/lx-music-source/main/flower/latest.js"),
    RemoteScriptSource("grass", "Grass", "https://raw.githubusercontent.com/pdone/lx-music-source/main/grass/latest.js"),
    RemoteScriptSource("helloworld", "HelloWorld", "https://raw.githubusercontent.com/pdone/lx-music-source/main/helloworld/latest.js"),
    RemoteScriptSource("huanyin", "HuanYin", "https://raw.githubusercontent.com/pdone/lx-music-source/main/huanyin/latest.js"),
    RemoteScriptSource("huibq", "Huibq", "https://raw.githubusercontent.com/pdone/lx-music-source/main/huibq/latest.js"),
    RemoteScriptSource("hyw", "HYW", "https://raw.githubusercontent.com/pdone/lx-music-source/main/hyw/latest.js"),
    RemoteScriptSource("ikun", "ikun", "https://raw.githubusercontent.com/pdone/lx-music-source/main/ikun/latest.js"),
    RemoteScriptSource("jiexiang", "JieXiang", "https://raw.githubusercontent.com/pdone/lx-music-source/main/jiexiang/latest.js"),
    RemoteScriptSource("juhe", "JuheApi", "https://raw.githubusercontent.com/pdone/lx-music-source/main/juhe/latest.js"),
    RemoteScriptSource("lx", "LX", "https://raw.githubusercontent.com/pdone/lx-music-source/main/lx/latest.js"),
    RemoteScriptSource("qdy", "QDY", "https://raw.githubusercontent.com/pdone/lx-music-source/main/qdy/latest.js"),
    RemoteScriptSource("qingliang", "QingLiang", "https://raw.githubusercontent.com/pdone/lx-music-source/main/qingliang/latest.js"),
    RemoteScriptSource("sixyin", "SixYin", "https://raw.githubusercontent.com/pdone/lx-music-source/main/sixyin/latest.js"),
    RemoteScriptSource("suyin", "SuYin", "https://raw.githubusercontent.com/pdone/lx-music-source/main/suyin/latest.js"),
    RemoteScriptSource("xinghai", "XingHai", "https://raw.githubusercontent.com/pdone/lx-music-source/main/xinghai/latest.js"),
    RemoteScriptSource("xinglan", "XingLan", "https://raw.githubusercontent.com/pdone/lx-music-source/main/xinglan/latest.js"),
    RemoteScriptSource("yuningxi", "YuNingXi", "https://raw.githubusercontent.com/pdone/lx-music-source/main/yuningxi/latest.js")
)
